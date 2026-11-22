package ma.mobility.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import ma.mobility.notifications.model.Notification;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String notificationId,
        String type,
        String bookingId,
        String travelerId,
        String channel,
        String subject,
        String body,
        String status,
        int attempts,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().eventName(), n.getBookingId(), n.getTravelerId(),
                n.getChannel(), n.getSubject(), n.getBody(), n.getStatus().name(), n.getAttempts(),
                n.getCreatedAt(), n.getSentAt());
    }
}
