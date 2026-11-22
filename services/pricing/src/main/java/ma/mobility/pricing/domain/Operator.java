package ma.mobility.pricing.domain;

/** The three operators of the platform (contracts/operator-port.md, field "operator"). */
public enum Operator {

    /** A train seat is sold per traveler. */
    RAIL(true),
    /** A flight seat is sold per traveler. */
    AIR(true),
    /** A car is rented as a whole: the price does not depend on the number of travelers. */
    CARSHARING(false);

    private final boolean pricedPerTraveler;

    Operator(boolean pricedPerTraveler) {
        this.pricedPerTraveler = pricedPerTraveler;
    }

    public boolean isPricedPerTraveler() {
        return pricedPerTraveler;
    }
}
