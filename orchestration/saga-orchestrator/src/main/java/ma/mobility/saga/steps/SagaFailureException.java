package ma.mobility.saga.steps;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class SagaFailureException extends RuntimeException {
    public SagaFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
