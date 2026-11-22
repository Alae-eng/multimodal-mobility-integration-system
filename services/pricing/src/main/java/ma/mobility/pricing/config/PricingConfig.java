package ma.mobility.pricing.config;

import java.time.Clock;
import ma.mobility.pricing.domain.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class PricingConfig {

    /** Single source of time, so tests can fix it. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
