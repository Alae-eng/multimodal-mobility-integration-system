package ma.mobility.pricing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A canonical Offer as returned by an operator. Only the fields that matter for the price are
 * read: the other canonical fields (origin, departure...) are accepted and ignored, so the saga
 * can forward offers as they are.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferInput(
        @NotBlank(message = "offerId is required") String offerId,

        @NotBlank(message = "operator is required") String operator,

        @NotNull(message = "priceMinor is required")
        @Min(value = 0, message = "priceMinor must not be negative") Long priceMinor) {
}
