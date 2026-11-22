package ma.mobility.carsharing.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Single source of time, so tests can move the clock to check the 10-minute expiry. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
