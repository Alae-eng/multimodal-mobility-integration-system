package ma.mobility.bookings.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The consolidated travel file: one booking per multimodal trip, with the reference of each
 * operator (train, flight, car).
 *
 * State changes are idempotent on purpose. The saga retries and compensates: replaying a cancel
 * or a failure must leave the file in the same state and must never raise an error.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @Column(length = 50)
    private String id;

    /** From the Idempotency-Key header. Unique: the same key can never create two files. */
    @Column(unique = true, length = 100)
    private String idempotencyKey;

    /** Fingerprint of the request, to detect a key reused for a different trip. */
    @Column(length = 64)
    private String requestHash;

    @Column(nullable = false, length = 100)
    private String travelerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private long totalMinor;

    /** The quote this file was priced with (optional, set by the saga). */
    @Column(length = 50)
    private String quoteId;

    /** Why the saga gave up, set when the status is FAILED. */
    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: a stale write can never overwrite a newer state. */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_legs", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "position")
    private List<BookingLeg> legs = new ArrayList<>();

    protected Booking() {
        // required by JPA
    }

    public Booking(String idempotencyKey, String requestHash, String travelerId, long totalMinor,
                   String quoteId, List<BookingLeg> legs, Instant now) {
        this.id = "BK-" + UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.travelerId = travelerId;
        this.totalMinor = totalMinor;
        this.quoteId = quoteId;
        this.legs = new ArrayList<>(legs);
        this.status = BookingStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** PENDING -> CONFIRMED. Returns true when the state changed (false on a replay). */
    public boolean confirm(Instant now) {
        if (status == BookingStatus.CONFIRMED) {
            return false;
        }
        if (status != BookingStatus.PENDING) {
            throw new IllegalBookingStateException(id, status, BookingStatus.CONFIRMED);
        }
        return moveTo(BookingStatus.CONFIRMED, now);
    }

    /**
     * PENDING or CONFIRMED -> CANCELLED. Returns true when the state changed, false when there was
     * nothing to cancel (already cancelled, or FAILED: nothing was ever confirmed). Never throws:
     * compensation is replayed by the saga and must be safe to repeat.
     */
    public boolean cancel(Instant now) {
        if (status == BookingStatus.CANCELLED || status == BookingStatus.FAILED) {
            return false;
        }
        return moveTo(BookingStatus.CANCELLED, now);
    }

    /** PENDING -> FAILED. Returns true when the state changed (false on a replay). */
    public boolean fail(String reason, Instant now) {
        if (status == BookingStatus.FAILED) {
            return false;
        }
        if (status != BookingStatus.PENDING) {
            throw new IllegalBookingStateException(id, status, BookingStatus.FAILED);
        }
        this.failureReason = reason;
        return moveTo(BookingStatus.FAILED, now);
    }

    /** Replaces a leg's hold reference by the booking reference returned at confirm time. */
    public void updateLegReference(Operator operator, String reference) {
        for (int i = 0; i < legs.size(); i++) {
            BookingLeg leg = legs.get(i);
            if (leg.getOperator() == operator) {
                legs.set(i, new BookingLeg(operator, leg.getOfferId(), reference, leg.getPriceMinor()));
                return;
            }
        }
        throw new IllegalArgumentException("no " + operator + " leg in booking " + id);
    }

    private boolean moveTo(BookingStatus target, Instant now) {
        this.status = target;
        this.updatedAt = now;
        return true;
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getTravelerId() { return travelerId; }
    public BookingStatus getStatus() { return status; }
    public long getTotalMinor() { return totalMinor; }
    public String getQuoteId() { return quoteId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<BookingLeg> getLegs() { return List.copyOf(legs); }
}
