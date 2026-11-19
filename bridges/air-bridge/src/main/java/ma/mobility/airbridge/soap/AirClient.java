package ma.mobility.airbridge.soap;

import io.micrometer.observation.Observation;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.observation.ObservationRegistry;
import ma.mobility.airbridge.ws.CancelFlightRequest;
import ma.mobility.airbridge.ws.ConfirmFlightRequest;
import ma.mobility.airbridge.ws.ConfirmFlightResponse;
import ma.mobility.airbridge.ws.Flight;
import ma.mobility.airbridge.ws.HoldFlightRequest;
import ma.mobility.airbridge.ws.HoldFlightResponse;
import ma.mobility.airbridge.ws.ObjectFactory;
import ma.mobility.airbridge.ws.SearchFlightsRequest;
import ma.mobility.airbridge.ws.SearchFlightsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.List;
import java.util.function.Supplier;

/** Seul endroit qui parle SOAP au partenaire aérien. */
@Component
public class AirClient {

    private static final Logger log = LoggerFactory.getLogger(AirClient.class);

    private final WebServiceTemplate template;
    private final ObservationRegistry observations;
    private final ObjectFactory factory = new ObjectFactory();

    private final CircuitBreaker breaker;

    public AirClient(WebServiceTemplate template, ObservationRegistry observations,
                     CircuitBreakerRegistry breakers) {
        this.template = template;
        this.observations = observations;
        this.breaker = breakers.circuitBreaker("air");
    }

    /** Un appel SOAP = un span dans la trace, protégé par le circuit breaker "air". */
    private <T> T traced(String operation, Supplier<T> call) {
        log.info("SOAP call: {}", operation);
        return Observation.createNotStarted("soap.air." + operation, observations)
                .lowCardinalityKeyValue("rpc.system", "soap")
                .lowCardinalityKeyValue("rpc.method", operation)
                .observe(() -> breaker.executeSupplier(call::get));
    }

    public List<Flight> searchFlights(String origin, String destination, String date) {
        SearchFlightsRequest req = factory.createSearchFlightsRequest();
        req.setOrigin(origin);
        req.setDestination(destination);
        req.setDate(date);
        return traced("searchFlights", () -> {
            SearchFlightsResponse resp = (SearchFlightsResponse) template.marshalSendAndReceive(req);
            return resp.getFlight();
        });
    }

    /** @return holdId */
    public String holdFlight(String flightId, int seats) {
        HoldFlightRequest req = factory.createHoldFlightRequest();
        req.setFlightId(flightId);
        req.setSeats(seats);
        return traced("holdFlight", () -> {
            try {
                HoldFlightResponse resp = (HoldFlightResponse) template.marshalSendAndReceive(req);
                return resp.getHoldId();
            } catch (SoapFaultClientException e) {
                throw new OperatorRefusedException(e.getFaultStringOrReason());
            }
        });
    }

    /** @return bookingRef */
    public String confirmFlight(String holdId) {
        ConfirmFlightRequest req = factory.createConfirmFlightRequest();
        req.setHoldId(holdId);
        return traced("confirmFlight", () -> {
            try {
                ConfirmFlightResponse resp = (ConfirmFlightResponse) template.marshalSendAndReceive(req);
                return resp.getBookingRef();
            } catch (SoapFaultClientException e) {
                throw new OperatorRefusedException(e.getFaultStringOrReason());
            }
        });
    }

    /**
     * Idempotent côté legacy : le statut renvoyé est ignoré. Un fault ici n'est jamais
     * un refus métier : c'est un échec technique (503), pas un 409.
     */
    public void cancelFlight(String reference) {
        CancelFlightRequest req = factory.createCancelFlightRequest();
        req.setReference(reference);
        traced("cancelFlight", () -> {
            try {
                template.marshalSendAndReceive(req);
                return null;
            } catch (SoapFaultClientException e) {
                throw new UpstreamFailureException("Unexpected SOAP fault on cancel", e);
            }
        });
    }
}