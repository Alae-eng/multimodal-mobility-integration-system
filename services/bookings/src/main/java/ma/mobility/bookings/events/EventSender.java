package ma.mobility.bookings.events;

import ma.mobility.bookings.model.OutboxEvent;

/** Delivers one event to the broker. Returns normally only once the broker has acknowledged it. */
public interface EventSender {

    /** @throws EventSendException when the event was not acknowledged (the relay will retry it) */
    void send(OutboxEvent event);

    class EventSendException extends RuntimeException {

        public EventSendException(String message, Throwable cause) {
            super(message, cause);
        }

        public EventSendException(String message) {
            super(message);
        }
    }
}
