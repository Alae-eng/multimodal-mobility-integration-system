package ma.mobility.airbridge.soap;

import ma.mobility.airbridge.ws.CancelFlightRequest;
import ma.mobility.airbridge.ws.ConfirmFlightRequest;
import ma.mobility.airbridge.ws.ConfirmFlightResponse;
import ma.mobility.airbridge.ws.Flight;
import ma.mobility.airbridge.ws.HoldFlightRequest;
import ma.mobility.airbridge.ws.HoldFlightResponse;
import ma.mobility.airbridge.ws.ObjectFactory;
import ma.mobility.airbridge.ws.SearchFlightsRequest;
import ma.mobility.airbridge.ws.SearchFlightsResponse;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.List;

/** Seul endroit qui parle SOAP au partenaire aérien. */
@Component
public class AirClient {

    private final WebServiceTemplate template;
    private final ObjectFactory factory = new ObjectFactory();

    public AirClient(WebServiceTemplate template) {
        this.template = template;
    }

    public List<Flight> searchFlights(String origin, String destination, String date) {
        SearchFlightsRequest req = factory.createSearchFlightsRequest();
        req.setOrigin(origin);
        req.setDestination(destination);
        req.setDate(date);
        SearchFlightsResponse resp = (SearchFlightsResponse) template.marshalSendAndReceive(req);
        return resp.getFlight();
    }

    /** @return holdId */
    public String holdFlight(String flightId, int seats) {
        HoldFlightRequest req = factory.createHoldFlightRequest();
        req.setFlightId(flightId);
        req.setSeats(seats);
        try {
            HoldFlightResponse resp = (HoldFlightResponse) template.marshalSendAndReceive(req);
            return resp.getHoldId();
        } catch (SoapFaultClientException e) {
            throw new OperatorRefusedException(e.getFaultStringOrReason());
        }
    }

    /** @return bookingRef */
    public String confirmFlight(String holdId) {
        ConfirmFlightRequest req = factory.createConfirmFlightRequest();
        req.setHoldId(holdId);
        try {
            ConfirmFlightResponse resp = (ConfirmFlightResponse) template.marshalSendAndReceive(req);
            return resp.getBookingRef();
        } catch (SoapFaultClientException e) {
            throw new OperatorRefusedException(e.getFaultStringOrReason());
        }
    }

    /**
     * Idempotent côté legacy : le statut renvoyé est ignoré. Un fault ici n'est jamais
     * un refus métier : c'est un échec technique (503), pas un 409.
     */
    public void cancelFlight(String reference) {
        CancelFlightRequest req = factory.createCancelFlightRequest();
        req.setReference(reference);
        try {
            template.marshalSendAndReceive(req);
        } catch (SoapFaultClientException e) {
            throw new UpstreamFailureException("Unexpected SOAP fault on cancel", e);
        }
    }
}