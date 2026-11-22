package ma.mobility.saga.api.model;

public record Offer(
        String offerId,
        String operator,
        String origin,
        String destination,
        String departure,
        String arrival,
        long priceMinor,
        int seatsAvailable) {
}
