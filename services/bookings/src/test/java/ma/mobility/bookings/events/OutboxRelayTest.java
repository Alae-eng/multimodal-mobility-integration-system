package ma.mobility.bookings.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import ma.mobility.bookings.dto.ConfirmBookingRequest;
import ma.mobility.bookings.dto.ConfirmBookingRequest.LegReference;
import ma.mobility.bookings.dto.CreateBookingRequest;
import ma.mobility.bookings.dto.LegInput;
import ma.mobility.bookings.model.Booking;
import ma.mobility.bookings.model.OutboxEvent;
import ma.mobility.bookings.service.BookingService;
import ma.mobility.bookings.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The guarantees of the outbox: no event lost, events in order, no hammering of a dead broker. */
class OutboxRelayTest extends IntegrationTestBase {

    @Autowired BookingService service;

    private Booking confirmedBooking() {
        Booking b = service.create(new CreateBookingRequest("traveler-1",
                List.of(new LegInput("RAIL", "RAIL-1", "RAILHOLD-1", 8000L)), 8000L, null), null);
        return service.confirm(b.getId(), new ConfirmBookingRequest(List.of(new LegReference("RAIL", "RAILBK-1"))));
    }

    @Test
    void anEventIsDeliveredOnceAndMarkedSent() {
        confirmedBooking();

        assertThat(relay.relayPending()).isEqualTo(1);
        assertThat(relay.relayPending()).isZero();

        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed");
        assertThat(outbox.countBySentAtIsNull()).isZero();
    }

    @Test
    void aDeadBrokerLosesNothingTheEventIsDeliveredWhenItComesBack() {
        sender.setBrokerDown(true);
        confirmedBooking();

        assertThat(relay.relayPending()).isZero();
        assertThat(outbox.countBySentAtIsNull()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getAttempts()).isEqualTo(1);

        sender.setBrokerDown(false);
        clock.advance(Duration.ofMinutes(1));
        assertThat(relay.relayPending()).isEqualTo(1);
        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed");
    }

    @Test
    void theRelayBacksOffInsteadOfRetryingEverySecond() {
        sender.setBrokerDown(true);
        confirmedBooking();

        relay.relayPending();                       // fails, next attempt in 2 s
        relay.relayPending();                       // too early: not even tried
        relay.relayPending();

        assertThat(outbox.findAll().get(0).getAttempts()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(3));
        relay.relayPending();                       // second attempt, next in 4 s
        assertThat(outbox.findAll().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void eventsLeaveInTheOrderTheyHappened() {
        Booking b = confirmedBooking();
        service.cancel(b.getId());

        relay.relayPending();

        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed", "BookingCancelled");
    }

    @Test
    void aFailureStopsTheBatchSoOrderIsKept() {
        Booking b = confirmedBooking();
        service.cancel(b.getId());
        sender.setBrokerDown(true);

        assertThat(relay.relayPending()).isZero();
        sender.setBrokerDown(false);
        clock.advance(Duration.ofMinutes(1));
        relay.relayPending();

        assertThat(sender.deliveredNames()).containsExactly("BookingConfirmed", "BookingCancelled");
    }

    @Test
    void thePayloadCarriesTheWholeTravelFileAndAUniqueEventId() {
        Booking b = confirmedBooking();
        service.cancel(b.getId());
        relay.relayPending();

        List<OutboxEvent> delivered = sender.delivered();
        String confirmed = delivered.get(0).getPayload();
        assertThat(confirmed).contains("\"event\":\"BookingConfirmed\"")
                .contains("\"bookingId\":\"" + b.getId() + "\"")
                .contains("\"travelerId\":\"traveler-1\"")
                .contains("\"status\":\"CONFIRMED\"")
                .contains("\"totalMinor\":8000")
                .contains("\"reference\":\"RAILBK-1\"")
                .contains("\"operator\":\"RAIL\"");
        assertThat(delivered.get(0).getRoutingKey()).isEqualTo("booking.confirmed");
        assertThat(delivered.get(1).getRoutingKey()).isEqualTo("booking.cancelled");
        assertThat(delivered).extracting(OutboxEvent::getEventId).doesNotHaveDuplicates();
        assertThat(delivered.get(0).getPayload()).contains("\"eventId\":\"" + delivered.get(0).getEventId() + "\"");
    }
}
