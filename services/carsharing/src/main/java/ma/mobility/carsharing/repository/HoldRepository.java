package ma.mobility.carsharing.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.mobility.carsharing.model.Hold;
import ma.mobility.carsharing.model.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, String> {

    /** Idempotency: the same Idempotency-Key must return the same hold. */
    Optional<Hold> findByIdempotencyKey(String idempotencyKey);

    /** HELD holds older than the cutoff (now - hold TTL), read without a lock. */
    List<Hold> findByStatusAndCreatedAtBefore(HoldStatus status, Instant cutoff);

    /**
     * Row-locking reads (SELECT ... FOR UPDATE). confirm, cancel and expiry all go through these,
     * so two concurrent calls on the same hold are serialized and the second one sees the result
     * of the first (same bookingRef, no double transition, no stale overwrite).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.bookingRef = :bookingRef")
    Optional<Hold> findByBookingRefForUpdate(@Param("bookingRef") String bookingRef);
}
