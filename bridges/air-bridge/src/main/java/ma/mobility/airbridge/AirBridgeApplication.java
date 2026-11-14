package ma.mobility.airbridge;

import ma.mobility.airbridge.ws.Flight;
import ma.mobility.airbridge.ws.ObjectFactory;
import ma.mobility.airbridge.ws.SearchFlightsRequest;
import ma.mobility.airbridge.ws.SearchFlightsResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/** Lot 0 : appel SOAP minimal (searchFlights) vers le partenaire aérien. Jetable. */
@SpringBootApplication
public class AirBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirBridgeApplication.class, args);
    }

    @Bean
    WebServiceTemplate airTemplate() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("ma.mobility.airbridge.ws");
        WebServiceTemplate t = new WebServiceTemplate(marshaller);
        t.setDefaultUri(System.getenv().getOrDefault("AIR_URL", "http://air:8080/ws"));
        return t;
    }

    @Bean
    CommandLineRunner smoke(WebServiceTemplate airTemplate) {
        return args -> {
            SearchFlightsRequest req = new ObjectFactory().createSearchFlightsRequest();
            req.setOrigin("Rabat");
            req.setDestination("Paris");
            req.setDate("2026-11-15");

            SearchFlightsResponse resp = (SearchFlightsResponse) airTemplate.marshalSendAndReceive(req);

            System.out.println("Vols trouvés : " + resp.getFlight().size());
            for (Flight f : resp.getFlight()) {
                System.out.printf("  %s  %s -> %s  %s  %d centimes  %d places%n",
                        f.getFlightId(), f.getOrigin(), f.getDestination(),
                        f.getDeparture(), f.getPriceMinor(), f.getSeatsAvailable());
            }
        };
    }
}