package ma.mobility.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the saga sends: the offers it selected (the canonical Offer returned by the operators,
 * contracts/operator-port.md section 1) and the number of travelers.
 */
public record QuoteRequest(
        @NotEmpty(message = "offers must contain at least one offer")
        @Size(max = 10, message = "offers must contain at most 10 offers")
        List<@NotNull(message = "offers must not contain null") @Valid OfferInput> offers,

        @Min(value = 1, message = "travelers must be at least 1")
        @Max(value = 20, message = "travelers must be at most 20")
        int travelers) {
}
