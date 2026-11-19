package ma.mobility.railbridge.api;

import ma.mobility.railbridge.corba.OperatorRefusedException;
import ma.mobility.railbridge.corba.RailClient;
import ma.mobility.railbridge.corba.RailUnavailableException;
import ma.mobility.railbridge.idempotency.IdempotencyStore;
import mobility.rail.Trip;
import org.junit.jupiter.api.Test;
import org.omg.CORBA.COMM_FAILURE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

/** Contrat REST du rail-bridge (contracts/operator-port.md), opérateur CORBA simulé. */
@WebMvcTest({OfferController.class, BookingController.class})
@Import(IdempotencyStore.class)
class BookingApiTest {

    private static final String HOLD_BODY = "{\"offerId\":\"RAIL-CAS-RAB-0730\",\"seats\":2}";

    @Autowired
    MockMvc mvc;

    @MockBean
    RailClient rail;

    private static Trip trip() {
        Trip t = new Trip();
        t.tripId = "RAIL-CAS-RAB-0730";
        t.origin = "Casablanca";
        t.destination = "Rabat";
        t.departure = "2026-11-15T07:30:00";
        t.arrival = "2026-11-15T08:25:00";
        t.priceMinor = 8000;
        t.seatsAvailable = 120;
        return t;
    }

    // ---------- search ----------

    @Test
    void search_returnsCanonicalOffers() throws Exception {
        when(rail.search("Casablanca", "Rabat", "2026-11-15")).thenReturn(List.of(trip()));

        mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Rabat").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerId").value("RAIL-CAS-RAB-0730"))
                .andExpect(jsonPath("$[0].operator").value("RAIL"))
                .andExpect(jsonPath("$[0].priceMinor").value(8000))
                .andExpect(jsonPath("$[0].seatsAvailable").value(120));
    }

    @Test
    void search_withNoResult_returnsEmptyListNot404() throws Exception {
        when(rail.search(anyString(), anyString(), anyString())).thenReturn(List.of());

        mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Tokyo").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void search_withInvalidDate_returns400() throws Exception {
        mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Rabat").param("date", "15/11/2026"))
                .andExpect(status().isBadRequest());
    }

    // ---------- hold ----------

    @Test
    void hold_returns201WithHoldId() throws Exception {
        when(rail.hold("RAIL-CAS-RAB-0730", 2)).thenReturn("HOLD-RAIL-1");

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value("HOLD-RAIL-1"));
    }

    @Test
    void hold_refusedByOperator_returns409NotAvailable() throws Exception {
        when(rail.hold("RAIL-CAS-RAB-0730", 9999))
                .thenThrow(new OperatorRefusedException("Places insuffisantes"));

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":\"RAIL-CAS-RAB-0730\",\"seats\":9999}"))
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
    void hold_sameIdempotencyKeyTwice_callsOperatorOnceAndReturnsSameHoldId() throws Exception {
        when(rail.hold("RAIL-CAS-RAB-0730", 2)).thenReturn("HOLD-RAIL-1", "HOLD-RAIL-2");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "saga-42-rail").content(HOLD_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.holdId").value("HOLD-RAIL-1"));
        }
        verify(rail, times(1)).hold("RAIL-CAS-RAB-0730", 2);
    }

    @Test
    void hold_withoutIdempotencyKey_callsOperatorEachTime() throws Exception {
        when(rail.hold("RAIL-CAS-RAB-0730", 2)).thenReturn("HOLD-RAIL-1", "HOLD-RAIL-2");

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(jsonPath("$.holdId").value("HOLD-RAIL-1"));
        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(jsonPath("$.holdId").value("HOLD-RAIL-2"));

        verify(rail, times(2)).hold("RAIL-CAS-RAB-0730", 2);
    }

    // ---------- confirm ----------

    @Test
    void confirm_returns200WithBookingRef() throws Exception {
        when(rail.confirm("HOLD-RAIL-1")).thenReturn("BK-RAIL-1");

        mvc.perform(post("/v1/holds/HOLD-RAIL-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value("BK-RAIL-1"));
    }

    @Test
    void confirm_expiredOrUnknownHold_returns409() throws Exception {
        when(rail.confirm("HOLD-OLD")).thenThrow(new OperatorRefusedException("Maintien inconnu ou expiré"));

        mvc.perform(post("/v1/holds/HOLD-OLD/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"));
    }

    // ---------- cancel ----------

    @Test
    void cancel_returns204() throws Exception {
        mvc.perform(delete("/v1/references/BK-RAIL-1")).andExpect(status().isNoContent());
        verify(rail).cancel("BK-RAIL-1");
    }

    @Test
    void cancel_whenOperatorUnreachable_returns503NotFalse204() throws Exception {
        doThrow(new COMM_FAILURE("serveur injoignable")).when(rail).cancel("BK-RAIL-1");

        mvc.perform(delete("/v1/references/BK-RAIL-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }

    // ---------- erreurs techniques ----------

    @Test
    void corbaSystemException_returns503UpstreamUnavailable() throws Exception {
        when(rail.hold(anyString(), anyInt())).thenThrow(new COMM_FAILURE("serveur injoignable"));

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void unreadableIor_returns503UpstreamUnavailable() throws Exception {
        when(rail.search(anyString(), anyString(), anyString()))
                .thenThrow(new RailUnavailableException("IOR illisible", new RuntimeException()));

        mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Rabat").param("date", "2026-11-15"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }
}