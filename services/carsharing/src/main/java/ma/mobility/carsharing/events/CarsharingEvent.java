package ma.mobility.carsharing.events;

/**
 * Operator-level events published by carsharing on the shared topic exchange.
 *
 * They are deliberately NOT named BookingConfirmed / BookingCancelled: those are the platform's
 * trip-level events (published once per multimodal booking, consumed by notifications). A car
 * leg confirmed before the flight fails is not a confirmed trip, so reusing the names would
 * send travelers duplicate or premature messages.
 */
public enum CarsharingEvent {

    RESERVATION_CONFIRMED("CarReservationConfirmed", "carsharing.reservation.confirmed"),
    RESERVATION_CANCELLED("CarReservationCancelled", "carsharing.reservation.cancelled");

    private final String eventName;
    private final String routingKey;

    CarsharingEvent(String eventName, String routingKey) {
        this.eventName = eventName;
        this.routingKey = routingKey;
    }

    public String eventName() {
        return eventName;
    }

    public String routingKey() {
        return routingKey;
    }
}
