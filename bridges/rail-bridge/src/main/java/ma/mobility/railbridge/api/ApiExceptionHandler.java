package ma.mobility.railbridge.api;

import ma.mobility.railbridge.corba.RailUnavailableException;
import org.omg.CORBA.SystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Opérateur ferroviaire injoignable -> 503 UPSTREAM_UNAVAILABLE (contrat, section 3). */
    @ExceptionHandler({SystemException.class, RailUnavailableException.class})
    public ResponseEntity<ErrorResponse> upstreamDown(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("UPSTREAM_UNAVAILABLE", "Rail operator unreachable"));
    }
}