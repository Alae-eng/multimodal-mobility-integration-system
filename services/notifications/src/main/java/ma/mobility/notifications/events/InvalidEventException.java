package ma.mobility.notifications.events;

/** The message is not a usable platform event. Retrying cannot fix it, so it goes to the dead-letter queue. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
