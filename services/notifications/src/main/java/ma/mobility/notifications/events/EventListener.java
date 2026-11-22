package ma.mobility.notifications.events;

import ma.mobility.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes the platform's trip events. A message that cannot be understood (wrong key, broken
 * JSON, missing field) is rejected without any retry: the same message would fail the same way,
 * so it goes straight to the dead-letter queue. A failure that may be temporary (the database is
 * down) propagates, and the listener retries it before parking it too.
 */
@Component
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final EventParser parser;
    private final NotificationService notifications;

    public EventListener(EventParser parser, NotificationService notifications) {
        this.parser = parser;
        this.notifications = notifications;
    }

    @RabbitListener(queues = "${notifications.queue}")
    public void onEvent(Message message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        IncomingEvent event;
        try {
            event = parser.parse(routingKey, message.getBody());
        } catch (InvalidEventException e) {
            log.error("Unusable event on '{}' sent to the dead-letter queue: {}", routingKey, e.getMessage());
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        notifications.handle(event);
    }
}
