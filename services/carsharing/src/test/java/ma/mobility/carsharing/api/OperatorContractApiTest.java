package ma.mobility.carsharing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.mobility.carsharing.model.HoldStatus;
import ma.mobility.carsharing.service.HoldExpirationJob;
import ma.mobility.carsharing.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The 4 operations of contracts/operator-port.md, exercised over HTTP. */
@AutoConfigureMockMvc
@AutoConfigureTracing
class OperatorContractApiTest extends IntegrationTestBase {

    private static final String HOLD_BODY = "{\"offerId\":\"CAR-RAB-001\",\"seats\":1}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HoldExpirationJob expirationJob;

    // ---- search ----------------------------------------------------------------------------

    @Test
    void searchReturnsTheCanonicalOfferAsRawJson() throws Exception {
        String body = mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Paris").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Raw text, not a parsed object: a serializer dropping ":00" seconds must fail this test.
        assertThat(body).isEqualTo("[{\"offerId\":\"CAR-PAR-001\",\"operator\":\"CARSHARING\","
                + "\"origin\":\"Paris\",\"destination\":\"Paris\","
                + "\"departure\":\"2026-11-15T08:00:00\",\"arrival\":\"2026-11-15T20:00:00\","
                + "\"priceMinor\":30000,\"seatsAvailable\":1}]");
    }

    @Test
    void searchWithNoResultIsAnEmptyArrayNever404() throws Exception {
        mvc.perform(get("/v1/offers")
                        .param("origin", "a").param("destination", "Tokyo").param("date", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void searchWithMissingOrBadParametersIsA400WithTheCommonErrorBody() throws Exception {
        mvc.perform(get("/v1/offers").param("origin", "a").param("destination", "Rabat"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mvc.perform(get("/v1/offers")
                        .param("origin", "a").param("destination", "Rabat").param("date", "15/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // ---- hold ------------------------------------------------------------------------------

    @Test
    void holdReturns201WithAHoldId() throws Exception {
        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value(org.hamcrest.Matchers.startsWith("CARHOLD-")));
    }

    @Test
    void holdOnATakenOrUnknownOfferIs409NotAvailable() throws Exception {
        hold(HOLD_BODY, null);

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":\"CAR-NOPE-1\",\"seats\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"));
    }

    @Test
    void holdWithInvalidBodyIs400() throws Exception {
        for (String bad : List.of("{\"offerId\":\"\",\"seats\":1}", "{\"offerId\":\"CAR-RAB-001\",\"seats\":0}",
                "{\"seats\":1}", "not json", "")) {
            mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
        assertThat(holds.count()).isZero();
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameHoldId() throws Exception {
        String first = hold(HOLD_BODY, "6f1c2a52-0000-4000-8000-000000000001");
        String second = hold(HOLD_BODY, "6f1c2a52-0000-4000-8000-000000000001");

        assertThat(second).isEqualTo(first);
        assertThat(holds.count()).isEqualTo(1);
    }

    // ---- confirm ---------------------------------------------------------------------------

    @Test
    void confirmReturns200WithABookingRefAndIsReplayable() throws Exception {
        String holdId = hold(HOLD_BODY, null);

        String ref1 = confirm(holdId);
        String ref2 = confirm(holdId);

        assertThat(ref1).startsWith("CARBK-").isEqualTo(ref2);
    }

    @Test
    void confirmOfAnUnknownOrExpiredHoldIs409() throws Exception {
        mvc.perform(post("/v1/holds/CARHOLD-unknown/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"));

        String holdId = hold(HOLD_BODY, null);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        mvc.perform(post("/v1/holds/" + holdId + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_AVAILABLE"));
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    // ---- cancel ----------------------------------------------------------------------------

    @Test
    void cancelAlwaysReturns204WhateverTheReference() throws Exception {
        String holdId = hold(HOLD_BODY, null);
        String ref = confirm(holdId);

        for (String reference : List.of(ref, ref, holdId, "never-existed", "CARBK-0000")) {
            mvc.perform(delete("/v1/references/" + reference)).andExpect(status().isNoContent());
        }
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    // ---- other HTTP errors keep the common body instead of a generic 500 -----------------------

    @Test
    void unknownPathIs404WrongMethodIs405WrongContentTypeIs415() throws Exception {
        mvc.perform(get("/v1/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(get("/v1/holds"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/v1/holds").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- saga scenario: compensation -------------------------------------------------------

    @Test
    void compensationFreesTheCarSoAnotherTravelerCanBookIt() throws Exception {
        String holdId = hold(HOLD_BODY, null);
        confirm(holdId);

        mvc.perform(delete("/v1/references/" + holdId)).andExpect(status().isNoContent());

        mvc.perform(get("/v1/offers")
                        .param("origin", "Casablanca").param("destination", "Rabat").param("date", "2026-11-15"))
                .andExpect(jsonPath("$.length()").value(2));
        hold(HOLD_BODY, null);
    }

    // ---- observability ---------------------------------------------------------------------

    @Test
    void incomingTraceparentIsContinuedAndReachesTheEventPayload() throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String traceparent = "00-" + traceId + "-00f067aa0ba902b7-01";

        String holdId = json.readTree(mvc.perform(post("/v1/holds").header("traceparent", traceparent)
                        .contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                .andReturn().getResponse().getContentAsString()).get("holdId").asText();
        mvc.perform(post("/v1/holds/" + holdId + "/confirm").header("traceparent", traceparent))
                .andExpect(status().isOk());

        // The trace id sent by the caller (the saga) comes out in the published event.
        var message = org.mockito.ArgumentCaptor.forClass(org.springframework.amqp.core.Message.class);
        org.mockito.Mockito.verify(rabbit).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), message.capture());
        assertThat(new String(message.getValue().getBody())).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void aRequestWithoutTraceparentStillGetsATraceId() throws Exception {
        String holdId = json.readTree(mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content(HOLD_BODY)).andReturn().getResponse().getContentAsString()).get("holdId").asText();
        mvc.perform(post("/v1/holds/" + holdId + "/confirm")).andExpect(status().isOk());

        var message = org.mockito.ArgumentCaptor.forClass(org.springframework.amqp.core.Message.class);
        org.mockito.Mockito.verify(rabbit).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), message.capture());
        assertThat(new String(message.getValue().getBody())).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
    }

    // ---- concurrency -----------------------------------------------------------------------

    @Test
    void tenSimultaneousHoldsOnOneCarGiveExactlyOneWinner() throws Exception {
        int callers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(HOLD_BODY))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdownNow();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(callers - 1);
        assertThat(holds.count()).isEqualTo(1);
    }

    @Test
    void simultaneousConfirmsOfOneHoldAllReceiveTheSameBookingRef() throws Exception {
        String holdId = hold(HOLD_BODY, null);
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/v1/holds/" + holdId + "/confirm"))
                        .andReturn().getResponse().getContentAsString();
            }));
        }
        start.countDown();

        List<String> bodies = new ArrayList<>();
        for (Future<String> f : results) {
            bodies.add(f.get());
        }
        pool.shutdownNow();

        assertThat(bodies).hasSize(callers).doesNotContain("").containsOnly(bodies.get(0));
        assertThat(bodies.get(0)).contains("CARBK-");
        assertThat(holds.findAll()).hasSize(1).allMatch(h -> h.getStatus() == HoldStatus.CONFIRMED);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private String hold(String body, String idempotencyKey) throws Exception {
        var request = post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        String response = mvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("holdId").asText();
    }

    private String confirm(String holdId) throws Exception {
        String response = mvc.perform(post("/v1/holds/" + holdId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Debug"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("bookingRef").asText();
    }
}
