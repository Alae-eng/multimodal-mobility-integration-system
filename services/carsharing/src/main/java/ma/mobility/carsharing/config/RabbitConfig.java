package ma.mobility.carsharing.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    /** Shared topic exchange; routing key = event name (BookingConfirmed, BookingCancelled...). */
    @Bean
    public TopicExchange eventsExchange(@Value("${carsharing.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
}
