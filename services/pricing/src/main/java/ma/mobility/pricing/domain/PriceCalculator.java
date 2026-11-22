package ma.mobility.pricing.domain;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure pricing rules, no I/O. Every amount is a whole number of centimes of MAD (long): there is
 * no floating point anywhere, so a total is exactly reproducible.
 *
 * <pre>
 *   line     = unit price x (travelers for RAIL/AIR, 1 for CARSHARING)
 *   subtotal = sum of the lines
 *   discount = subtotal x rate(number of distinct operators), rounded half up to the centime
 *   total    = subtotal - discount
 * </pre>
 */
@Component
public class PriceCalculator {

    private static final long BPS_DENOMINATOR = 10_000L;

    public record Leg(String offerId, Operator operator, long unitPriceMinor) {
    }

    public record PricedLine(String offerId, Operator operator, long unitPriceMinor, int quantity,
                             long amountMinor) {
    }

    public record Breakdown(List<PricedLine> lines, long subtotalMinor, int discountBps,
                            long discountMinor, long totalMinor) {
    }

    private final PricingProperties properties;

    public PriceCalculator(PricingProperties properties) {
        this.properties = properties;
    }

    public Breakdown calculate(List<Leg> legs, int travelers) {
        List<PricedLine> lines = legs.stream().map(leg -> price(leg, travelers)).toList();

        long subtotal = 0;
        for (PricedLine line : lines) {
            subtotal = Math.addExact(subtotal, line.amountMinor());
        }

        int discountBps = discountBpsFor((int) legs.stream().map(Leg::operator).distinct().count());
        long discount = roundHalfUp(subtotal, discountBps);
        return new Breakdown(lines, subtotal, discountBps, discount, subtotal - discount);
    }

    private PricedLine price(Leg leg, int travelers) {
        int quantity = leg.operator().isPricedPerTraveler() ? travelers : 1;
        return new PricedLine(leg.offerId(), leg.operator(), leg.unitPriceMinor(), quantity,
                Math.multiplyExact(leg.unitPriceMinor(), (long) quantity));
    }

    private int discountBpsFor(int distinctOperators) {
        if (distinctOperators >= 3) {
            return properties.threeOperatorsBps();
        }
        return distinctOperators == 2 ? properties.twoOperatorsBps() : 0;
    }

    /** amount x bps / 10000, a half centime rounds up. Integer arithmetic only. */
    private long roundHalfUp(long amountMinor, int bps) {
        return Math.addExact(Math.multiplyExact(amountMinor, (long) bps), BPS_DENOMINATOR / 2) / BPS_DENOMINATOR;
    }
}
