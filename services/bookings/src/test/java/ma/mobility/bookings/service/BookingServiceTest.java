package ma.mobility.bookings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import ma.mobility.bookings.dto.ConfirmBookingRequest;
import ma.mobility.bookings.dto.ConfirmBookingRequest.LegReference;
import ma.mobility.bookings.dto.CreateBookingRequest;
import ma.mobility.bookings.dto.LegInput;
import ma.mobility.bookings.exception.BookingNotFoundException;
import ma.mobility.bookings.exception.IdempotencyConflictException;
import ma.mobility.bookings.exception.InvalidBookingException;
import ma.mobility.bookings.model.Booking;
import ma.mobility.bookings.model.BookingStatus;
import ma.mobility.bookings.model.IllegalBookingStateException;
import ma.mobility.bookings.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingServiceTest extends IntegrationTestBase {

    @Autowired BookingService service;
    @Autowired BookingExpirationJob expirationJob;

    private static CreateBookingRequest trip(String travelerId) {
        return new CreateBookingRequest(travelerId, List.of(
                new LegInput("RAIL", "RAIL-CAS-RAB-0730", "RAILHOLD-1", 8000L),
                new LegInput("AIR", "AIR-RBA-ORY-1200", "AIRHOLD-1", 180000L),
                new LegInput("CARSHARING", "CAR-PAR-001", "CARHOLD-1", 30000L)), 196200L, "QUOTE-1");
    }

    private static ConfirmBookingRequest refs() {
        return new ConfirmBookingRequest(List.of(
                new LegReference("RAIL", "RAILBK-1"),
                new LegReference("AIR", "AIRBK-1"),
                new LegReference("CARSHARING", "CARBK-1")));
    }

    private Booking createPending() {
        return service.create(trip("traveler-1"), null);
    }

    // ---- create ----------------------------------------------------------------------------

    @Test
    void createOpensAPendingFileWithTheHoldReferences() {
        Booking b = createPending();

        assertThat(b.getId()).startsWith("BK-");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.getTotalMinor()).isEqualTo(196200L);
        assertThat(b.getLegs()).extracting(l -> l.getReference())
                .containsExactlyInAnyOrder("RAILHOLD-1", "AIRHOLD-1", "CARHOLD-1");
    }

    @Test
    void createPublishesNothing() {
        createPending();

        assertThat(outbox.count()).isZero();
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameFileEvenIfLegsAreInAnotherOrder() {
        Booking first = service.create(trip("traveler-1"), "key-1");
        CreateBookingRequest reordered = new CreateBookingRequest("traveler-1", List.of(
                new LegInput("CARSHARING", "CAR-PAR-001", "CARHOLD-1", 30000L),
                new LegInput("RAIL", "RAIL-CAS-RAB-0730", "RAILHOLD-1", 8000L),
                new LegInput("AIR", "AIR-RBA-ORY-1200", "AIRHOLD-1", 180000L)), 196200L, "QUOTE-1");

        assertThat(service.create(reordered, "key-1").getId()).isEqualTo(first.getId());
        assertThat(bookings.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithAnotherTripIsRefused() {
        service.create(trip("traveler-1"), "key-2");

        assertThatThrownBy(() -> service.create(trip("traveler-2"), "key-2"))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(bookings.count()).isEqualTo(1);
    }

    @Test
    void aDuplicateOperatorOrUnknownOperatorIsRefused() {
        CreateBookingRequest twoRails = new CreateBookingRequest("t", List.of(
                new LegInput("RAIL", "A", "H1", 1L), new LegInput("RAIL", "B", "H2", 2L)), 3L, null);
        CreateBookingRequest bus = new CreateBookingRequest("t", List.of(new LegInput("BUS", "A", "H1", 1L)), 1L, null);

        assertThatThrownBy(() -> service.create(twoRails, null)).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> service.create(bus, null)).isInstanceOf(InvalidBookingException.class);
        assertThat(bookings.count()).isZero();
    }

    // ---- confirm ---------------------------------------------------------------------------

    @Test
    void confirmReplacesHoldReferencesByBookingRefsAndAnnouncesTheTripOnce() {
        Booking pending = createPending();

        Booking confirmed = service.confirm(pending.getId(), refs());

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getLegs()).extracting(l -> l.getReference())
                .containsExactlyInAnyOrder("RAILBK-1", "AIRBK-1", "CARBK-1");
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getEventName()).isEqualTo("BookingConfirmed");
    }

    @Test
    void confirmReplayChangesNothingAndPublishesNothing() {
        Booking pending = createPending();
        service.confirm(pending.getId(), refs());

        Booking again = service.confirm(pending.getId(), refs());

        assertThat(again.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void confirmWithAMissingOrUnknownReferenceIsRefusedAndTheFileStaysPending() {
        Booking pending = createPending();
        ConfirmBookingRequest missingCar = new ConfirmBookingRequest(List.of(
                new LegReference("RAIL", "RAILBK-1"), new LegReference("AIR", "AIRBK-1")));
        ConfirmBookingRequest extraLeg = new ConfirmBookingRequest(List.of(
                new LegReference("RAIL", "RAILBK-1"), new LegReference("AIR", "AIRBK-1"),
                new LegReference("CARSHARING", "CARBK-1"), new LegReference("BUS", "X")));

        assertThatThrownBy(() -> service.confirm(pending.getId(), missingCar)).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> service.confirm(pending.getId(), extraLeg)).isInstanceOf(InvalidBookingException.class);

        assertThat(service.get(pending.getId()).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(service.get(pending.getId()).getLegs()).extracting(l -> l.getReference())
                .containsExactlyInAnyOrder("RAILHOLD-1", "AIRHOLD-1", "CARHOLD-1");
        assertThat(outbox.count()).isZero();
    }

    @Test
    void aCancelledOrFailedFileCannotBeConfirmed() {
        Booking cancelled = createPending();
        service.cancel(cancelled.getId());
        Booking failed = service.create(trip("traveler-2"), null);
        service.fail(failed.getId(), "no seats");

        assertThatThrownBy(() -> service.confirm(cancelled.getId(), refs()))
                .isInstanceOf(IllegalBookingStateException.class);
        assertThatThrownBy(() -> service.confirm(failed.getId(), refs()))
                .isInstanceOf(IllegalBookingStateException.class);
    }

    @Test
    void confirmOfAnUnknownFileIs404() {
        assertThatThrownBy(() -> service.confirm("BK-nope", refs())).isInstanceOf(BookingNotFoundException.class);
    }

    // ---- cancel ----------------------------------------------------------------------------

    @Test
    void cancellingAConfirmedFileAnnouncesItOnce() {
        Booking b = createPending();
        service.confirm(b.getId(), refs());

        service.cancel(b.getId());
        service.cancel(b.getId());

        assertThat(service.get(b.getId()).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(outbox.findAll()).extracting(e -> e.getEventName())
                .containsExactlyInAnyOrder("BookingConfirmed", "BookingCancelled");
    }

    @Test
    void cancellingAPendingFileAnnouncesNothingBecauseNobodyWasTold() {
        Booking b = createPending();

        service.cancel(b.getId());

        assertThat(service.get(b.getId()).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void cancelNeverFailsWhateverTheFile() {
        Booking failed = createPending();
        service.fail(failed.getId(), "no seats");

        service.cancel("BK-does-not-exist");
        service.cancel(failed.getId());
        service.cancel(failed.getId());

        assertThat(service.get(failed.getId()).getStatus()).isEqualTo(BookingStatus.FAILED);
    }

    // ---- fail ------------------------------------------------------------------------------

    @Test
    void failRecordsTheReasonAndPublishesSagaFailedOnce() {
        Booking b = createPending();

        service.fail(b.getId(), "no seats on AIR-CMN-MAD-1800");
        service.fail(b.getId(), "no seats on AIR-CMN-MAD-1800");

        Booking failed = service.get(b.getId());
        assertThat(failed.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("no seats on AIR-CMN-MAD-1800");
        assertThat(outbox.findAll()).extracting(e -> e.getEventName()).containsExactly("SagaFailed");
    }

    @Test
    void aConfirmedFileCannotBeFailed() {
        Booking b = createPending();
        service.confirm(b.getId(), refs());

        assertThatThrownBy(() -> service.fail(b.getId(), "late")).isInstanceOf(IllegalBookingStateException.class);
        assertThat(service.get(b.getId()).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ---- abandoned files -------------------------------------------------------------------

    @Test
    void anAbandonedPendingFileIsFailedAfterTheTtlAndAnnouncedOnce() {
        Booking old = createPending();
        clock.advance(Duration.ofMinutes(10));
        Booking recent = service.create(trip("traveler-2"), null);
        clock.advance(Duration.ofMinutes(6));          // old: 16 min, recent: 6 min

        expirationJob.run();
        expirationJob.run();

        assertThat(service.get(old.getId()).getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(service.get(recent.getId()).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(outbox.findAll()).extracting(e -> e.getEventName()).containsExactly("SagaFailed");
    }

    @Test
    void theExpiryNeverTouchesConfirmedOrCancelledFiles() {
        Booking confirmed = createPending();
        service.confirm(confirmed.getId(), refs());
        Booking cancelled = service.create(trip("traveler-2"), null);
        service.cancel(cancelled.getId());
        clock.advance(Duration.ofHours(3));

        expirationJob.run();

        assertThat(service.get(confirmed.getId()).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(service.get(cancelled.getId()).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // ---- reads -----------------------------------------------------------------------------

    @Test
    void aTravelerSeesOnlyTheirOwnFilesMostRecentFirst() {
        Booking first = service.create(trip("traveler-1"), null);
        clock.advance(Duration.ofMinutes(1));
        Booking second = service.create(new CreateBookingRequest("traveler-1",
                List.of(new LegInput("RAIL", "RAIL-2", "RAILHOLD-2", 5000L)), 5000L, null), null);
        service.create(trip("someone-else"), null);

        assertThat(service.listForTraveler("traveler-1")).extracting(Booking::getId)
                .containsExactly(second.getId(), first.getId());
    }
}
