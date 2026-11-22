package ma.mobility.notifications.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    /** MutableClock is a java.time.Clock: as the primary one it replaces the production clock. */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-11-15T09:00:00Z"));
    }

    /** Replaces the shipped channel (primary NotificationChannel): tests see exactly what was sent. */
    @Bean
    @Primary
    public RecordingChannel recordingChannel() {
        return new RecordingChannel();
    }
}
