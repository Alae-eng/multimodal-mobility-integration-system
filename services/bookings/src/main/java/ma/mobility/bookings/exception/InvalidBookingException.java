package ma.mobility.bookings.exception;

/** The request is well formed but makes no sense (unknown operator, duplicate leg...). Becomes HTTP 400. */
public class InvalidBookingException extends RuntimeException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
