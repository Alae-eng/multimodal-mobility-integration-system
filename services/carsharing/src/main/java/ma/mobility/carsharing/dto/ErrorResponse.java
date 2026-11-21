package ma.mobility.carsharing.dto;

/** Body of every 409 / 400 / 503 answer. Example: {"error":"NOT_AVAILABLE","message":"..."} */
public record ErrorResponse(String error, String message) {
}