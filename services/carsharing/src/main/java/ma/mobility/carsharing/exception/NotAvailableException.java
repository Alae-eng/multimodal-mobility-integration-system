package ma.mobility.carsharing.exception;

/** Business refusal: unknown offer, vehicle already taken, hold expired... Becomes HTTP 409. */
public class NotAvailableException extends RuntimeException {

    public NotAvailableException(String message) {
        super(message);
    }
}