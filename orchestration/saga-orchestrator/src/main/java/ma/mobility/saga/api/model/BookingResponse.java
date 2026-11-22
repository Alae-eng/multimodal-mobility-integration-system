package ma.mobility.saga.api.model;

import java.util.List;

public record BookingResponse(
        boolean success,
        String tripId,
        String message,
        List<String> bookingRefs) {
}
