package ma.mobility.bookings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One operator's part of the trip, as the saga knows it after the hold phase:
 * the offer and the holdId returned by the operator.
 */
public record LegInput(
        @NotBlank(message = "operator is required") String operator,

        @NotBlank(message = "offerId is required")
        @Size(max = 100, message = "offerId is too long") String offerId,

        @NotBlank(message = "reference is required")
        @Size(max = 100, message = "reference is too long") String reference,

        @NotNull(message = "priceMinor is required")
        @Min(value = 0, message = "priceMinor must not be negative") Long priceMinor) {
}
