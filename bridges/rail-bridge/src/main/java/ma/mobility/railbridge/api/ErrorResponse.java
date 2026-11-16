package ma.mobility.railbridge.api;

/** Corps d'erreur commun (contracts/operator-port.md, section 3). */
public record ErrorResponse(String error, String message) {
}