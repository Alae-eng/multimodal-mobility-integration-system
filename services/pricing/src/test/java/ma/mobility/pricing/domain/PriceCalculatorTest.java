package ma.mobility.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ma.mobility.pricing.domain.PriceCalculator.Breakdown;
import ma.mobility.pricing.domain.PriceCalculator.Leg;
import org.junit.jupiter.api.Test;

/** The pricing rules alone: no Spring, no database. */
class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator(new PricingProperties(500, 1000));

    private static Leg rail(long price) { return new Leg("RAIL-1", Operator.RAIL, price); }
    private static Leg air(long price) { return new Leg("AIR-1", Operator.AIR, price); }
    private static Leg car(long price) { return new Leg("CAR-1", Operator.CARSHARING, price); }

    @Test
    void aSingleOperatorHasNoDiscount() {
        Breakdown b = calculator.calculate(List.of(rail(8000)), 1);

        assertThat(b.subtotalMinor()).isEqualTo(8000);
        assertThat(b.discountBps()).isZero();
        assertThat(b.discountMinor()).isZero();
        assertThat(b.totalMinor()).isEqualTo(8000);
    }

    @Test
    void theDemoTripTrainFlightAndCarGetsTheThreeOperatorDiscount() {
        Breakdown b = calculator.calculate(List.of(rail(8000), air(180000), car(15000)), 1);

        assertThat(b.subtotalMinor()).isEqualTo(203000);
        assertThat(b.discountBps()).isEqualTo(1000);
        assertThat(b.discountMinor()).isEqualTo(20300);
        assertThat(b.totalMinor()).isEqualTo(182700);
    }

    @Test
    void twoOperatorsGetTheSmallerDiscount() {
        Breakdown b = calculator.calculate(List.of(rail(8000), air(180000)), 1);

        assertThat(b.discountBps()).isEqualTo(500);
        assertThat(b.discountMinor()).isEqualTo(9400);
        assertThat(b.totalMinor()).isEqualTo(178600);
    }

    @Test
    void seatsAreMultipliedByTravelersButACarIsRentedOnce() {
        Breakdown b = calculator.calculate(List.of(rail(8000), air(180000), car(15000)), 3);

        assertThat(b.lines()).extracting(PriceCalculator.PricedLine::amountMinor)
                .containsExactly(24000L, 540000L, 15000L);
        assertThat(b.lines()).extracting(PriceCalculator.PricedLine::quantity).containsExactly(3, 3, 1);
        assertThat(b.subtotalMinor()).isEqualTo(579000);
    }

    @Test
    void aHalfCentimeDiscountRoundsUpAndStaysWhole() {
        // 5 % of 10 = 0.5 centime -> 1 ; 5 % of 30 = 1.5 -> 2 ; 5 % of 9 = 0.45 -> 0
        assertThat(calculator.calculate(List.of(rail(5), air(5)), 1).discountMinor()).isEqualTo(1);
        assertThat(calculator.calculate(List.of(rail(10), air(20)), 1).discountMinor()).isEqualTo(2);
        assertThat(calculator.calculate(List.of(rail(4), air(5)), 1).discountMinor()).isZero();
    }

    @Test
    void aFreeLegIsAllowed() {
        Breakdown b = calculator.calculate(List.of(rail(0), air(100)), 1);

        assertThat(b.subtotalMinor()).isEqualTo(100);
        assertThat(b.totalMinor()).isEqualTo(95);
    }

    @Test
    void discountCanBeSwitchedOff() {
        PriceCalculator noDiscount = new PriceCalculator(new PricingProperties(0, 0));

        assertThat(noDiscount.calculate(List.of(rail(8000), air(180000), car(15000)), 1).totalMinor())
                .isEqualTo(203000);
    }

    /** Invariants that must hold for any prices: checked on many random trips (seeded, so reproducible). */
    @Test
    void totalIsAlwaysSubtotalMinusDiscountAndNeverNegativeOrAboveSubtotal() {
        Random random = new Random(20261125L);
        for (int i = 0; i < 2000; i++) {
            List<Leg> legs = new ArrayList<>();
            Operator[] operators = Operator.values();
            int count = 1 + random.nextInt(3);
            for (int j = 0; j < count; j++) {
                legs.add(new Leg("OFFER-" + j, operators[j], random.nextInt(2_000_000)));
            }
            int travelers = 1 + random.nextInt(20);

            Breakdown b = calculator.calculate(legs, travelers);

            long expectedSubtotal = b.lines().stream().mapToLong(PriceCalculator.PricedLine::amountMinor).sum();
            assertThat(b.subtotalMinor()).as("seed iteration %d", i).isEqualTo(expectedSubtotal);
            assertThat(b.totalMinor()).isEqualTo(b.subtotalMinor() - b.discountMinor());
            assertThat(b.discountMinor()).isBetween(0L, b.subtotalMinor());
            assertThat(b.totalMinor()).isBetween(0L, b.subtotalMinor());
            // The discount is the exact rate, to within half a centime.
            assertThat(Math.abs(b.discountMinor() * 10_000L - b.subtotalMinor() * b.discountBps()))
                    .isLessThanOrEqualTo(5_000L);
        }
    }

    @Test
    void theOrderOfTheOffersDoesNotChangeTheTotal() {
        List<Leg> legs = List.of(rail(8000), air(180000), car(15000));
        List<Leg> reversed = List.of(car(15000), air(180000), rail(8000));

        assertThat(calculator.calculate(reversed, 2).totalMinor()).isEqualTo(calculator.calculate(legs, 2).totalMinor());
    }

    @Test
    void anOverflowIsRefusedNotWrappedAround() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> calculator.calculate(List.of(rail(Long.MAX_VALUE), air(1)), 20))
                .isInstanceOf(ArithmeticException.class);
    }
}
