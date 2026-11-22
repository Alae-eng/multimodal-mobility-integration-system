package ma.mobility.notifications.events;

import java.util.List;
import ma.mobility.notifications.model.NotificationType;

/** A platform event, read from the broker and checked: every field a notification needs is present. */
public record IncomingEvent(
        String eventId,
        NotificationType type,
        String bookingId,
        String travelerId,
        Long totalMinor,
        List<Leg> legs) {

    public record Leg(String operator, String reference) {
    }
}
