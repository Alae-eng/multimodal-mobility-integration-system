package ma.mobility.bookings.events;

/** The platform events announced by bookings (event names agreed by the team). */
public enum BookingEvent {

    BOOKING_CONFIRMED("BookingConfirmed", "booking.confirmed"),
    BOOKING_CANCELLED("BookingCancelled", "booking.cancelled"),
    SAGA_FAILED("SagaFailed", "saga.failed");

    private final String eventName;
    private final String routingKey;

    BookingEvent(String eventName, String routingKey) {
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
