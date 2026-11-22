package ma.mobility.pricing.dto;

import java.util.List;
import ma.mobility.pricing.model.Quote;

/** All amounts are whole numbers of centimes of MAD. */
public record QuoteResponse(
        String quoteId,
        int travelers,
        List<Line> lines,
        long subtotalMinor,
        int discountBps,
        long discountMinor,
        long totalMinor,
        String currency) {

    public record Line(String offerId, String operator, long unitPriceMinor, int quantity, long amountMinor) {
    }

    public static QuoteResponse from(Quote quote) {
        List<Line> lines = quote.getLines().stream()
                .map(l -> new Line(l.getOfferId(), l.getOperator().name(), l.getUnitPriceMinor(),
                        l.getQuantity(), l.getAmountMinor()))
                .toList();
        return new QuoteResponse(quote.getId(), quote.getTravelers(), lines, quote.getSubtotalMinor(),
                quote.getDiscountBps(), quote.getDiscountMinor(), quote.getTotalMinor(), "MAD");
    }
}
