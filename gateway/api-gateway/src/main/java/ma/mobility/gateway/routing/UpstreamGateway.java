package ma.mobility.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class UpstreamGateway {
    private final WebClient webClient;
    private final String sagaUrl;
    private final String pricingUrl;
    private final String bookingsUrl;
    private final String notificationsUrl;

    public UpstreamGateway(
            WebClient.Builder builder,
            @Value("${gateway.saga-url}") String sagaUrl,
            @Value("${gateway.pricing-url}") String pricingUrl,
            @Value("${gateway.bookings-url}") String bookingsUrl,
            @Value("${gateway.notifications-url}") String notificationsUrl) {
        this.webClient = builder.build();
        this.sagaUrl = sagaUrl;
        this.pricingUrl = pricingUrl;
        this.bookingsUrl = bookingsUrl;
        this.notificationsUrl = notificationsUrl;
    }

    public ResponseEntity<String> getSaga(String path, String traceparent, String idempotencyKey) {
        return get(sagaUrl + path, traceparent, idempotencyKey);
    }

    public ResponseEntity<String> postSaga(String path, String body,
                                            String traceparent, String idempotencyKey) {
        return post(sagaUrl + path, body, traceparent, idempotencyKey);
    }

    public ResponseEntity<String> postPricing(String path, String body,
                                              String traceparent, String idempotencyKey) {
        return post(pricingUrl + path, body, traceparent, idempotencyKey);
    }

    public ResponseEntity<String> getBookings(String path, String traceparent) {
        return get(bookingsUrl + path, traceparent, null);
    }

    public ResponseEntity<String> getNotifications(String path, String traceparent) {
        return get(notificationsUrl + path, traceparent, null);
    }

    private ResponseEntity<String> get(String url, String traceparent, String idempotencyKey) {
        return webClient.get()
                .uri(url)
                .headers(headers -> addHeaders(headers, traceparent, idempotencyKey))
                .retrieve()
                .toEntity(String.class)
                .block();
    }

    private ResponseEntity<String> post(String url, String body,
                                        String traceparent, String idempotencyKey) {
        return webClient.post()
                .uri(url)
                .headers(headers -> addHeaders(headers, traceparent, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toEntity(String.class)
                .block();
    }

    private void addHeaders(HttpHeaders headers, String traceparent, String idempotencyKey) {
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (traceparent != null && !traceparent.isBlank()) {
            headers.set("traceparent", traceparent);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
    }
}
