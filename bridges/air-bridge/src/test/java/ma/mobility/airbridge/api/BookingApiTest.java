package ma.mobility.airbridge.api;

import ma.mobility.airbridge.idempotency.IdempotencyStore;
import ma.mobility.airbridge.soap.AirClient;
import ma.mobility.airbridge.soap.OperatorRefusedException;
import ma.mobility.airbridge.soap.UpstreamFailureException;
import ma.mobility.airbridge.ws.Flight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ws.client.WebServiceIOException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat REST de l'air-bridge (contracts/operator-port.md), partenaire SOAP simulé. */
@WebMvcTest({OfferController.class, BookingController.class})
@Import(IdempotencyStore.class)
class BookingApiTest {

    private static final String HOLD_BODY = "{\"offerId\":\"AIR-RBA-ORY-1200\",\"seats\":2}";

    @Autowired
    MockMvc mvc;

    @MockBean
    AirClient air;

    private static Flight flight() {
        Flight f = new Flight();
        f.setFlightId("AIR-RBA-ORY-1200");
        f.setOrigin("Rabat");
        f.setDestination("Paris");
        f.setDeparture("2026-11-15T12:00:00");
        f.setArrival("2026-11-15T15:30:00");
        f.setPriceMinor(180000);
        f.setSeatsAvailable(40);
        return f;
    }

    // ---------- search ----------

    @Test
    void search_returnsCanonicalOffers() throws Exception {
        when(air.searchFlights("Rabat", "Paris", "2026-11-15")).thenReturn(List.of(flight()));

        mvc.perform(get("/v1/offers")
                        .param("origin", "Rabat").param("destination", "Paris").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerId").value("AIR-RBA-ORY-1200"))
                .andExpect(jsonPath("$[0].operator").value("AIR"))
                .andExpect(jsonPath("$[0].priceMinor").value(180000))
                .andExpect(jsonPath("$[0].seatsAvailable").value(40));
    }

    @Test
    void search_withNoResult_returnsEmptyListNot404() throws Exception {
        when(air.searchFlights(anyString(), anyString(), anyString())).thenReturn(List.of());

        mvc.perform(get("/v1/offers")
                        .param("origin", "Rabat").param("destination", "Tokyo").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void search_withInvalidDate_returns400() throws Exception {
        mvc.perform(get("/v1/offers")
                        .param("origin", "Rabat").param("destination", "Paris").param("date", "15/11/2026"))
                .andExpect(status().isBadRequest());
    }

    // ---------- hold ----------

    @Test
    void hold_returns201WithHoldId() throws Exception {
        when(air.holdFlight("AIR-RBA-ORY-1200", 2)).thenReturn("HOLD-AIR-1");

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value("HOLD-AIR-1"));
    }

    @Test
    void hold_refusedByPartner_returns409NotAvailable() throws Exception {
        when(air.holdFlight("AIR-CMN-MAD-1800", 4))
                .thenThrow(new OperatorRefusedException("Places insuffisantes"));

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":\"AIR-CMN-MAD-1800\",\"seats\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("Places insuffisantes"));
    }

    @Test
    void hold_withoutOfferId_returns400() throws Exception {
        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content("{\"seats\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void hold_sameIdempotencyKeyTwice_callsPartnerOnceAndReturnsSameHoldId() throws Exception {
        when(air.holdFlight("AIR-RBA-ORY-1200", 2)).thenReturn("HOLD-AIR-1", "HOLD-AIR-2");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "saga-42-air").content(HOLD_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.holdId").value("HOLD-AIR-1"));
        }
        verify(air, times(1)).holdFlight("AIR-RBA-ORY-1200", 2);
    }

    @Test
    void hold_withoutIdempotencyKey_callsPartnerEachTime() throws Exception {
        when(air.holdFlight("AIR-RBA-ORY-1200", 2)).thenReturn("HOLD-AIR-1", "HOLD-AIR-2");

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(jsonPath("$.holdId").value("HOLD-AIR-1"));
        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(jsonPath("$.holdId").value("HOLD-AIR-2"));

        verify(air, times(2)).holdFlight("AIR-RBA-ORY-1200", 2);
    }

    // ---------- confirm ----------

    @Test
    void confirm_returns200WithBookingRef() throws Exception {
        when(air.confirmFlight("HOLD-AIR-1")).thenReturn("BK-AIR-1");

        mvc.perform(post("/v1/holds/HOLD-AIR-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value("BK-AIR-1"));
    }

    @Test
    void confirm_expiredOrUnknownHold_returns409() throws Exception {
        when(air.confirmFlight("HOLD-OLD")).thenThrow(new OperatorRefusedException("Maintien inconnu ou expiré"));

        mvc.perform(post("/v1/holds/HOLD-OLD/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"));
    }

    // ---------- cancel ----------

    @Test
    void cancel_returns204() throws Exception {
        mvc.perform(delete("/v1/references/BK-AIR-1")).andExpect(status().isNoContent());
        verify(air).cancelFlight("BK-AIR-1");
    }

    @Test
    void cancel_whenPartnerFailsTechnically_returns503NotFalse204() throws Exception {
        doThrow(new UpstreamFailureException("fault inattendu", new RuntimeException()))
                .when(air).cancelFlight("BK-AIR-1");

        mvc.perform(delete("/v1/references/BK-AIR-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }

    // ---------- erreurs techniques ----------

    @Test
    void partnerUnreachable_returns503UpstreamUnavailable() throws Exception {
        when(air.holdFlight(anyString(), anyInt())).thenThrow(new WebServiceIOException("I/O error"));

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }
}