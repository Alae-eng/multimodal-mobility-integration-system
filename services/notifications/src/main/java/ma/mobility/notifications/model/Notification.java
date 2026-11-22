package ma.mobility.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A message addressed to a traveler. One row per received event, which is also what makes
 * duplicates harmless: the same event (eventId), or a second event of the same type for the same
 * booking, hits a unique constraint and is ignored.
 */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_booking_type", columnNames = {"bookingId", "type"}),
        indexes = {
                @Index(name = "idx_notification_traveler", columnList = "travelerId, createdAt"),
                @Index(name = "idx_notification_pending", columnList = "status, updatedAt")
        })
public class Notification {

    @Id
    @Column(length = 50)
    private String id;

    /** Id of the event that caused this notification (unique): the same event twice is one notification. */
    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 50)
    private String bookingId;

    @Column(nullable = false, length = 100)
    private String travelerId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    /** Last time this row changed: the retry job waits a minimum age after it before trying again. */
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant sentAt;

    protected Notification() {
        // required by JPA
    }

    public Notification(String eventId, NotificationType type, String bookingId, String travelerId,
                        String channel, String subject, String body, Instant now) {
        this.id = "NOTIF-" + UUID.randomUUID();
        this.eventId = eventId;
        this.type = type;
        this.bookingId = bookingId;
        this.travelerId = travelerId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = NotificationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.attempts++;
        this.status = NotificationStatus.SENT;
        this.lastError = null;
        this.sentAt = now;
        this.updatedAt = now;
    }

    /** A delivery attempt failed: stays PENDING for a retry, or becomes FAILED once out of attempts. */
    public void recordFailedAttempt(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.updatedAt = now;
        if (this.attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        }
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public NotificationType getType() { return type; }
    public String getBookingId() { return bookingId; }
    public String getTravelerId() { return travelerId; }
    public String getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
}
