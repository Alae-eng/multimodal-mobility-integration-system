package ma.mobility.notifications.channel;

import ma.mobility.notifications.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in for an email provider: writes the message in the log, as a real one would send it. */
@Component
public class SimulatedEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailChannel.class);

    @Override
    public String name() {
        return "EMAIL";
    }

    @Override
    public void deliver(Notification notification) {
        log.info("[simulated email] to traveler {} | {} | booking {}",
                notification.getTravelerId(), notification.getSubject(), notification.getBookingId());
    }
}
