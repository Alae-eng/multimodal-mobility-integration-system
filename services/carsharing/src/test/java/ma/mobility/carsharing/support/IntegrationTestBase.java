package ma.mobility.carsharing.support;

import java.time.Instant;
import ma.mobility.carsharing.events.CarsharingEventPublisher;
import ma.mobility.carsharing.model.Vehicle;
import ma.mobility.carsharing.repository.HoldRepository;
import ma.mobility.carsharing.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full application context on an in-memory database. RabbitMQ is replaced by a mock, so every
 * test can check exactly which events were published. Each test starts from the same data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class IntegrationTestBase {

    public static final String RAB_1 = "CAR-RAB-001";
    public static final String RAB_2 = "CAR-RAB-002";

    @Autowired protected VehicleRepository vehicles;
    @Autowired protected HoldRepository holds;
    @Autowired protected MutableClock clock;
    @Autowired protected CarsharingEventPublisher publisher;

    @MockitoBean protected RabbitTemplate rabbit;

    @BeforeEach
    void resetState() {
        holds.deleteAll();
        vehicles.deleteAll();
        vehicles.save(new Vehicle(RAB_1, "Rabat", "Dacia Spring", 15000));
        vehicles.save(new Vehicle(RAB_2, "Rabat", "Renault Clio", 18000));
        vehicles.save(new Vehicle("CAR-PAR-001", "Paris", "Renault Zoe", 30000));
        clock.set(Instant.parse("2026-11-15T09:00:00Z"));
        publisher.resetCircuitBreaker();
    }
}
