package ma.mobility.airbridge.mapping;

import ma.mobility.airbridge.api.Offer;
import ma.mobility.airbridge.ws.Flight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightMapperTest {

    @Test
    void mapsSoapFlightToCanonicalOffer() {
        Flight f = new Flight();
        f.setFlightId("AIR-RBA-ORY-1200");
        f.setOrigin("Rabat");
        f.setDestination("Paris");
        f.setDeparture("2026-11-15T12:00:00");
        f.setArrival("2026-11-15T15:30:00");
        f.setPriceMinor(180000);
        f.setSeatsAvailable(40);

        Offer o = FlightMapper.toOffer(f);

        assertEquals("AIR-RBA-ORY-1200", o.offerId(), "l'id legacy est repris tel quel");
        assertEquals("AIR", o.operator());
        assertEquals("Rabat", o.origin());
        assertEquals("Paris", o.destination());
        assertEquals("2026-11-15T12:00:00", o.departure());
        assertEquals("2026-11-15T15:30:00", o.arrival());
        assertEquals(180000L, o.priceMinor(), "prix en centimes, jamais de décimales");
        assertEquals(40, o.seatsAvailable());
    }
}