package ma.mobility.pricing.support;

import ma.mobility.pricing.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Full application context on an in-memory database; each test starts from an empty database. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected QuoteRepository quotes;

    @BeforeEach
    void resetState() {
        quotes.deleteAll();
    }
}
