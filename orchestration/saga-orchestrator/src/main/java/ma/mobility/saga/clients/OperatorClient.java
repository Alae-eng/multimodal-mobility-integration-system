package ma.mobility.saga.clients;

import ma.mobility.saga.api.model.Offer;
import java.time.LocalDate;
import java.util.List;

public interface OperatorClient {
    List<Offer> search(String baseUrl, String origin, String destination, LocalDate date, String traceparent);
    String hold(String baseUrl, String offerId, int seats, String idempotencyKey, String traceparent);
    String confirm(String baseUrl, String holdId, String idempotencyKey, String traceparent);
    void cancel(String baseUrl, String reference, String traceparent);
}
