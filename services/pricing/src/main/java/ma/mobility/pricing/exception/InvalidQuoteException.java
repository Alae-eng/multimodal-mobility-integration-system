package ma.mobility.pricing.exception;

/** The request is well formed but cannot be priced (unknown operator, duplicate offer...). Becomes HTTP 400. */
public class InvalidQuoteException extends RuntimeException {

    public InvalidQuoteException(String message) {
        super(message);
    }
}
