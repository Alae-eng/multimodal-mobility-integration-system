package ma.mobility.bookings.events;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import ma.mobility.bookings.model.OutboxEvent;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sends an outbox event to RabbitMQ and waits for the broker's publisher confirm. */
@Component
public class RabbitEventSender implements EventSender {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbit;
    private final String exchange;

    public RabbitEventSender(RabbitTemplate rabbit, @Value("${bookings.events.exchange}") String exchange) {
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    @Override
    public void send(OutboxEvent event) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setType(event.getEventName());
        props.setMessageId(event.getEventId());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props);

        CorrelationData correlation = new CorrelationData(event.getEventId());
        try {
            rabbit.send(exchange, event.getRoutingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new EventSendException("broker refused " + event.getEventName() + ": " + confirm.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventSendException("interrupted while sending " + event.getEventName(), e);
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            throw new EventSendException("could not send " + event.getEventName() + ": " + e.getMessage(), e);
        }
    }
}
