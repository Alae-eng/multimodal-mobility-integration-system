package ma.mobility.bookings.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.mobility.bookings.model.BookingStatus;
import ma.mobility.bookings.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class BookingApiTest extends IntegrationTestBase {

    private static final String CREATE = """
            {"travelerId":"traveler-1","totalMinor":196200,"quoteId":"QUOTE-1","legs":[
              {"operator":"RAIL","offerId":"RAIL-CAS-RAB-0730","reference":"RAILHOLD-1","priceMinor":8000},
              {"operator":"AIR","offerId":"AIR-RBA-ORY-1200","reference":"AIRHOLD-1","priceMinor":180000},
              {"operator":"CARSHARING","offerId":"CAR-PAR-001","reference":"CARHOLD-1","priceMinor":30000}]}""";

    private static final String CONFIRM = """
            {"references":[{"operator":"RAIL","bookingRef":"RAILBK-1"},
                           {"operator":"AIR","bookingRef":"AIRBK-1"},
                           {"operator":"CARSHARING","bookingRef":"CARBK-1"}]}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions create(String body, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private String createdId() throws Exception {
        return idOf(create(CREATE, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String idOf(String body) {
        return json.readTree(body).get("bookingId").asText();
    }

    private ResultActions confirm(String id) throws Exception {
        return mvc.perform(post("/v1/bookings/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content(CONFIRM));
    }

    // ---- nominal saga ----------------------------------------------------------------------

    @Test
    void theNominalFlowOpensConfirmsAndAnnouncesTheTrip() throws Exception {
        create(CREATE, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalMinor").value(196200))
                .andExpect(jsonPath("$.currency").value("MAD"))
                .andExpect(jsonPath("$.legs.length()").value(3));

        String id = bookings.findAll().get(0).getId();
        confirm(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.legs[?(@.operator=='AIR')].reference").value("AIRBK-1"));

        relay.relayPending();
        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed");
    }

    @Test
    void amountsAreSerializedAsPlainIntegers() throws Exception {
        String body = create(CREATE, null).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"totalMinor\":196200").doesNotContain("196200.0").doesNotContain("\"196200\"");
    }

    @Test
    void aFileCanBeReadAndListedByTraveler() throws Exception {
        String id = createdId();

        mvc.perform(get("/v1/bookings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(id));
        mvc.perform(get("/v1/bookings").param("travelerId", "traveler-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/v1/bookings").param("travelerId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- compensation ----------------------------------------------------------------------

    @Test
    void cancelAlwaysReturns204AndAConfirmedFileIsAnnouncedOnce() throws Exception {
        String id = createdId();
        confirm(id).andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            mvc.perform(delete("/v1/bookings/" + id)).andExpect(status().isNoContent());
        }
        mvc.perform(delete("/v1/bookings/BK-never-existed")).andExpect(status().isNoContent());

        mvc.perform(get("/v1/bookings/" + id)).andExpect(jsonPath("$.status").value("CANCELLED"));
        relay.relayPending();
        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed", "BookingCancelled");
    }

    @Test
    void aFailedSagaIsRecordedAndAnnouncedOnce() throws Exception {
        String id = createdId();
        String body = "{\"reason\":\"AIR-CMN-MAD-1800: not enough seats\"}";

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/bookings/" + id + "/fail").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.failureReason").value("AIR-CMN-MAD-1800: not enough seats"));
        }
        relay.relayPending();
        assertThat(sender.deliveredNames()).containsExactly("SagaFailed");
    }

    // ---- idempotency and concurrency ---------------------------------------------------------

    @Test
    void sameKeyReturnsTheSameFileAnotherTripIsRefused() throws Exception {
        String first = idOf(create(CREATE, "key-1").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String second = idOf(create(CREATE, "key-1").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(second).isEqualTo(first);
        create(CREATE.replace("traveler-1", "traveler-2"), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(bookings.count()).isEqualTo(1);
    }

    @Test
    void tenSimultaneousCreatesWithTheSameKeyMakeOneFile() throws Exception {
        List<String> bodies = simultaneously(10, () -> create(CREATE, "same-key").andReturn().getResponse().getContentAsString());

        assertThat(bookings.count()).isEqualTo(1);
        assertThat(bodies.stream().map(this::idOf).distinct()).hasSize(1);
    }

    @Test
    void simultaneousConfirmsGiveOneConfirmationAndOneEvent() throws Exception {
        String id = createdId();

        List<String> bodies = simultaneously(8, () -> {
            var response = confirm(id).andReturn().getResponse();
            return response.getStatus() + " " + response.getContentAsString();
        });

        // On PostgreSQL every caller gets 200 (checked live, 10 simultaneous confirms: 10 x 200):
        // the row lock makes the later callers wait, then read the already confirmed file.
        // The in-memory H2 used here hands a lock waiter its stale row, so a loser may see the
        // optimistic-lock conflict (409 CONCURRENT_UPDATE, "retry"). Both are correct answers;
        // what must never happen is any other outcome, or a duplicated transition or event.
        assertThat(bodies).hasSize(8).allSatisfy(b -> assertThat(b).matches(
                "(?s)(200 .*\"status\":\"CONFIRMED\".*|409 .*\"error\":\"CONCURRENT_UPDATE\".*)"));
        assertThat(bodies).anyMatch(b -> b.startsWith("200 "));
        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void aConfirmRacingAFailLeavesExactlyOneConsistentFinalStateAndOneEvent() throws Exception {
        String id = createdId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> confirming = pool.submit(() -> {
            start.await();
            return confirm(id).andReturn().getResponse().getStatus();
        });
        Future<Integer> failing = pool.submit(() -> {
            start.await();
            return mvc.perform(post("/v1/bookings/" + id + "/fail").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"race\"}")).andReturn().getResponse().getStatus();
        });
        start.countDown();
        int confirmStatus = confirming.get();
        int failStatus = failing.get();
        pool.shutdownNow();

        BookingStatus finalStatus = bookings.findById(id).orElseThrow().getStatus();
        assertThat(finalStatus).isIn(BookingStatus.CONFIRMED, BookingStatus.FAILED);
        assertThat(outbox.count()).isEqualTo(1);
        // The loser is told 409, never a success that contradicts the final state.
        if (finalStatus == BookingStatus.CONFIRMED) {
            assertThat(confirmStatus).isEqualTo(200);
            assertThat(failStatus).isEqualTo(409);
        } else {
            assertThat(failStatus).isEqualTo(200);
            assertThat(confirmStatus).isEqualTo(409);
        }
    }

    // ---- refusals --------------------------------------------------------------------------

    @Test
    void invalidRequestsAreRefusedWithBadRequest() throws Exception {
        List<String> bad = List.of(
                CREATE.replace("\"travelerId\":\"traveler-1\"", "\"travelerId\":\"\""),
                CREATE.replace("\"totalMinor\":196200,", ""),
                CREATE.replace("\"totalMinor\":196200", "\"totalMinor\":-5"),
                CREATE.replace("\"totalMinor\":196200", "\"totalMinor\":1962.5"),
                CREATE.replace("\"priceMinor\":8000", "\"priceMinor\":-1"),
                CREATE.replace("\"operator\":\"RAIL\"", "\"operator\":\"BUS\""),
                CREATE.replace("\"operator\":\"AIR\"", "\"operator\":\"RAIL\""),
                CREATE.replace("\"reference\":\"RAILHOLD-1\"", "\"reference\":\"\""),
                "{\"travelerId\":\"t\",\"totalMinor\":1,\"legs\":[]}",
                "{\"travelerId\":\"t\",\"totalMinor\":1}",
                "{\"travelerId\":\"t\",\"totalMinor\":1,\"legs\":[null]}",
                "not json",
                "");
        for (String body : bad) {
            create(body, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertThat(bookings.count()).isZero();
    }

    @Test
    void confirmWithAnIncompleteSetOfReferencesIs400AndKeepsTheFilePending() throws Exception {
        String id = createdId();

        mvc.perform(post("/v1/bookings/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"references\":[{\"operator\":\"RAIL\",\"bookingRef\":\"RAILBK-1\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void confirmingACancelledFileIs409IllegalState() throws Exception {
        String id = createdId();
        mvc.perform(delete("/v1/bookings/" + id)).andExpect(status().isNoContent());

        confirm(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ILLEGAL_STATE"));
    }

    @Test
    void unknownFileIs404() throws Exception {
        mvc.perform(get("/v1/bookings/BK-nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        confirm("BK-nope").andExpect(status().isNotFound());
    }

    @Test
    void unknownPathIs404WrongMethodIs405WrongContentTypeIs415MissingParameterIs400() throws Exception {
        mvc.perform(get("/v1/nothing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(delete("/v1/bookings")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/v1/bookings").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/v1/bookings")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private interface Call {
        String run() throws Exception;
    }

    private List<String> simultaneously(int callers, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return call.run();
            }));
        }
        start.countDown();
        List<String> bodies = new ArrayList<>();
        for (Future<String> f : results) {
            bodies.add(f.get());
        }
        pool.shutdownNow();
        return bodies;
    }
}
