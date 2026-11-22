package ma.mobility.bookings.model;

/** A transition the lifecycle does not allow (for example confirming a cancelled file). Becomes HTTP 409. */
public class IllegalBookingStateException extends RuntimeException {

    public IllegalBookingStateException(String bookingId, BookingStatus from, BookingStatus to) {
        super("Booking " + bookingId + " is " + from + ", it cannot become " + to);
    }
}
