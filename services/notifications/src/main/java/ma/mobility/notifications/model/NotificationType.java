package ma.mobility.notifications.model;

import java.util.Arrays;
import java.util.Optional;

/** The platform events this service reacts to (names and routing keys agreed by the team). */
public enum NotificationType {

    BOOKING_CONFIRMED("BookingConfirmed", "booking.confirmed"),
    BOOKING_CANCELLED("BookingCancelled", "booking.cancelled"),
    SAGA_FAILED("SagaFailed", "saga.failed");

    private final String eventName;
    private final String routingKey;

    NotificationType(String eventName, String routingKey) {
        this.eventName = eventName;
        this.routingKey = routingKey;
    }

    public String eventName() {
        return eventName;
    }

    public String routingKey() {
        return routingKey;
    }

    public static Optional<NotificationType> fromEventName(String eventName) {
        return Arrays.stream(values()).filter(t -> t.eventName.equals(eventName)).findFirst();
    }

    public static Optional<NotificationType> fromRoutingKey(String routingKey) {
        return Arrays.stream(values()).filter(t -> t.routingKey.equals(routingKey)).findFirst();
    }
}
