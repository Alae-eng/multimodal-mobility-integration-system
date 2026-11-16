package ma.mobility.airbridge.soap;

/** Échec technique côté partenaire (fault inattendu) -> HTTP 503 UPSTREAM_UNAVAILABLE. */
public class UpstreamFailureException extends RuntimeException {
    public UpstreamFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}