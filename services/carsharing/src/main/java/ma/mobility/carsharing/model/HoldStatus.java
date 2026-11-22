package ma.mobility.carsharing.model;

public enum HoldStatus {
    HELD,       // temporary reservation, expires after 10 minutes
    CONFIRMED,  // final booking, has a bookingRef
    CANCELLED,  // cancelled by the saga (compensation) or by a client
    EXPIRED     // hold older than 10 minutes, released by HoldExpirationJob
}