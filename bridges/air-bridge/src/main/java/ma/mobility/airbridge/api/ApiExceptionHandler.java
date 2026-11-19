package ma.mobility.airbridge.api;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import ma.mobility.airbridge.soap.OperatorRefusedException;
import ma.mobility.airbridge.soap.UpstreamFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.ws.client.WebServiceIOException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Refus métier (SOAP Fault) -> 409 NOT_AVAILABLE. */
    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<ErrorResponse> refused(OperatorRefusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NOT_AVAILABLE", e.getMessage()));
    }

    /** Partenaire aérien injoignable ou en échec technique -> 503 UPSTREAM_UNAVAILABLE. */
    @ExceptionHandler({WebServiceIOException.class, UpstreamFailureException.class})
    public ResponseEntity<ErrorResponse> upstreamDown(RuntimeException e) {
        // La cause réelle doit rester visible : le client, lui, ne voit qu'un 503 générique
        log.warn("UPSTREAM_UNAVAILABLE : {} (cause: {})", e, e.getCause() == null ? "-" : e.getCause().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Air partner unreachable"));
    }

    /** Circuit ouvert : on échoue tout de suite, sans appeler le partenaire. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> circuitOpen(CallNotPermittedException e) {
        log.warn("UPSTREAM_UNAVAILABLE : circuit breaker ouvert ({})", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Air partner circuit open"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }
}