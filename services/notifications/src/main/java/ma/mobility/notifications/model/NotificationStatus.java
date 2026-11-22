package ma.mobility.notifications.model;

/**
 * PENDING: recorded, not delivered yet (or the last attempt failed and a retry is due).
 * SENT: delivered. FAILED: gave up after the maximum number of attempts.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
