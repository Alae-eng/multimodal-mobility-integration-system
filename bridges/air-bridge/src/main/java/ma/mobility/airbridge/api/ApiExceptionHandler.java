package ma.mobility.airbridge.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.ws.client.WebServiceIOException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Partenaire aérien injoignable -> 503 UPSTREAM_UNAVAILABLE (contrat, section 3). */
    @ExceptionHandler(WebServiceIOException.class)
    public ResponseEntity<ErrorResponse> upstreamDown(WebServiceIOException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Air partner unreachable"));
    }
}