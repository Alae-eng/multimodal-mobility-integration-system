package ma.mobility.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.mobility.notifications.events.IncomingEvent;
import ma.mobility.notifications.model.Notification;
import ma.mobility.notifications.model.NotificationStatus;
import ma.mobility.notifications.model.NotificationType;
import ma.mobility.notifications.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationServiceTest extends IntegrationTestBase {

    @Autowired NotificationService service;
    @Autowired NotificationRetryJob retryJob;

    private static IncomingEvent event(String eventId, NotificationType type, String bookingId) {
        return new IncomingEvent(eventId, type, bookingId, "traveler-1", 196200L,
                List.of(new IncomingEvent.Leg("RAIL", "RAILBK-1"), new IncomingEvent.Leg("AIR", "AIRBK-1")));
    }

    // ---- the three events ------------------------------------------------------------------

    @Test
    void aConfirmedBookingTellsTheTravelerWithTheTotalAndTheReferences() {
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));

        Notification n = notifications.findAll().get(0);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getTravelerId()).isEqualTo("traveler-1");
        assertThat(n.getSubject()).isEqualTo("Your trip is confirmed");
        assertThat(n.getBody()).contains("BK-1").contains("1962.00 MAD").contains("RAIL RAILBK-1").contains("AIR AIRBK-1");
        assertThat(channel.sentBookingIds()).containsExactly("BK-1:BookingConfirmed");
    }

    @Test
    void aCancelledBookingAndAFailedSagaEachHaveTheirOwnMessage() {
        service.handle(event("E1", NotificationType.BOOKING_CANCELLED, "BK-1"));
        service.handle(event("E2", NotificationType.SAGA_FAILED, "BK-2"));

        assertThat(notifications.findAll()).extracting(Notification::getSubject)
                .containsExactlyInAnyOrder("Your trip was cancelled", "We could not complete your booking");
        assertThat(notifications.findAll().stream().filter(n -> n.getType() == NotificationType.SAGA_FAILED)
                .findFirst().orElseThrow().getBody()).contains("Nothing was charged");
    }

    @Test
    void amountsAreShownInMadWithTwoDecimals() {
        assertThat(MessageComposer.formatMad(196200)).isEqualTo("1962.00 MAD");
        assertThat(MessageComposer.formatMad(8005)).isEqualTo("80.05 MAD");
        assertThat(MessageComposer.formatMad(7)).isEqualTo("0.07 MAD");
        assertThat(MessageComposer.formatMad(0)).isEqualTo("0.00 MAD");
    }

    // ---- duplicates ------------------------------------------------------------------------

    @Test
    void theSameEventDeliveredTwiceNotifiesOnce() {
        IncomingEvent e = event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1");

        service.handle(e);
        service.handle(e);
        service.handle(e);

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(channel.sentBookingIds()).hasSize(1);
    }

    @Test
    void theSameNewsAboutTheSameBookingUnderAnotherEventIdNotifiesOnce() {
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
        service.handle(event("E2", NotificationType.BOOKING_CONFIRMED, "BK-1"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(channel.sentBookingIds()).hasSize(1);
    }

    @Test
    void differentNewsAboutTheSameBookingAreAllDelivered() {
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
        service.handle(event("E2", NotificationType.BOOKING_CANCELLED, "BK-1"));

        assertThat(channel.sentBookingIds()).containsExactly("BK-1:BookingConfirmed", "BK-1:BookingCancelled");
    }

    @Test
    void twentySimultaneousCopiesOfOneEventNotifyExactlyOnce() throws Exception {
        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : results) {
            f.get();            // none of the copies may fail
        }
        pool.shutdownNow();

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(channel.sentBookingIds()).hasSize(1);
    }

    // ---- delivery failures -----------------------------------------------------------------

    @Test
    void aFailedDeliveryKeepsTheNotificationPendingAndTheRetryDeliversIt() {
        channel.failNext(1);

        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));

        Notification n = notifications.findAll().get(0);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getLastError()).isEqualTo("provider unavailable");
        assertThat(channel.sentBookingIds()).isEmpty();

        clock.advance(Duration.ofMinutes(1));
        retryJob.run();

        assertThat(notifications.findAll().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(channel.sentBookingIds()).containsExactly("BK-1:BookingConfirmed");
    }

    @Test
    void theRetryWaitsBeforeTryingAgain() {
        channel.failNext(1);
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));

        retryJob.run();                                   // too early: not even tried

        assertThat(notifications.findAll().get(0).getAttempts()).isEqualTo(1);
        assertThat(channel.sentBookingIds()).isEmpty();
    }

    @Test
    void afterTheMaximumAttemptsItGivesUpAndStopsTrying() {
        channel.failNext(100);
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofMinutes(1));
            retryJob.run();
        }

        Notification n = notifications.findAll().get(0);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getAttempts()).isEqualTo(5);

        channel.reset();
        clock.advance(Duration.ofMinutes(5));
        retryJob.run();
        assertThat(channel.sentBookingIds()).isEmpty();
    }

    @Test
    void aSentNotificationIsNeverSentAgainByTheRetryJob() {
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
        clock.advance(Duration.ofHours(1));

        retryJob.run();
        retryJob.run();

        assertThat(channel.sentBookingIds()).hasSize(1);
    }

    @Test
    void aFailedDeliveryNeverMakesTheEventFailSoTheBrokerDoesNotRedeliverIt() {
        channel.failNext(1);

        // must not throw: the event is recorded, the retry job owns the delivery from here
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));

        assertThat(notifications.count()).isEqualTo(1);
    }

    // ---- reads -----------------------------------------------------------------------------

    @Test
    void aTravelerSeesOnlyTheirOwnNotificationsMostRecentFirst() {
        service.handle(event("E1", NotificationType.BOOKING_CONFIRMED, "BK-1"));
        clock.advance(Duration.ofMinutes(1));
        service.handle(event("E2", NotificationType.BOOKING_CANCELLED, "BK-1"));
        service.handle(new IncomingEvent("E3", NotificationType.BOOKING_CONFIRMED, "BK-9", "someone-else", 1L, List.of()));

        assertThat(service.listForTraveler("traveler-1")).extracting(Notification::getType)
                .containsExactly(NotificationType.BOOKING_CANCELLED, NotificationType.BOOKING_CONFIRMED);
    }
}
