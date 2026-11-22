package ma.mobility.bookings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import ma.mobility.bookings.model.Booking;

/** All amounts are whole numbers of centimes of MAD. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        String bookingId,
        String travelerId,
        String status,
        long totalMinor,
        String currency,
        String quoteId,
        String failureReason,
        List<Leg> legs,
        Instant createdAt,
        Instant updatedAt) {

    public record Leg(String operator, String offerId, String reference, long priceMinor) {
    }

    public static BookingResponse from(Booking b) {
        List<Leg> legs = b.getLegs().stream()
                .map(l -> new Leg(l.getOperator().name(), l.getOfferId(), l.getReference(), l.getPriceMinor()))
                .toList();
        return new BookingResponse(b.getId(), b.getTravelerId(), b.getStatus().name(), b.getTotalMinor(), "MAD",
                b.getQuoteId(), b.getFailureReason(), legs, b.getCreatedAt(), b.getUpdatedAt());
    }
}
