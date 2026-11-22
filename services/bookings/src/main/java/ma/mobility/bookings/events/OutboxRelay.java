package ma.mobility.bookings.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import ma.mobility.bookings.model.OutboxEvent;
import ma.mobility.bookings.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the events recorded in the outbox to the broker, oldest first.
 *
 * Guarantees: an event is never lost (it stays in the database until the broker acknowledged it)
 * and events leave in the order they happened (a failure stops the batch). Delivery is
 * at-least-once: if the service dies between the broker's ack and the database update, the event
 * is sent again, which is why every payload carries a unique eventId for the consumers.
 *
 * While the broker is down the relay backs off (1 s, 2 s, 4 s... up to 30 s) instead of logging
 * and retrying every second.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long MAX_BACKOFF_SECONDS = 30;

    private final OutboxRepository outbox;
    private final EventSender sender;
    private final Clock clock;
    private final TransactionTemplate tx;

    private int consecutiveFailures;
    private Instant nextAttempt = Instant.EPOCH;

    public OutboxRelay(OutboxRepository outbox, EventSender sender, Clock clock, PlatformTransactionManager txManager) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${bookings.outbox.interval:PT1S}",
               initialDelayString = "${bookings.outbox.initial-delay:PT5S}")
    public void run() {
        relayPending();
    }

    /** Returns how many events were sent during this call. */
    public synchronized int relayPending() {
        if (clock.instant().isBefore(nextAttempt)) {
            return 0;
        }
        int sent = 0;
        for (OutboxEvent event : outbox.findTop50BySentAtIsNullOrderByIdAsc()) {
            try {
                sender.send(event);
            } catch (RuntimeException e) {
                onFailure(event, e);
                return sent;
            }
            tx.executeWithoutResult(status -> {
                event.markSent(clock.instant());
                outbox.save(event);
            });
            sent++;
        }
        consecutiveFailures = 0;
        nextAttempt = Instant.EPOCH;
        return sent;
    }

    private void onFailure(OutboxEvent event, RuntimeException e) {
        consecutiveFailures++;
        long delay = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(consecutiveFailures, 5));
        nextAttempt = clock.instant().plus(Duration.ofSeconds(delay));
        log.warn("Event {} ({}) not delivered, will retry in {} s: {}",
                event.getEventId(), event.getEventName(), delay, e.getMessage());
        try {
            tx.executeWithoutResult(status -> {
                event.markFailedAttempt();
                outbox.save(event);
            });
        } catch (RuntimeException dbError) {
            log.error("Could not record the failed attempt of event {}: {}", event.getEventId(), dbError.getMessage());
        }
    }
}
