package ma.mobility.airbridge.api;

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