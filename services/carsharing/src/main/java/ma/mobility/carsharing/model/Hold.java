package ma.mobility.carsharing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holds", indexes = {
        @Index(name = "idx_holds_status_created", columnList = "status, createdAt"),
        @Index(name = "idx_holds_booking_ref", columnList = "bookingRef")
})
public class Hold {

    /** The holdId returned to the saga. */
    @Id
    @Column(length = 50)
    private String id;

    /** Id of the held Vehicle (a plain column, no JPA relation, to keep it simple). */
    @Column(nullable = false, length = 50)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    /**
     * From the Idempotency-Key header of POST /v1/holds.
     * Unique: the same key can never create two holds. Nullable (PostgreSQL allows many NULLs).
     */
    @Column(unique = true, length = 100)
    private String idempotencyKey;

    /** Set only when the hold is confirmed. */
    @Column(unique = true, length = 50)
    private String bookingRef;

    @Column(nullable = false)
    private Instant createdAt;

    protected Hold() {
        // required by JPA
    }

    public Hold(String vehicleId, String idempotencyKey, Instant createdAt) {
        this.id = "CARHOLD-" + UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.idempotencyKey = idempotencyKey;
        this.status = HoldStatus.HELD;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** HELD -> CONFIRMED. Generates the booking reference. */
    public void confirm() {
        this.status = HoldStatus.CONFIRMED;
        this.bookingRef = "CARBK-" + UUID.randomUUID();
    }

    /** Safe to call several times (cancel must be idempotent). */
    public void cancel() {
        if (status == HoldStatus.HELD || status == HoldStatus.CONFIRMED) {
            this.status = HoldStatus.CANCELLED;
        }
    }

    public void expire() {
        if (status == HoldStatus.HELD) {
            this.status = HoldStatus.EXPIRED;
        }
    }

    public boolean isActive() {
        return status == HoldStatus.HELD || status == HoldStatus.CONFIRMED;
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public HoldStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getBookingRef() { return bookingRef; }
    public Instant getCreatedAt() { return createdAt; }
}