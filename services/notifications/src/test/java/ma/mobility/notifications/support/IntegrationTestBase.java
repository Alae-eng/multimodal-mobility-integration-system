package ma.mobility.notifications.support;

import java.time.Instant;
import ma.mobility.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Full application context on an in-memory database; each test starts from an empty state. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTestBase {

    @Autowired protected NotificationRepository notifications;
    @Autowired protected MutableClock clock;
    @Autowired protected RecordingChannel channel;

    @BeforeEach
    void resetState() {
        notifications.deleteAll();
        channel.reset();
        clock.set(Instant.parse("2026-11-15T09:00:00Z"));
    }
}
