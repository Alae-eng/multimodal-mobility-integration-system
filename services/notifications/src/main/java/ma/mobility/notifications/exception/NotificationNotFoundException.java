package ma.mobility.notifications.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String id) {
        super("Unknown notification: " + id);
    }
}
