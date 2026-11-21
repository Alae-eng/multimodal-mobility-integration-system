package ma.mobility.carsharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import ma.mobility.carsharing.dto.OfferResponse;
import ma.mobility.carsharing.exception.NotAvailableException;
import ma.mobility.carsharing.model.Hold;
import ma.mobility.carsharing.model.HoldStatus;
import ma.mobility.carsharing.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;

class CarsharingServiceTest extends IntegrationTestBase {

    @Autowired CarsharingService service;
    @Autowired HoldExpirationJob expirationJob;

    // ---- search ----------------------------------------------------------------------------

    @Test
    void searchReturnsOneCanonicalOfferPerFreeVehicleInTheDestinationCity() {
        List<OfferResponse> offers = service.search("Casablanca", "rabat", LocalDate.parse("2026-11-15"));

        assertThat(offers).hasSize(2);
        OfferResponse offer = offers.stream().filter(o -> o.offerId().equals(RAB_1)).findFirst().orElseThrow();
        assertThat(offer.operator()).isEqualTo("CARSHARING");
        assertThat(offer.departure()).isEqualTo("2026-11-15T08:00:00");
        assertThat(offer.arrival()).isEqualTo("2026-11-15T20:00:00");
        assertThat(offer.priceMinor()).isEqualTo(15000L);
        assertThat(offer.seatsAvailable()).isEqualTo(1);
    }

    @Test
    void searchWithNoMatchReturnsEmptyListNotAnError() {
        assertThat(service.search("Rabat", "Tokyo", LocalDate.parse("2026-11-15"))).isEmpty();
    }

    @Test
    void aHeldVehicleDisappearsFromSearch() {
        service.hold(RAB_1, null);

        assertThat(service.search("x", "Rabat", LocalDate.parse("2026-11-15")))
                .extracting(OfferResponse::offerId).containsExactly(RAB_2);
    }

    // ---- hold ------------------------------------------------------------------------------

    @Test
    void holdTakesTheVehicleAndSecondHoldIsRefused() {
        Hold hold = service.hold(RAB_1, null);

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isFalse();
        assertThatThrownBy(() -> service.hold(RAB_1, null)).isInstanceOf(NotAvailableException.class);
    }

    @Test
    void holdOnUnknownOfferIsRefused() {
        assertThatThrownBy(() -> service.hold("CAR-NOPE-999", null)).isInstanceOf(NotAvailableException.class);
    }

