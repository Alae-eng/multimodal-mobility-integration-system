package ma.mobility.airbridge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import ma.mobility.airbridge.api.HoldRequest;
import ma.mobility.airbridge.api.Offer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests contre le VRAI partenaire aérien du starter kit (non modifié).
 * Ignorés automatiquement si le kit ne tourne pas ou n'est pas joignable.
 * À lancer sur le réseau Docker du kit (voir README).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.enabled=false")
class KitIntegrationTest {

    private static final String FLIGHT = "AIR-RBA-ORY-1200";
    private static final String SEARCH = "/v1/offers?origin=Rabat&destination=Paris&date=2026-11-15";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Value("${air.url}")
    String airUrl;

    @BeforeEach
    void requireKit() {
        assumeTrue(kitReachable(), "Kit non joignable (" + airUrl + ") : test d'intégration ignoré");
    }

    /** Jusqu'à 3 essais de 5 s : un partenaire lent ne doit pas faire ignorer le test à tort. */
    private boolean kitReachable() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<Void> r = client.send(
                        HttpRequest.newBuilder(URI.create(airUrl + "/air.wsdl")).timeout(Duration.ofSeconds(5)).build(),
                        HttpResponse.BodyHandlers.discarding());
                if (r.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                // on réessaie
            }
        }
        return false;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<JsonNode> hold(String offerId, int seats, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(url("/v1/holds"), HttpMethod.POST,
                new HttpEntity<>(new HoldRequest(offerId, seats), headers), JsonNode.class);
    }

    private ResponseEntity<Void> cancel(String reference) {
        return rest.exchange(url("/v1/references/" + reference), HttpMethod.DELETE, null, Void.class);
    }

    private int seats() {
        Offer[] offers = rest.getForObject(url(SEARCH), Offer[].class);
        assertNotNull(offers);
        return Arrays.stream(offers).filter(o -> o.offerId().equals(FLIGHT))
                .findFirst().orElseThrow().seatsAvailable();
    }

    @Test
    void search_returnsTheRealFlightAsCanonicalOffer() {
        Offer[] offers = rest.getForObject(url(SEARCH), Offer[].class);

        assertNotNull(offers);
        Offer o = Arrays.stream(offers).filter(x -> x.offerId().equals(FLIGHT)).findFirst().orElseThrow();
        assertEquals("AIR", o.operator());
        assertEquals("Rabat", o.origin());
        assertEquals("Paris", o.destination());
        assertTrue(o.priceMinor() > 0);
    }

    @Test
    void search_unknownRoute_returnsEmptyList() {
        Offer[] offers = rest.getForObject(
                url("/v1/offers?origin=Rabat&destination=Tokyo&date=2026-11-15"), Offer[].class);
        assertNotNull(offers);
        assertEquals(0, offers.length);
    }

    @Test
    void fullCycle_hold_confirm_cancel_thenCancelReplayed() {
        int before = seats();

        ResponseEntity<JsonNode> held = hold(FLIGHT, 2, null);
        assertEquals(HttpStatus.CREATED, held.getStatusCode());
        String holdId = held.getBody().get("holdId").asText();
        assertEquals(before - 2, seats(), "le hold retire les places");

        ResponseEntity<JsonNode> confirmed = rest.postForEntity(
                url("/v1/holds/" + holdId + "/confirm"), null, JsonNode.class);
        assertEquals(HttpStatus.OK, confirmed.getStatusCode());
        String bookingRef = confirmed.getBody().get("bookingRef").asText();

        assertEquals(HttpStatus.NO_CONTENT, cancel(bookingRef).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, cancel(bookingRef).getStatusCode(), "annulation rejouée : toujours 204");
        assertEquals(before, seats(), "l'annulation restitue les places");
    }

    @Test
    void forcedFailure_tooManySeats_returns409NotAvailable() {
        ResponseEntity<JsonNode> r = hold("AIR-CMN-MAD-1800", 4, null);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("NOT_AVAILABLE", r.getBody().get("error").asText());
    }

    @Test
    void confirmUnknownHold_returns409() {
        ResponseEntity<JsonNode> r = rest.postForEntity(
                url("/v1/holds/HOLD-AIR-INEXISTANT/confirm"), null, JsonNode.class);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("NOT_AVAILABLE", r.getBody().get("error").asText());
    }

    @Test
    void cancelUnknownReference_returns204() {
        assertEquals(HttpStatus.NO_CONTENT, cancel("REF-INEXISTANTE").getStatusCode());
    }

    @Test
    void idempotencyKey_sameHoldIdAndSeatsReservedOnlyOnce() {
        int before = seats();
        String key = UUID.randomUUID().toString();

        String first = hold(FLIGHT, 2, key).getBody().get("holdId").asText();
        String second = hold(FLIGHT, 2, key).getBody().get("holdId").asText();

        assertEquals(first, second);
        assertEquals(before - 2, seats(), "un seul hold, pas deux");

        cancel(first);
        assertEquals(before, seats());
    }
}