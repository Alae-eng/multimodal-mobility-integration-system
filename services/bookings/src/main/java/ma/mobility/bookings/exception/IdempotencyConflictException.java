package ma.mobility.bookings.exception;

/** The same Idempotency-Key was reused for a different trip. Becomes HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key " + key + " was already used with a different request");
    }
}
