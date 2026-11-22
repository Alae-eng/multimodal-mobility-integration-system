package ma.mobility.bookings.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ma.mobility.bookings.events.EventSender;
import ma.mobility.bookings.model.OutboxEvent;

/** Stands in for RabbitMQ: records what was delivered and can simulate a dead broker. */
public class RecordingEventSender implements EventSender {

    private final List<OutboxEvent> delivered = new CopyOnWriteArrayList<>();
    private volatile boolean brokerDown;

    public void setBrokerDown(boolean down) {
        this.brokerDown = down;
    }

    public List<OutboxEvent> delivered() {
        return delivered;
    }

    public List<String> deliveredNames() {
        return delivered.stream().map(OutboxEvent::getEventName).toList();
    }

    public void reset() {
        delivered.clear();
        brokerDown = false;
    }

    @Override
    public void send(OutboxEvent event) {
        if (brokerDown) {
            throw new EventSendException("broker down");
        }
        delivered.add(event);
    }
}
