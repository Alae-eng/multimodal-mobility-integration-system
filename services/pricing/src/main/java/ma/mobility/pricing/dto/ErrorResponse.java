package ma.mobility.pricing.dto;

/** Common error body of the platform (contracts/operator-port.md, section 3). */
public record ErrorResponse(String error, String message) {
}
