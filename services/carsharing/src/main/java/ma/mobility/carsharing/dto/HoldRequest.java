package ma.mobility.carsharing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record HoldRequest(
        @NotBlank(message = "offerId is required") String offerId,
        @Min(value = 1, message = "seats must be at least 1") int seats
) {
}