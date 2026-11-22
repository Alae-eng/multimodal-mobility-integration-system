package ma.mobility.carsharing.dto;

/**
 * Canonical Offer, same shape as the air-bridge Offer.
 * departure/arrival are strings "YYYY-MM-DDTHH:mm:ss" (seconds always present).
 */
public record OfferResponse(
        String offerId,
        String operator,        // always "CARSHARING"
        String origin,
        String destination,
        String departure,
        String arrival,
        long priceMinor,        // centimes of MAD
        int seatsAvailable
) {
}
