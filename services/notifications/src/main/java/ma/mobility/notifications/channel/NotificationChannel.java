package ma.mobility.notifications.channel;

import ma.mobility.notifications.model.Notification;

/**
 * How a message reaches the traveler (email, SMS, push...). The events carry the traveler id but
 * no address, so the shipped channel only simulates the sending; a real provider replaces it by
 * implementing this interface, nothing else changes.
 */
public interface NotificationChannel {

    /** Short name stored with each notification (for example EMAIL). */
    String name();

    /**
     * Delivers the notification. Must return only once the message was accepted by the provider,
     * and should enforce its own timeout: it runs while the notification row is locked.
     *
     * @throws RuntimeException when the message was not delivered (the notification will be retried)
     */
    void deliver(Notification notification);
}
