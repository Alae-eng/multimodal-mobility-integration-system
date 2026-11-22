package ma.mobility.notifications.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.mobility.notifications.channel.NotificationChannel;
import ma.mobility.notifications.events.IncomingEvent;
import ma.mobility.notifications.model.Notification;
import ma.mobility.notifications.model.NotificationStatus;
import ma.mobility.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns platform events into messages for the traveler.
 *
 * Duplicates are ignored, whatever their origin: the broker delivers at least once, the producer's
 * relay may send an event twice, a redelivery follows a crash. Two unique constraints decide it in
 * the database (not a check-then-insert, which two threads could both pass):
 * - the event id: the same event twice;
 * - the (booking, type) pair: the same news about the same booking, even under another event id.
 *
 * Recording and delivering are two separate steps. The notification is first saved as PENDING (so
 * the event can be acknowledged and is never lost), then delivered under a row lock. A crash, a
 * channel failure or a lost race leaves it PENDING and the retry job finishes it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final MessageComposer composer;
    private final NotificationChannel channel;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration retryMinAge;
    private final int maxAttempts;

    public NotificationService(NotificationRepository notifications, MessageComposer composer,
                               NotificationChannel channel, Clock clock, PlatformTransactionManager txManager,
                               @Value("${notifications.retry.min-age:PT30S}") Duration retryMinAge,
                               @Value("${notifications.retry.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("notifications.retry.max-attempts must be between 1 and 100");
        }
        this.notifications = notifications;
        this.composer = composer;
        this.channel = channel;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.retryMinAge = retryMinAge;
        this.maxAttempts = maxAttempts;
    }

    // ------------------------------------------------------------------ receive

    /** Called for every event received. Safe to call any number of times with the same event. */
    public void handle(IncomingEvent event) {
        Optional<String> id = record(event);
        id.ifPresent(this::deliver);
    }

    /** Saves the notification, or returns empty when it is a duplicate (and does nothing). */
    private Optional<String> record(IncomingEvent event) {
        if (notifications.existsByEventId(event.eventId())
                || notifications.existsByBookingIdAndType(event.bookingId(), event.type())) {
            log.info("Duplicate {} for booking {} ignored (event {})", event.type().eventName(),
                    event.bookingId(), event.eventId());
            return Optional.empty();
        }
        MessageComposer.Message message = composer.compose(event);
        Notification notification = new Notification(event.eventId(), event.type(), event.bookingId(),
                event.travelerId(), channel.name(), message.subject(), message.body(), clock.instant());
        try {
            tx.execute(status -> notifications.saveAndFlush(notification));
            return Optional.of(notification.getId());
        } catch (DataIntegrityViolationException race) {
            // Two copies of the event were processed at the same moment: the other one won.
            log.info("Duplicate {} for booking {} ignored (lost the race)", event.type().eventName(), event.bookingId());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ deliver

    /**
     * Delivers one notification. The row is locked while the channel is called, so the immediate
     * delivery and the retry job can never both send it. A failure is recorded and never thrown:
     * the event was recorded, the retry job owns it from here, and the broker must not redeliver it.
     */
    void deliver(String notificationId) {
        try {
            tx.executeWithoutResult(status -> {
                Notification n = notifications.findByIdForUpdate(notificationId).orElse(null);
                if (n == null || n.getStatus() != NotificationStatus.PENDING) {
                    return;                      // already sent or given up: nothing to do
                }
                try {
                    channel.deliver(n);
                    n.markSent(clock.instant());
                } catch (RuntimeException e) {
                    n.recordFailedAttempt(e.getMessage(), clock.instant(), maxAttempts);
                    log.warn("Notification {} not delivered (attempt {}/{}): {}", n.getId(), n.getAttempts(),
                            maxAttempts, e.getMessage());
                }
                notifications.save(n);
            });
        } catch (RuntimeException e) {
            log.error("Notification {} could not be processed, the retry job will pick it up: {}",
                    notificationId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------- retry

    /**
     * Called by the retry job: delivers the notifications that are still PENDING and have waited
     * at least the minimum age since their last change (so it never races the delivery that is
     * still in progress). Returns how many were sent.
     */
    public int retryPending() {
        Instant cutoff = clock.instant().minus(retryMinAge);
        List<Notification> due = notifications
                .findTop50ByStatusAndAttemptsLessThanAndUpdatedAtBeforeOrderByCreatedAtAsc(
                        NotificationStatus.PENDING, maxAttempts, cutoff);
        int sent = 0;
        for (Notification n : due) {
            deliver(n.getId());
            if (notifications.findById(n.getId()).map(x -> x.getStatus() == NotificationStatus.SENT).orElse(false)) {
                sent++;
            }
        }
        return sent;
    }

    // -------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<Notification> listForTraveler(String travelerId) {
        return notifications.findTop100ByTravelerIdOrderByCreatedAtDesc(travelerId.trim());
    }

    @Transactional(readOnly = true)
    public Optional<Notification> find(String id) {
        return notifications.findById(id);
    }
}
