package ma.mobility.bookings.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.mobility.bookings.dto.ConfirmBookingRequest;
import ma.mobility.bookings.dto.CreateBookingRequest;
import ma.mobility.bookings.dto.LegInput;
import ma.mobility.bookings.events.BookingEvent;
import ma.mobility.bookings.events.OutboxWriter;
import ma.mobility.bookings.exception.BookingNotFoundException;
import ma.mobility.bookings.exception.IdempotencyConflictException;
import ma.mobility.bookings.exception.InvalidBookingException;
import ma.mobility.bookings.model.Booking;
import ma.mobility.bookings.model.BookingLeg;
import ma.mobility.bookings.model.BookingStatus;
import ma.mobility.bookings.model.Operator;
import ma.mobility.bookings.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The consolidated travel file.
 *
 * Consistency rules:
 * - confirm / cancel / fail / expiry lock the booking row (SELECT ... FOR UPDATE), so replays and
 *   races see the result of the first call: one transition, one event, no lost update;
 * - every state change and its event are written in the SAME transaction (transactional outbox),
 *   so a booking can never change without its event, and an event never exists for a change that
 *   was rolled back;
 * - cancel never fails: the saga replays it during compensation.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final TransactionTemplate tx;

    /** A PENDING file older than this was abandoned by the saga (the operators' holds are long gone). */
    private final Duration pendingTtl;

    public BookingService(BookingRepository bookings, OutboxWriter outbox, Clock clock,
                          PlatformTransactionManager txManager,
                          @Value("${bookings.pending-ttl:PT15M}") Duration pendingTtl) {
        if (pendingTtl.compareTo(Duration.ofSeconds(1)) < 0 || pendingTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("bookings.pending-ttl must be between 1 second and 24 hours");
        }
        this.bookings = bookings;
        this.outbox = outbox;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.pendingTtl = pendingTtl;
    }

    // ------------------------------------------------------------------ create

    /**
     * Opens a PENDING file with the holdId of each operator. Idempotent: the same Idempotency-Key
     * with the same trip returns the same file; the same key with another trip is refused (409).
     *
     * Not one big transaction on purpose: when two requests with the same key arrive together the
     * loser's insert fails on the unique key and its transaction is unusable, so the insert runs
     * in its own short transaction and the winner's file is read afterwards in a fresh one.
     */
    public Booking create(CreateBookingRequest request, String idempotencyKey) {
        List<BookingLeg> legs = toLegs(request.legs());
        String key = blankToNull(idempotencyKey);
        String hash = fingerprint(request, legs);

        if (key != null) {
            Optional<Booking> existing = bookings.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), key, hash);
            }
        }

        Booking booking = new Booking(key, hash, request.travelerId().trim(), request.totalMinor(),
                blankToNull(request.quoteId()), legs, clock.instant());
        try {
            return tx.execute(status -> bookings.saveAndFlush(booking));
        } catch (DataIntegrityViolationException race) {
            if (key == null) {
                throw race;
            }
            return bookings.findByIdempotencyKey(key)
                    .map(winner -> replay(winner, key, hash))
                    .orElseThrow(() -> race);
        }
    }

    // ----------------------------------------------------------------- confirm

    /**
     * PENDING -> CONFIRMED with the bookingRef of every operator. A replay on a CONFIRMED file
     * returns it unchanged and publishes nothing. Refused (409) on a CANCELLED or FAILED file.
     */
    @Transactional
    public Booking confirm(String bookingId, ConfirmBookingRequest request) {
        Booking booking = lock(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return booking;
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            applyReferences(booking, request);
        }
        if (booking.confirm(clock.instant())) { // throws IllegalBookingStateException when not allowed
            bookings.save(booking);
            outbox.record(BookingEvent.BOOKING_CONFIRMED, booking);
        }
        return booking;
    }

    // ------------------------------------------------------------------ cancel

    /**
     * Compensation and traveler cancellation. Idempotent and never fails: an unknown, already
     * cancelled or failed file is a success that changes nothing.
     * Only a CONFIRMED file is announced (BookingCancelled): the traveler never heard about a file
     * that was still PENDING, so cancelling it is not news.
     */
    @Transactional
    public void cancel(String bookingId) {
        Optional<Booking> found = bookings.findByIdForUpdate(bookingId);
        if (found.isEmpty()) {
            return;
        }
        Booking booking = found.get();
        boolean wasConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        if (booking.cancel(clock.instant())) {
            bookings.save(booking);
            if (wasConfirmed) {
                outbox.record(BookingEvent.BOOKING_CANCELLED, booking);
            }
        }
    }

    // -------------------------------------------------------------------- fail

    /** PENDING -> FAILED: the saga gave up. Publishes SagaFailed once; a replay changes nothing. */
    @Transactional
    public Booking fail(String bookingId, String reason) {
        Booking booking = lock(bookingId);
        if (booking.fail(reason, clock.instant())) { // throws IllegalBookingStateException when not allowed
            bookings.save(booking);
            outbox.record(BookingEvent.SAGA_FAILED, booking);
        }
        return booking;
    }

    // -------------------------------------------------------------- expiration

    /**
     * Called by BookingExpirationJob. A PENDING file the saga never completed becomes FAILED.
     * Each file is handled in its own transaction, so one failure does not block the others and
     * the failed one is retried on the next run. Returns how many files were expired.
     */
    public int expireStalePending() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(pendingTtl);
        int expired = 0;
        for (Booking candidate : bookings.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)) {
            try {
                Boolean done = tx.execute(status -> expireIfStillPending(candidate.getId(), cutoff));
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Could not expire booking {}, will retry on the next run: {}", candidate.getId(), e.getMessage());
            }
        }
        return expired;
    }

    private boolean expireIfStillPending(String bookingId, Instant cutoff) {
        // Re-read under lock: the saga may have confirmed or failed it since the candidate query.
        Optional<Booking> locked = bookings.findByIdForUpdate(bookingId);
        if (locked.isEmpty() || locked.get().getStatus() != BookingStatus.PENDING
                || !locked.get().getCreatedAt().isBefore(cutoff)) {
            return false;
        }
        Booking booking = locked.get();
        booking.fail("Saga did not complete in time", clock.instant());
        bookings.save(booking);
        outbox.record(BookingEvent.SAGA_FAILED, booking);
        return true;
    }

    // ------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Booking get(String bookingId) {
        return bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    @Transactional(readOnly = true)
    public List<Booking> listForTraveler(String travelerId) {
        return bookings.findTop100ByTravelerIdOrderByCreatedAtDesc(travelerId.trim());
    }

    // ----------------------------------------------------------------- helpers

    private Booking lock(String bookingId) {
        return bookings.findByIdForUpdate(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private Booking replay(Booking existing, String key, String hash) {
        if (!hash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException(key);
        }
        return existing;
    }

    /** Every leg must be confirmed: a CONFIRMED file may never keep a holdId as its reference. */
    private void applyReferences(Booking booking, ConfirmBookingRequest request) {
        Map<Operator, String> refs = new EnumMap<>(Operator.class);
        for (ConfirmBookingRequest.LegReference r : request.references()) {
            Operator operator = parseOperator(r.operator());
            if (refs.put(operator, r.bookingRef().trim()) != null) {
                throw new InvalidBookingException("operator " + operator + " appears more than once");
            }
        }
        for (BookingLeg leg : booking.getLegs()) {
            if (!refs.containsKey(leg.getOperator())) {
                throw new InvalidBookingException("missing bookingRef for operator " + leg.getOperator());
            }
        }
        for (Operator operator : refs.keySet()) {
            if (booking.getLegs().stream().noneMatch(l -> l.getOperator() == operator)) {
                throw new InvalidBookingException("booking " + booking.getId() + " has no " + operator + " leg");
            }
        }
        refs.forEach(booking::updateLegReference);
    }

    private List<BookingLeg> toLegs(List<LegInput> inputs) {
        Map<Operator, LegInput> seen = new EnumMap<>(Operator.class);
        for (LegInput input : inputs) {
            Operator operator = parseOperator(input.operator());
            if (seen.put(operator, input) != null) {
                throw new InvalidBookingException("operator " + operator + " appears more than once");
            }
        }
        return seen.entrySet().stream()
                .map(e -> new BookingLeg(e.getKey(), e.getValue().offerId().trim(),
                        e.getValue().reference().trim(), e.getValue().priceMinor()))
                .toList();
    }

    private Operator parseOperator(String value) {
        try {
            return Operator.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidBookingException("unknown operator '" + value + "' (expected RAIL, AIR or CARSHARING)");
        }
    }

    /** SHA-256 of the trip content, independent of the order of the legs. */
    private String fingerprint(CreateBookingRequest request, List<BookingLeg> legs) {
        String content = legs.stream()
                .map(l -> l.getOperator() + "|" + l.getOfferId() + "|" + l.getReference() + "|" + l.getPriceMinor())
                .sorted()
                .reduce("traveler=" + request.travelerId().trim() + ";total=" + request.totalMinor()
                        + ";quote=" + blankToNull(request.quoteId()), (a, b) -> a + ";" + b);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
