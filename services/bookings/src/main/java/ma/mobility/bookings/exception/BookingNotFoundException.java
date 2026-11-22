package ma.mobility.bookings.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("Unknown booking: " + bookingId);
    }
}
