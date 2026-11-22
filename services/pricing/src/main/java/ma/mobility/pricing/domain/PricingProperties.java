package ma.mobility.pricing.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Multimodal discount, in basis points (100 bps = 1 %). A trip that combines several operators
 * is cheaper than the same legs bought separately. Set both to 0 to switch the discount off.
 */
@Validated
@ConfigurationProperties(prefix = "pricing.discount")
public record PricingProperties(
        @DefaultValue("500") @Min(0) @Max(5000) int twoOperatorsBps,
        @DefaultValue("1000") @Min(0) @Max(5000) int threeOperatorsBps) {
}
