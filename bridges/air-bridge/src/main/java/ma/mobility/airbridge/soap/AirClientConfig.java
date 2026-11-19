package ma.mobility.airbridge.soap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.time.Duration;

@Configuration
public class AirClientConfig {

    @Bean
    WebServiceTemplate airTemplate(
            @Value("${air.url}") String airUrl,
            @Value("${air.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${air.read-timeout-ms}") long readTimeoutMs) {

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("ma.mobility.airbridge.ws");
        // Sans délais, un partenaire qui ne répond plus bloque les threads indéfiniment
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofMillis(connectTimeoutMs));
        sender.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        WebServiceTemplate t = new WebServiceTemplate(marshaller);
        t.setDefaultUri(airUrl);
        t.setMessageSender(sender);
        return t;
    }
}