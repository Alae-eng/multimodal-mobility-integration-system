package ma.mobility.bookings.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Body of every event published by bookings. eventId is unique per event: a consumer that
 * receives the same event twice (the relay is at-least-once) ignores the second one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingEventPayload(
        String eventId,
        String event,
        String bookingId,
        String travelerId,
        String status,
        long totalMinor,
        String quoteId,
        String reason,
        List<Leg> legs,
        Instant occurredAt) {

    public record Leg(String operator, String offerId, String reference, long priceMinor) {
    }
}
