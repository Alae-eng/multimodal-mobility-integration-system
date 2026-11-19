package ma.mobility.railbridge.api;

import ma.mobility.railbridge.corba.OperatorRefusedException;
import ma.mobility.railbridge.corba.RailUnavailableException;
import org.omg.CORBA.SystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Refus métier (CORBA NotAvailable) -> 409 NOT_AVAILABLE. */
    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<ErrorResponse> refused(OperatorRefusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NOT_AVAILABLE", e.getMessage()));
    }

    /** Opérateur ferroviaire injoignable -> 503 UPSTREAM_UNAVAILABLE (contrat, section 3). */
    @ExceptionHandler({SystemException.class, RailUnavailableException.class})
    public ResponseEntity<ErrorResponse> upstreamDown(RuntimeException e) {
        // La cause réelle doit rester visible : le client, lui, ne voit qu'un 503 générique
        log.warn("UPSTREAM_UNAVAILABLE : {} (cause: {})", e, e.getCause() == null ? "-" : e.getCause().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Rail operator unreachable"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }
}