package ma.mobility.pricing.repository;

import java.util.Optional;
import ma.mobility.pricing.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, String> {

    Optional<Quote> findByIdempotencyKey(String idempotencyKey);
}
