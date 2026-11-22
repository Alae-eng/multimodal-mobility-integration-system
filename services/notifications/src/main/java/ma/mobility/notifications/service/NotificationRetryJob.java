package ma.mobility.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically delivers the notifications that failed or were interrupted before they were sent. */
@Component
public class NotificationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final NotificationService service;

    public NotificationRetryJob(NotificationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${notifications.retry.interval:PT30S}",
               initialDelayString = "${notifications.retry.initial-delay:PT30S}")
    public void run() {
        int sent = service.retryPending();
        if (sent > 0) {
            log.info("NotificationRetryJob: {} pending notification(s) delivered", sent);
        }
    }
}