    @Test
    void sameIdempotencyKeyGivesTheSameHoldAndHoldsNothingTwice() {
        Hold first = service.hold(RAB_1, "key-1");
        Hold again = service.hold(RAB_1, "key-1");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(holds.count()).isEqualTo(1);
        assertThat(vehicles.findById(RAB_2).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void idempotencyKeyStillReturnsTheSameHoldIdAfterCancel() {
        Hold first = service.hold(RAB_1, "key-2");
        service.cancel(first.getId());

        assertThat(service.hold(RAB_1, "key-2").getId()).isEqualTo(first.getId());
    }

    // ---- confirm ---------------------------------------------------------------------------

    @Test
    void confirmReturnsABookingRefAndReplayReturnsTheSameOne() {
        Hold hold = service.hold(RAB_1, null);

        String ref = service.confirm(hold.getId());
        assertThat(ref).startsWith("CARBK-");
        assertThat(service.confirm(hold.getId())).isEqualTo(ref);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isFalse();
    }

    @Test
    void confirmOnUnknownHoldIsRefused() {
        assertThatThrownBy(() -> service.confirm("CARHOLD-unknown")).isInstanceOf(NotAvailableException.class);
    }

    @Test
    void confirmAfterTenMinutesIsRefusedAndFreesTheVehicle() {
        Hold hold = service.hold(RAB_1, null);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThatThrownBy(() -> service.confirm(hold.getId())).isInstanceOf(NotAvailableException.class);

        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void confirmJustBeforeTenMinutesStillWorks() {
        Hold hold = service.hold(RAB_1, null);
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));

        assertThat(service.confirm(hold.getId())).startsWith("CARBK-");
    }

    // ---- cancel ----------------------------------------------------------------------------

    @Test
    void cancelByHoldIdFreesTheVehicle() {
        Hold hold = service.hold(RAB_1, null);

        service.cancel(hold.getId());

        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CANCELLED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void cancelByBookingRefFreesTheVehicle() {
        Hold hold = service.hold(RAB_1, null);
        String ref = service.confirm(hold.getId());

        service.cancel(ref);

        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CANCELLED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void cancelIsIdempotentAndNeverFails() {
        Hold hold = service.hold(RAB_1, null);
        String ref = service.confirm(hold.getId());

        service.cancel(ref);
        service.cancel(ref);
        service.cancel(hold.getId());
        service.cancel("does-not-exist");

        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CANCELLED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void aReplayedCancelDoesNotFreeAVehicleThatSomeoneElseHasTakenSince() {
        Hold first = service.hold(RAB_1, null);
        service.cancel(first.getId());
        service.hold(RAB_1, null);          // another traveler takes the car

        service.cancel(first.getId());      // the saga replays its compensation

        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isFalse();
    }

    // ---- expiry job ------------------------------------------------------------------------

    @Test
    void expiryReleasesOldHeldHoldsOnly() {
        Hold old = service.hold(RAB_1, null);
        clock.advance(Duration.ofMinutes(6));
        Hold recent = service.hold(RAB_2, null);
        clock.advance(Duration.ofMinutes(5));     // old: 11 min, recent: 5 min

        expirationJob.run();

        assertThat(holds.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isTrue();
        assertThat(holds.findById(recent.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.HELD);
        assertThat(vehicles.findById(RAB_2).orElseThrow().isAvailable()).isFalse();
    }

    @Test
    void expiryNeverTouchesAConfirmedBooking() {
        Hold hold = service.hold(RAB_1, null);
        service.confirm(hold.getId());
        clock.advance(Duration.ofHours(2));

        expirationJob.run();

        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(vehicles.findById(RAB_1).orElseThrow().isAvailable()).isFalse();
    }

    @Test
    void anExpiredHoldCannotBeConfirmedEvenIfTheJobHasNotRunYet() {
        Hold hold = service.hold(RAB_1, null);
        clock.advance(Duration.ofMinutes(11));
        expirationJob.run();

        assertThatThrownBy(() -> service.confirm(hold.getId())).isInstanceOf(NotAvailableException.class);
        assertThat(service.releaseExpiredHolds()).isZero();
    }

    // ---- events ----------------------------------------------------------------------------

    @Test
    void confirmPublishesOneCarReservationConfirmedEventAndReplayPublishesNone() {
        Hold hold = service.hold(RAB_1, null);

        service.confirm(hold.getId());
        service.confirm(hold.getId());

        verify(rabbit, times(1)).send(eq("mobility.events"), eq("carsharing.reservation.confirmed"), any(Message.class));
    }

    @Test
    void cancellingAConfirmedBookingPublishesOneCancelledEventReplaysNone() {
        Hold hold = service.hold(RAB_1, null);
        String ref = service.confirm(hold.getId());

        service.cancel(ref);
        service.cancel(ref);

        verify(rabbit, times(1)).send(eq("mobility.events"), eq("carsharing.reservation.cancelled"), any(Message.class));
    }

    @Test
    void releasingAnUnconfirmedHoldPublishesNoEvent() {
        Hold hold = service.hold(RAB_1, null);

        service.cancel(hold.getId());

        verify(rabbit, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void eventPayloadCarriesTheBookingDetails() {
        Hold hold = service.hold(RAB_1, null);
        service.confirm(hold.getId());

        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(anyString(), anyString(), captor.capture());
        String json = new String(captor.getValue().getBody());
        assertThat(json).contains("\"event\":\"CarReservationConfirmed\"")
                .contains("\"holdId\":\"" + hold.getId() + "\"")
                .contains("\"vehicleId\":\"" + RAB_1 + "\"")
                .contains("\"operator\":\"CARSHARING\"")
                .contains("\"bookingRef\":\"CARBK-");
        assertThat(captor.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void aDeadBrokerNeverBreaksTheBookingNorBlocksIt() {
        doThrow(new AmqpConnectException(new java.io.IOException("broker down")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class));
        Hold hold = service.hold(RAB_1, null);

        String ref = service.confirm(hold.getId());

        assertThat(ref).startsWith("CARBK-");
        assertThat(holds.findById(hold.getId()).orElseThrow().getStatus()).isEqualTo(HoldStatus.CONFIRMED);
    }

    private static <T> T any(Class<T> type) {
        return org.mockito.ArgumentMatchers.any(type);
    }
}
