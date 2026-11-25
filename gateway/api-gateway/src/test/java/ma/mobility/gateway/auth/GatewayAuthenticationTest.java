package ma.mobility.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mobility.gateway.routing.UpstreamGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "gateway.auth.username=test-user",
        "gateway.auth.password=test-password",
        "gateway.auth.jwt-secret=test-jwt-signing-secret-that-is-long-enough"
})
@AutoConfigureMockMvc
@Import(GatewayAuthenticationTest.TestUpstreamConfiguration.class)
class GatewayAuthenticationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthIsPublicAndApiRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/booking-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentialsReturnJwtThatAuthorizesApiRequest() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = response.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/bookings/booking-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/auth")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestUpstreamConfiguration {
        @Bean
        @Primary
        UpstreamGateway testUpstreamGateway() {
            return new UpstreamGateway(WebClient.builder(),
                    "http://saga", "http://pricing", "http://bookings", "http://notifications") {
                @Override
                public ResponseEntity<String> getBookings(String path, String traceparent) {
                    return ResponseEntity.ok("{}");
                }
            };
        }
    }
}
