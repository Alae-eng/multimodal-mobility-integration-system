package ma.mobility.pricing.exception;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String quoteId) {
        super("Unknown quote: " + quoteId);
    }
}
