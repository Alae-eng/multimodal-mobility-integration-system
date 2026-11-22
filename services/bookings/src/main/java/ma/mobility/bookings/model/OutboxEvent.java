package ma.mobility.bookings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An event waiting to be sent to the broker (transactional outbox). It is written in the same
 * database transaction as the booking change, so a booking change and its event either both
 * exist or neither does. The relay sends it and stamps sentAt once the broker acknowledged it.
 */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_pending", columnList = "sentAt, id"))
public class OutboxEvent {

    /** Sequence number: events are sent in the order they were recorded. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique id carried in the payload, so consumers can ignore a duplicate delivery. */
    @Column(nullable = false, unique = true, length = 50)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventName;

    @Column(nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, length = 8000)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String eventId, String eventName, String routingKey, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markSent(Instant now) {
        this.sentAt = now;
        this.attempts++;
    }

    public void markFailedAttempt() {
        this.attempts++;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventName() { return eventName; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
}
