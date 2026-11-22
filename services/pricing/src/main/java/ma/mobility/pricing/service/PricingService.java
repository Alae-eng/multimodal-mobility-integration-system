package ma.mobility.pricing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ma.mobility.pricing.domain.Operator;
import ma.mobility.pricing.domain.PriceCalculator;
import ma.mobility.pricing.domain.PriceCalculator.Breakdown;
import ma.mobility.pricing.domain.PriceCalculator.Leg;
import ma.mobility.pricing.dto.OfferInput;
import ma.mobility.pricing.dto.QuoteRequest;
import ma.mobility.pricing.exception.IdempotencyConflictException;
import ma.mobility.pricing.exception.InvalidQuoteException;
import ma.mobility.pricing.exception.QuoteNotFoundException;
import ma.mobility.pricing.model.Quote;
import ma.mobility.pricing.model.QuoteLine;
import ma.mobility.pricing.repository.QuoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PricingService {

    private final PriceCalculator calculator;
    private final QuoteRepository quotes;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PricingService(PriceCalculator calculator, QuoteRepository quotes, Clock clock,
                          PlatformTransactionManager txManager) {
        this.calculator = calculator;
        this.quotes = quotes;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Prices a multimodal trip and stores the quote.
     * Idempotent: the same Idempotency-Key with the same request returns the same quote; the same
     * key with a different request is refused (409) rather than silently answered with the old price.
     *
     * Deliberately not one big transaction: when two requests with the same key arrive together,
     * the loser's insert fails on the unique key and its transaction is unusable. So the insert runs
     * in its own short transaction, and the winner's quote is read afterwards in a fresh one.
     */
    public Quote quote(QuoteRequest request, String idempotencyKey) {
        List<Leg> legs = toLegs(request.offers());
        String key = blankToNull(idempotencyKey);
        String hash = fingerprint(legs, request.travelers());

        if (key != null) {
            Optional<Quote> existing = quotes.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), key, hash);
            }
        }

        Quote quote = build(legs, request.travelers(), key, hash);
        try {
            return tx.execute(status -> quotes.saveAndFlush(quote));
        } catch (DataIntegrityViolationException race) {
            if (key == null) {
                throw race;
            }
            // The other request with the same key won: answer with its quote.
            return quotes.findByIdempotencyKey(key)
                    .map(winner -> replay(winner, key, hash))
                    .orElseThrow(() -> race);
        }
    }

    private Quote build(List<Leg> legs, int travelers, String key, String hash) {
        Breakdown breakdown;
        try {
            breakdown = calculator.calculate(legs, travelers);
        } catch (ArithmeticException overflow) {
            throw new InvalidQuoteException("amounts are too large to be priced");
        }
        return new Quote(key, hash, travelers, breakdown.subtotalMinor(), breakdown.discountBps(),
                breakdown.discountMinor(), breakdown.totalMinor(), clock.instant(), toLines(breakdown));
    }

    @Transactional(readOnly = true)
    public Quote get(String quoteId) {
        return quotes.findById(quoteId).orElseThrow(() -> new QuoteNotFoundException(quoteId));
    }

    private Quote replay(Quote existing, String key, String hash) {
        if (!hash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException(key);
        }
        return existing;
    }

    private List<Leg> toLegs(List<OfferInput> offers) {
        Set<String> seen = new HashSet<>();
        return offers.stream().map(offer -> {
            if (!seen.add(offer.offerId())) {
                throw new InvalidQuoteException("offer " + offer.offerId() + " appears more than once");
            }
            return new Leg(offer.offerId(), parseOperator(offer.operator()), offer.priceMinor());
        }).toList();
    }

    private Operator parseOperator(String value) {
        try {
            return Operator.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuoteException("unknown operator '" + value + "' (expected RAIL, AIR or CARSHARING)");
        }
    }

    private List<QuoteLine> toLines(Breakdown breakdown) {
        return breakdown.lines().stream()
                .map(l -> new QuoteLine(l.offerId(), l.operator(), l.unitPriceMinor(), l.quantity(), l.amountMinor()))
                .toList();
    }

    /** SHA-256 of the priced content, independent of the order of the offers. */
    private String fingerprint(List<Leg> legs, int travelers) {
        String content = legs.stream()
                .map(l -> l.offerId() + "|" + l.operator() + "|" + l.unitPriceMinor())
                .sorted()
                .reduce("travelers=" + travelers, (a, b) -> a + ";" + b);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
