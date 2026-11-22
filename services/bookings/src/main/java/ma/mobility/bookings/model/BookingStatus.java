package ma.mobility.bookings.model;

/**
 * Lifecycle of a travel file. PENDING is the only state with no final answer yet.
 *
 * <pre>
 *   PENDING --confirm--> CONFIRMED --cancel--> CANCELLED
 *      |  \--cancel--> CANCELLED
 *      \----fail----> FAILED          (cancelling a FAILED file does nothing)
 * </pre>
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
