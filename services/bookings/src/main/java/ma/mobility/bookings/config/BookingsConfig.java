package ma.mobility.bookings.config;

import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingsConfig {

    /** Single source of time, so tests can move the clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Shared topic exchange (same name and settings as the other services declare). */
    @Bean
    public TopicExchange eventsExchange(@Value("${bookings.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
}
