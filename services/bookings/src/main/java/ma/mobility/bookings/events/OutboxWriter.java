package ma.mobility.bookings.events;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import ma.mobility.bookings.model.Booking;
import ma.mobility.bookings.model.OutboxEvent;
import ma.mobility.bookings.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Records an event in the outbox table, inside the transaction that changes the booking.
 * MANDATORY: calling it without a transaction is a programming error, because the whole point is
 * that the booking change and its event are committed together or not at all.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public OutboxWriter(OutboxRepository outbox, Clock clock, ObjectMapper json) {
        this.outbox = outbox;
        this.clock = clock;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BookingEvent event, Booking booking) {
        String eventId = "EVT-" + UUID.randomUUID();
        List<BookingEventPayload.Leg> legs = booking.getLegs().stream()
                .map(l -> new BookingEventPayload.Leg(l.getOperator().name(), l.getOfferId(),
                        l.getReference(), l.getPriceMinor()))
                .toList();
        BookingEventPayload payload = new BookingEventPayload(eventId, event.eventName(), booking.getId(),
                booking.getTravelerId(), booking.getStatus().name(), booking.getTotalMinor(),
                booking.getQuoteId(), booking.getFailureReason(), legs, clock.instant());

        outbox.save(new OutboxEvent(eventId, event.eventName(), event.routingKey(),
                json.writeValueAsString(payload), clock.instant()));
    }
}
