package ma.mobility.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The saga gave up before confirming. */
public record FailBookingRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason is too long") String reason) {
}
