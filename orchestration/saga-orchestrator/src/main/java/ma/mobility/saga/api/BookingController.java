package ma.mobility.saga.api;

import jakarta.validation.Valid;
import ma.mobility.saga.api.model.BookingRequest;
import ma.mobility.saga.api.model.BookingResponse;
import ma.mobility.saga.api.model.Offer;
import ma.mobility.saga.steps.SagaOrchestrator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
    private final SagaOrchestrator orchestrator;

    public BookingController(SagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }

    @GetMapping("/search")
    public List<Offer> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return orchestrator.search(origin, destination, date, traceparent);
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> book(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orchestrator.execute(request, idempotencyKey, traceparent));
    }
}
