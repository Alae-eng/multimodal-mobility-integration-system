package ma.mobility.bookings.repository;

import java.util.List;
import ma.mobility.bookings.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** Oldest unsent events first, so the broker receives them in the order they happened. */
    List<OutboxEvent> findTop50BySentAtIsNullOrderByIdAsc();

    long countBySentAtIsNull();
}
