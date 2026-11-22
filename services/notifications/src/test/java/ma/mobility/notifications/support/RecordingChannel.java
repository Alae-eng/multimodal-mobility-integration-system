package ma.mobility.notifications.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import ma.mobility.notifications.channel.NotificationChannel;
import ma.mobility.notifications.model.Notification;

/** Stands in for the delivery provider: records what was sent and can be told to fail. */
public class RecordingChannel implements NotificationChannel {

    private final List<String> sentBookingIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    /** The next n delivery attempts fail. */
    public void failNext(int n) {
        failuresLeft.set(n);
    }

    public List<String> sentBookingIds() {
        return sentBookingIds;
    }

    public void reset() {
        sentBookingIds.clear();
        failuresLeft.set(0);
    }

    @Override
    public String name() {
        return "TEST";
    }

    @Override
    public void deliver(Notification notification) {
        if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new IllegalStateException("provider unavailable");
        }
        sentBookingIds.add(notification.getBookingId() + ":" + notification.getType().eventName());
    }
}
