package ma.mobility.airbridge.api;

import ma.mobility.airbridge.soap.OperatorRefusedException;
import ma.mobility.airbridge.soap.UpstreamFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.ws.client.WebServiceIOException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Refus métier (SOAP Fault) -> 409 NOT_AVAILABLE. */
    @ExceptionHandler(OperatorRefusedException.class)
    public ResponseEntity<ErrorResponse> refused(OperatorRefusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NOT_AVAILABLE", e.getMessage()));
    }

    /** Partenaire aérien injoignable -> 503 UPSTREAM_UNAVAILABLE (contrat, section 3). */
    @ExceptionHandler(WebServiceIOException.class)
    public ResponseEntity<ErrorResponse> upstreamDown(WebServiceIOException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Air partner unreachable"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }
}