package ma.mobility.railbridge.mapping;

import ma.mobility.railbridge.api.Offer;
import mobility.rail.Trip;

/** Couche anti-corruption : Trip (CORBA) -> Offer canonique. */
public final class TripMapper {

    public static final String OPERATOR = "RAIL";

    public static Offer toOffer(Trip t) {
        return new Offer(
                t.tripId,
                OPERATOR,
                t.origin,
                t.destination,
                t.departure,
                t.arrival,
                t.priceMinor,
                t.seatsAvailable);
    }

    private TripMapper() {}
}