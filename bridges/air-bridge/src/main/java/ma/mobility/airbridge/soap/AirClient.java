package ma.mobility.airbridge.soap;

import ma.mobility.airbridge.ws.Flight;
import ma.mobility.airbridge.ws.ObjectFactory;
import ma.mobility.airbridge.ws.SearchFlightsRequest;
import ma.mobility.airbridge.ws.SearchFlightsResponse;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

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
}