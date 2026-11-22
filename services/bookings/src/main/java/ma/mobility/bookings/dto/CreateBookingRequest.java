package ma.mobility.bookings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the saga sends to open a travel file once it holds the resources of every operator.
 * totalMinor is the amount to charge, taken from the pricing quote (centimes of MAD).
 */
public record CreateBookingRequest(
        @NotBlank(message = "travelerId is required")
        @Size(max = 100, message = "travelerId is too long") String travelerId,

        @NotEmpty(message = "legs must contain at least one leg")
        @Size(max = 3, message = "legs must contain at most 3 legs (one per operator)")
        List<@NotNull(message = "legs must not contain null") @Valid LegInput> legs,

        @NotNull(message = "totalMinor is required")
        @Min(value = 0, message = "totalMinor must not be negative") Long totalMinor,

        @Size(max = 50, message = "quoteId is too long") String quoteId) {
}
