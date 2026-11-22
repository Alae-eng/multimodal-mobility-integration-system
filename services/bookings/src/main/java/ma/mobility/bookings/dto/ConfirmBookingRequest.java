package ma.mobility.bookings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The bookingRef each operator returned when the saga confirmed its hold. */
public record ConfirmBookingRequest(
        @NotEmpty(message = "references must contain at least one reference")
        @Size(max = 3, message = "references must contain at most 3 references")
        List<@NotNull(message = "references must not contain null") @Valid LegReference> references) {

    public record LegReference(
            @NotBlank(message = "operator is required") String operator,
            @NotBlank(message = "bookingRef is required")
            @Size(max = 100, message = "bookingRef is too long") String bookingRef) {
    }
}
