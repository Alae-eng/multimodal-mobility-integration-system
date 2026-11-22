package ma.mobility.saga.api;

import ma.mobility.saga.api.model.BookingResponse;
import ma.mobility.saga.steps.SagaFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SagaFailureException.class)
    public ResponseEntity<BookingResponse> handleSagaFailure(SagaFailureException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BookingResponse(false, null, failure.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BookingResponse> handleValidation(MethodArgumentNotValidException failure) {
        return ResponseEntity.badRequest()
                .body(new BookingResponse(false, null, "Invalid booking request", List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BookingResponse> handleInvalidOffer(IllegalArgumentException failure) {
        return ResponseEntity.badRequest()
                .body(new BookingResponse(false, null, failure.getMessage(), List.of()));
    }
}
