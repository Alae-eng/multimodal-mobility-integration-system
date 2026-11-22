package ma.mobility.bookings.support;

import java.time.Instant;
import ma.mobility.bookings.events.OutboxRelay;
import ma.mobility.bookings.repository.BookingRepository;
import ma.mobility.bookings.repository.OutboxRepository;
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

    @Autowired protected BookingRepository bookings;
    @Autowired protected OutboxRepository outbox;
    @Autowired protected MutableClock clock;
    @Autowired protected RecordingEventSender sender;
    @Autowired protected OutboxRelay relay;

    @BeforeEach
    void resetState() {
        outbox.deleteAll();
        bookings.deleteAll();
        sender.reset();
        clock.set(Instant.parse("2026-11-15T09:00:00Z"));
    }
}
