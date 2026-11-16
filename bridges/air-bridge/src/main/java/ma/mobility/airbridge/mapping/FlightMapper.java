package ma.mobility.airbridge.mapping;

import ma.mobility.airbridge.api.Offer;
import ma.mobility.airbridge.ws.Flight;

/** Couche anti-corruption : Flight (SOAP/JAXB) -> Offer canonique. */
public final class FlightMapper {

    public static final String OPERATOR = "AIR";

    public static Offer toOffer(Flight f) {
        return new Offer(
                f.getFlightId(),
                OPERATOR,
                f.getOrigin(),
                f.getDestination(),
                f.getDeparture(),
                f.getArrival(),
                f.getPriceMinor(),
                f.getSeatsAvailable());
    }

    private FlightMapper() {}
}