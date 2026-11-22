package ma.mobility.saga.steps;

import ma.mobility.saga.api.model.BookingRequest;
import ma.mobility.saga.api.model.BookingResponse;
import ma.mobility.saga.api.model.Offer;
import ma.mobility.saga.clients.OperatorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SagaOrchestrator {
    private final OperatorClient operatorClient;
    private final String railBaseUrl;
    private final String airBaseUrl;
    private final String carsharingBaseUrl;
    private final Map<String, BookingResponse> completedSagas = new ConcurrentHashMap<>();

    public SagaOrchestrator(
            OperatorClient operatorClient,
            @Value("${operators.rail-base-url}") String railBaseUrl,
            @Value("${operators.air-base-url}") String airBaseUrl,
            @Value("${operators.carsharing-base-url}") String carsharingBaseUrl) {
        this.operatorClient = operatorClient;
        this.railBaseUrl = railBaseUrl;
        this.airBaseUrl = airBaseUrl;
        this.carsharingBaseUrl = carsharingBaseUrl;
    }

    public List<Offer> search(String origin, String destination, LocalDate date, String traceparent) {
        List<Offer> offers = new ArrayList<>();
        offers.addAll(operatorClient.search(railBaseUrl, origin, destination, date, traceparent));
        offers.addAll(operatorClient.search(airBaseUrl, origin, destination, date, traceparent));
        offers.addAll(operatorClient.search(carsharingBaseUrl, origin, destination, date, traceparent));
        return offers;
    }

    public BookingResponse execute(BookingRequest request, String idempotencyKey, String traceparent) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            BookingResponse existing = completedSagas.get(idempotencyKey);
            if (existing != null) {
                return existing;
            }
        }

        String tripId = "TRIP-" + UUID.randomUUID();
        List<HoldReference> holds = new ArrayList<>();
        List<BookingReference> bookings = new ArrayList<>();

        try {
            for (int i = 0; i < request.offerIds().size(); i++) {
                String offerId = request.offerIds().get(i);
                String baseUrl = getOperatorUrl(offerId);
                String holdKey = buildKey(idempotencyKey, "hold", i);
                String holdId = operatorClient.hold(baseUrl, offerId, request.seats(), holdKey, traceparent);
                holds.add(new HoldReference(baseUrl, holdId));
            }

            List<HoldReference> pendingHolds = new ArrayList<>(holds);
            for (int i = 0; i < pendingHolds.size(); i++) {
                HoldReference hold = pendingHolds.get(i);
                String confirmKey = buildKey(idempotencyKey, "confirm", i);
                String bookingRef = operatorClient.confirm(
                        hold.baseUrl(), hold.holdId(), confirmKey, traceparent);
                bookings.add(new BookingReference(hold.baseUrl(), bookingRef));
                holds.remove(hold);
            }

            BookingResponse response = new BookingResponse(
                    true,
                    tripId,
                    "Booking confirmed",
                    bookings.stream().map(BookingReference::reference).toList());
            remember(idempotencyKey, response);
            return response;
        } catch (Exception failure) {
            compensate(bookings, holds, traceparent);
            String message = failure.getMessage();
            if (message == null || message.isBlank()) {
                message = "Booking failed";
            }
            throw new SagaFailureException(message, failure);
        }
    }

    private void compensate(List<BookingReference> bookings,
                            List<HoldReference> holds,
                            String traceparent) {
        for (int i = bookings.size() - 1; i >= 0; i--) {
            BookingReference booking = bookings.get(i);
            safeCancel(booking.baseUrl(), booking.reference(), traceparent);
        }
        for (int i = holds.size() - 1; i >= 0; i--) {
            HoldReference hold = holds.get(i);
            safeCancel(hold.baseUrl(), hold.holdId(), traceparent);
        }
    }

    private void safeCancel(String baseUrl, String reference, String traceparent) {
        try {
            operatorClient.cancel(baseUrl, reference, traceparent);
        } catch (Exception ignored) {
        }
    }

    private void remember(String idempotencyKey, BookingResponse response) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            completedSagas.put(idempotencyKey, response);
        }
    }

    private String buildKey(String idempotencyKey, String phase, int index) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey + ":" + phase + ":" + index;
    }

    String getOperatorUrl(String reference) {
        String normalized = reference == null ? "" : reference.toUpperCase();
        if (normalized.startsWith("RAIL") || normalized.contains("RAIL")) {
            return railBaseUrl;
        }
        if (normalized.startsWith("AIR") || normalized.contains("AIR")) {
            return airBaseUrl;
        }
        if (normalized.startsWith("CAR") || normalized.contains("CAR")) {
            return carsharingBaseUrl;
        }
        throw new IllegalArgumentException("Unknown operator for offerId: " + reference);
    }

    private record HoldReference(String baseUrl, String holdId) {}
    private record BookingReference(String baseUrl, String reference) {}
}
