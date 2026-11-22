package ma.mobility.gateway.api;

import ma.mobility.gateway.routing.UpstreamGateway;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.Map;

@RestController
public class GatewayController {
    private final UpstreamGateway upstream;

    public GatewayController(UpstreamGateway upstream) {
        this.upstream = upstream;
    }

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/v1/search")
    public ResponseEntity<String> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String path = UriComponentsBuilder.fromPath("/api/v1/search")
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("date", date)
                .build()
                .encode()
                .toUriString();
        return upstream.getSaga(path, traceparent, idempotencyKey);
    }

    @PostMapping(value = "/api/v1/bookings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createBooking(
            @RequestBody String body,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return upstream.postSaga("/api/v1/bookings", body, traceparent, idempotencyKey);
    }

    @PostMapping(value = "/api/v1/quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createQuote(
            @RequestBody String body,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return upstream.postPricing("/v1/quotes", body, traceparent, idempotencyKey);
    }

    @GetMapping("/api/v1/bookings/{bookingId}")
    public ResponseEntity<String> getBooking(
            @PathVariable String bookingId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return upstream.getBookings("/v1/bookings/" + bookingId, traceparent);
    }

    @GetMapping("/api/v1/notifications")
    public ResponseEntity<String> getNotifications(
            @RequestParam String travelerId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        String path = UriComponentsBuilder.fromPath("/v1/notifications")
                .queryParam("travelerId", travelerId)
                .build()
                .encode()
                .toUriString();
        return upstream.getNotifications(path, traceparent);
    }
}
