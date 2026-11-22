package ma.mobility.gateway.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> upstreamFailure(WebClientResponseException failure) {
        return ResponseEntity.status(failure.getStatusCode())
                .body(Map.of(
                        "success", false,
                        "status", failure.getStatusCode().value(),
                        "message", "Upstream request failed",
                        "upstream", failure.getResponseBodyAsString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> gatewayFailure(Exception failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "success", false,
                        "status", 502,
                        "message", "Gateway could not reach an upstream service"));
    }
}
