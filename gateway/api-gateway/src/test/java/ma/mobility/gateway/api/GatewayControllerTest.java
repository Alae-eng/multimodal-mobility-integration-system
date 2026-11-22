package ma.mobility.gateway.api;

import ma.mobility.gateway.routing.UpstreamGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatewayControllerTest {
    private MockMvc mockMvc;
    private RecordingGateway upstream;

    @BeforeEach
    void setUp() {
        upstream = new RecordingGateway();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GatewayController(upstream))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void routesSearchToSagaWithEncodedParameters() throws Exception {
        upstream.sagaResponse = ResponseEntity.ok("[{\"offerId\":\"RAIL-1\"}]");

        mockMvc.perform(get("/api/v1/search")
                        .param("origin", "Casablanca")
                        .param("destination", "Rabat")
                        .param("date", "2026-11-15")
                        .header("traceparent", "trace-1")
                        .header("Idempotency-Key", "search-key"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"offerId\":\"RAIL-1\"}]"));

        assertEquals(
                "/api/v1/search?origin=Casablanca&destination=Rabat&date=2026-11-15",
                upstream.lastSagaPath);
        assertEquals("trace-1", upstream.lastTraceparent);
        assertEquals("search-key", upstream.lastIdempotencyKey);
    }

    @Test
    void routesBookingToSagaAndPreservesHeaders() throws Exception {
        upstream.sagaResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body("{\"success\":true}");

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerIds\":[\"RAIL-1\"],\"seats\":1}")
                        .header("traceparent", "trace-2")
                        .header("Idempotency-Key", "booking-key"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"success\":true}"));

        assertEquals("{\"offerIds\":[\"RAIL-1\"],\"seats\":1}",
                upstream.lastSagaBody);
        assertEquals("trace-2", upstream.lastTraceparent);
        assertEquals("booking-key", upstream.lastIdempotencyKey);
    }

    private static class RecordingGateway extends UpstreamGateway {
        private ResponseEntity<String> sagaResponse = ResponseEntity.ok("{}");
        private String lastSagaPath;
        private String lastSagaBody;
        private String lastTraceparent;
        private String lastIdempotencyKey;

        RecordingGateway() {
            super(WebClient.builder(),
                    "http://saga",
                    "http://pricing",
                    "http://bookings",
                    "http://notifications");
        }

        @Override
        public ResponseEntity<String> getSaga(String path,
                                              String traceparent,
                                              String idempotencyKey) {
            lastSagaPath = path;
            lastTraceparent = traceparent;
            lastIdempotencyKey = idempotencyKey;
            return sagaResponse;
        }

        @Override
        public ResponseEntity<String> postSaga(String path,
                                               String body,
                                               String traceparent,
                                               String idempotencyKey) {
            lastSagaPath = path;
            lastSagaBody = body;
            lastTraceparent = traceparent;
            lastIdempotencyKey = idempotencyKey;
            return sagaResponse;
        }
    }
}
