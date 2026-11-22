package ma.mobility.bookings.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically fails the travel files the saga left PENDING, so they do not stay open forever. */
@Component
public class BookingExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationJob.class);

    private final BookingService service;

    public BookingExpirationJob(BookingService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${bookings.expiration.interval:PT30S}",
               initialDelayString = "${bookings.expiration.initial-delay:PT30S}")
    public void run() {
        int expired = service.expireStalePending();
        if (expired > 0) {
            log.info("BookingExpirationJob: {} abandoned booking(s) failed", expired);
        }
    }
}
