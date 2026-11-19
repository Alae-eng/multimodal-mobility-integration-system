package ma.mobility.railbridge.mapping;

import ma.mobility.railbridge.api.Offer;
import mobility.rail.Trip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripMapperTest {

    @Test
    void mapsCorbaTripToCanonicalOffer() {
        Trip t = new Trip();
        t.tripId = "RAIL-CAS-RAB-0730";
        t.origin = "Casablanca";
        t.destination = "Rabat";
        t.departure = "2026-11-15T07:30:00";
        t.arrival = "2026-11-15T08:25:00";
        t.priceMinor = 8000;
        t.seatsAvailable = 120;

        Offer o = TripMapper.toOffer(t);

        assertEquals("RAIL-CAS-RAB-0730", o.offerId(), "l'id legacy est repris tel quel");
        assertEquals("RAIL", o.operator());
        assertEquals("Casablanca", o.origin());
        assertEquals("Rabat", o.destination());
        assertEquals("2026-11-15T07:30:00", o.departure());
        assertEquals("2026-11-15T08:25:00", o.arrival());
        assertEquals(8000L, o.priceMinor(), "prix en centimes, jamais de décimales");
        assertEquals(120, o.seatsAvailable());
    }
}