package ma.mobility.saga.clients;

import ma.mobility.saga.api.model.Offer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@Component
public class WebClientOperatorClient implements OperatorClient {
    private final WebClient webClient;

    public WebClientOperatorClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public List<Offer> search(String baseUrl, String origin, String destination, LocalDate date, String traceparent) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/v1/offers")
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("date", date)
                .build()
                .toUriString();
        var request = webClient.get()
                .uri(url)
                .headers(headers -> addTrace(headers, traceparent));
        return request.retrieve()
                .bodyToFlux(Offer.class)
                .collectList()
                .block();
    }

    @Override
    public String hold(String baseUrl, String offerId, int seats, String idempotencyKey, String traceparent) {
        record HoldRequest(String offerId, int seats) {}
        record HoldResponse(String holdId) {}

        var request = webClient.post()
                .uri(baseUrl + (baseUrl.endsWith("/") ? "v1/holds" : "/v1/holds"))
                .headers(headers -> {
                    addTrace(headers, traceparent);
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .bodyValue(new HoldRequest(offerId, seats));
        return request.retrieve()
                .bodyToMono(HoldResponse.class)
                .map(HoldResponse::holdId)
                .block();
    }

    @Override
    public String confirm(String baseUrl, String holdId, String idempotencyKey, String traceparent) {
        record ConfirmResponse(String bookingRef) {}

        var request = webClient.post()
                .uri(baseUrl + (baseUrl.endsWith("/") ? "v1/holds/" : "/v1/holds/") + holdId + "/confirm")
                .headers(headers -> {
                    addTrace(headers, traceparent);
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                });
        return request.retrieve()
                .bodyToMono(ConfirmResponse.class)
                .map(ConfirmResponse::bookingRef)
                .block();
    }

    @Override
    public void cancel(String baseUrl, String reference, String traceparent) {
        webClient.delete()
                .uri(baseUrl + (baseUrl.endsWith("/") ? "v1/references/" : "/v1/references/") + reference)
                .headers(headers -> addTrace(headers, traceparent))
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(WebClientResponseException.class, error ->
                        error.getStatusCode().is5xxServerError()
                                ? reactor.core.publisher.Mono.error(error)
                                : reactor.core.publisher.Mono.empty())
                .block();
    }

    private void addTrace(org.springframework.http.HttpHeaders headers, String traceparent) {
        if (traceparent != null && !traceparent.isBlank()) {
            headers.set("traceparent", traceparent);
        }
    }
}
