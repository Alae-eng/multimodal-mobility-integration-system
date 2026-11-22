package ma.mobility.bookings.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.mobility.bookings.model.Booking;
import ma.mobility.bookings.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, String> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /** The traveler's most recent travel files. */
    List<Booking> findTop100ByTravelerIdOrderByCreatedAtDesc(String travelerId);

    /** PENDING files created before the cutoff, read without a lock. */
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);

    /**
     * Row-locking read (SELECT ... FOR UPDATE). confirm, cancel, fail and expiry all go through
     * this, so two concurrent calls on one file are serialized and the second one sees the
     * result of the first: a replay is a no-op, a race can never overwrite a newer state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") String id);
}
