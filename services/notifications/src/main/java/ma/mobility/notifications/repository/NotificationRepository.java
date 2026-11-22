package ma.mobility.notifications.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.mobility.notifications.model.Notification;
import ma.mobility.notifications.model.NotificationStatus;
import ma.mobility.notifications.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    boolean existsByEventId(String eventId);

    boolean existsByBookingIdAndType(String bookingId, NotificationType type);

    /** The traveler's latest notifications, most recent first. */
    List<Notification> findTop100ByTravelerIdOrderByCreatedAtDesc(String travelerId);

    /** Notifications that still have to be delivered and have waited long enough since the last try. */
    List<Notification> findTop50ByStatusAndAttemptsLessThanAndUpdatedAtBeforeOrderByCreatedAtAsc(
            NotificationStatus status, int maxAttempts, Instant cutoff);

    /**
     * Row-locking read (SELECT ... FOR UPDATE): the immediate delivery and the retry job can never
     * deliver the same notification at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") String id);
}
