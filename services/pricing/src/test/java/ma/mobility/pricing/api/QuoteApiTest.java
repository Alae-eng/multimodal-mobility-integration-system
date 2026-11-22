package ma.mobility.pricing.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import ma.mobility.pricing.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@AutoConfigureTracing
class QuoteApiTest extends IntegrationTestBase {

    /** Real canonical offers, with their extra fields: pricing must accept them as they are. */
    private static final String TRIP = """
            {"travelers":1,"offers":[
              {"offerId":"RAIL-CAS-RAB-0730","operator":"RAIL","origin":"Casablanca","destination":"Rabat",
               "departure":"2026-11-15T07:30:00","arrival":"2026-11-15T08:25:00","priceMinor":8000,"seatsAvailable":120},
              {"offerId":"AIR-RBA-ORY-1200","operator":"AIR","origin":"Rabat","destination":"Paris",
               "departure":"2026-11-15T12:00:00","arrival":"2026-11-15T15:10:00","priceMinor":180000,"seatsAvailable":40},
              {"offerId":"CAR-PAR-001","operator":"CARSHARING","origin":"Paris","destination":"Paris",
               "departure":"2026-11-15T08:00:00","arrival":"2026-11-15T20:00:00","priceMinor":30000,"seatsAvailable":1}
            ]}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions postQuote(String body, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    // ---- nominal ----------------------------------------------------------------------------

    @Test
    void theDemoTripIsPricedFromTheCanonicalOffers() throws Exception {
        postQuote(TRIP, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(org.hamcrest.Matchers.startsWith("QUOTE-")))
                .andExpect(jsonPath("$.currency").value("MAD"))
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.subtotalMinor").value(218000))
                .andExpect(jsonPath("$.discountBps").value(1000))
                .andExpect(jsonPath("$.discountMinor").value(21800))
                .andExpect(jsonPath("$.totalMinor").value(196200));
    }

    @Test
    void amountsAreSerializedAsPlainIntegers() throws Exception {
        String body = postQuote(TRIP, null).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"totalMinor\":196200").contains("\"subtotalMinor\":218000")
                .doesNotContain("196200.0").doesNotContain("\"196200\"");
    }

    @Test
    void aQuoteCanBeFetchedAgain() throws Exception {
        String quoteId = json.readTree(postQuote(TRIP, null).andReturn().getResponse().getContentAsString())
                .get("quoteId").asText();

        mvc.perform(get("/v1/quotes/" + quoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinor").value(196200))
                .andExpect(jsonPath("$.lines[1].offerId").value("AIR-RBA-ORY-1200"));
    }

    // ---- idempotency -----------------------------------------------------------------------

    @Test
    void sameKeySameRequestReturnsTheSameQuote() throws Exception {
        String first = quoteIdOf(postQuote(TRIP, "key-1"));
        String second = quoteIdOf(postQuote(TRIP, "key-1"));

        assertThat(second).isEqualTo(first);
        assertThat(quotes.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithTheOffersInAnotherOrderIsStillTheSameRequest() throws Exception {
        String reordered = """
                {"travelers":1,"offers":[
                  {"offerId":"CAR-PAR-001","operator":"CARSHARING","priceMinor":30000},
                  {"offerId":"AIR-RBA-ORY-1200","operator":"AIR","priceMinor":180000},
                  {"offerId":"RAIL-CAS-RAB-0730","operator":"RAIL","priceMinor":8000}]}""";

        String first = quoteIdOf(postQuote(TRIP, "key-2"));

        assertThat(quoteIdOf(postQuote(reordered, "key-2"))).isEqualTo(first);
    }

    @Test
    void sameKeyWithADifferentRequestIsRefusedNotAnsweredWithTheOldPrice() throws Exception {
        postQuote(TRIP, "key-3").andExpect(status().isCreated());

        postQuote(TRIP.replace("\"travelers\":1", "\"travelers\":2"), "key-3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(quotes.count()).isEqualTo(1);
    }

    @Test
    void tenSimultaneousRequestsWithTheSameKeyCreateExactlyOneQuote() throws Exception {
        int callers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return postQuote(TRIP, "same-key").andReturn().getResponse().getContentAsString();
            }));
        }
        start.countDown();
        List<String> bodies = new ArrayList<>();
        for (Future<String> f : results) {
            bodies.add(f.get());
        }
        pool.shutdownNow();

        assertThat(quotes.count()).isEqualTo(1);
        assertThat(bodies).allSatisfy(b -> assertThat(b).contains("\"totalMinor\":196200"));
        assertThat(bodies.stream().map(this::quoteIdIn).distinct()).hasSize(1);
    }

    // ---- validation: every refusal is a 400 with the common error body ---------------------

    @Test
    void invalidRequestsAreRefusedWithBadRequest() throws Exception {
        List<String> bad = List.of(
                "{\"travelers\":1,\"offers\":[]}",
                "{\"travelers\":1}",
                "{\"travelers\":0,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":1}]}",
                "{\"travelers\":21,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":1}]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"\",\"operator\":\"RAIL\",\"priceMinor\":1}]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"BUS\",\"priceMinor\":1}]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"rail\",\"priceMinor\":1}]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":-1}]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\"}]}",
                "{\"travelers\":1,\"offers\":[null]}",
                "{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":1},"
                        + "{\"offerId\":\"A\",\"operator\":\"AIR\",\"priceMinor\":2}]}",
                "not json",
                "");
        for (String body : bad) {
            postQuote(body, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertThat(quotes.count()).isZero();
    }

    @Test
    void aDecimalPriceIsRefusedNeverTruncated() throws Exception {
        postQuote("{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":80.5}]}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        postQuote("{\"travelers\":1,\"offers\":[{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":80.0}]}", null)
                .andExpect(status().isBadRequest());
        assertThat(quotes.count()).isZero();
    }

    @Test
    void aTooLargePriceIsRefusedNotWrappedAround() throws Exception {
        postQuote("{\"travelers\":20,\"offers\":["
                + "{\"offerId\":\"A\",\"operator\":\"RAIL\",\"priceMinor\":9223372036854775807},"
                + "{\"offerId\":\"B\",\"operator\":\"AIR\",\"priceMinor\":1}]}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // ---- other HTTP errors keep the common error body (not a generic 500) ------------------

    @Test
    void unknownQuoteIs404() throws Exception {
        mvc.perform(get("/v1/quotes/QUOTE-nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void unknownPathIs404WrongMethodIs405WrongContentTypeIs415() throws Exception {
        mvc.perform(get("/v1/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(get("/v1/quotes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/v1/quotes").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- observability -----------------------------------------------------------------------

    @Test
    void aRequestWithATraceparentIsServedNormally() throws Exception {
        mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(TRIP)
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isCreated());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private String quoteIdOf(ResultActions result) throws Exception {
        return quoteIdIn(result.andReturn().getResponse().getContentAsString());
    }

    private String quoteIdIn(String body) {
        return json.readTree(body).get("quoteId").asText();
    }
}
