package ma.mobility.notifications.config;

import java.time.Clock;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ma.mobility.notifications.model.NotificationType;

/**
 * RabbitMQ topology. The exchange is shared by the platform (same name and settings as the other
 * services declare); the queue belongs to this service, so no event is lost while it is down: the
 * broker keeps them until it comes back.
 */
@Configuration
public class NotificationsConfig {

    /** Single source of time, so tests can move the clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TopicExchange eventsExchange(@Value("${notifications.events.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    /** Messages that cannot be processed (after the retries) are parked here instead of being lost. */
    @Bean
    public Queue deadLetterQueue(@Value("${notifications.dead-letter-queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    public Queue notificationsQueue(@Value("${notifications.queue}") String name,
                                    @Value("${notifications.dead-letter-queue}") String deadLetterQueue) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")                    // the default exchange routes by queue name
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
    }

    @Bean
    public Binding confirmedBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(NotificationType.BOOKING_CONFIRMED.routingKey());
    }

    @Bean
    public Binding cancelledBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(NotificationType.BOOKING_CANCELLED.routingKey());
    }

    @Bean
    public Binding sagaFailedBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(NotificationType.SAGA_FAILED.routingKey());
    }
}
