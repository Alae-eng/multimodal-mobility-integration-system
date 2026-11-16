package ma.mobility.railbridge.api;

/** Offre canonique (contracts/operator-port.md, section 1). */
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
