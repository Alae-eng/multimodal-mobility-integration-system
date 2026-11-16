package ma.mobility.airbridge.soap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class AirClientConfig {

    @Bean
    WebServiceTemplate airTemplate(@Value("${air.url}") String airUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("ma.mobility.airbridge.ws");
        WebServiceTemplate t = new WebServiceTemplate(marshaller);
        t.setDefaultUri(airUrl);
        return t;
    }
}