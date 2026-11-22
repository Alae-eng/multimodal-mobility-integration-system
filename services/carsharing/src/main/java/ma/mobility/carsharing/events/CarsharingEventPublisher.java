package ma.mobility.carsharing.events;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import ma.mobility.carsharing.model.Hold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes carsharing events to RabbitMQ after the DB transaction commits (never for a rolled
 * back change). The W3C trace context travels as a message header (RabbitTemplate observation)
 * and the trace id is repeated in the payload.
 *
 * Resilience: a failed publish is retried once, and a circuit breaker stops hammering a dead
 * broker so requests stay fast. Whatever happens, the HTTP answer is unchanged: the booking is
 * already committed, losing an event is logged, not turned into a failure.
 */
@Component
public class CarsharingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CarsharingEventPublisher.class);

    private final RabbitTemplate rabbit;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;
    private final String exchange;
    private final CircuitBreaker breaker;
    private final Retry retry;

    public CarsharingEventPublisher(RabbitTemplate rabbit,
                                    ObjectProvider<Tracer> tracer,
                                    Clock clock,
                                    @Value("${carsharing.events.exchange}") String exchange) {
        this.rabbit = rabbit;
        this.tracer = tracer;
        this.clock = clock;
        this.exchange = exchange;
        this.breaker = CircuitBreaker.of("rabbitmq", CircuitBreakerConfig.custom()
                .slidingWindowSize(6)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .build());
        this.retry = Retry.of("rabbitmq", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(200))
                .ignoreExceptions(CallNotPermittedException.class)
                .build());
    }

    public void publishAfterCommit(CarsharingEvent event, Hold hold) {
        byte[] body = toJson(event, hold, currentTraceId()).getBytes(StandardCharsets.UTF_8);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event, hold.getId(), body);
                }
            });
        } else {
            send(event, hold.getId(), body);
        }
    }

    /** Back to a closed circuit (used by tests and operators after a broker incident). */
    public void resetCircuitBreaker() {
        breaker.reset();
    }

    private void send(CarsharingEvent event, String holdId, byte[] body) {
        try {
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding("UTF-8");
            props.setType(event.eventName());
            Message message = new Message(body, props);

            Runnable publish = () -> rabbit.send(exchange, event.routingKey(), message);
            Retry.decorateRunnable(retry, CircuitBreaker.decorateRunnable(breaker, publish)).run();
        } catch (CallNotPermittedException e) {
            log.error("Event {} for hold {} not published: broker circuit is open", event.eventName(), holdId);
        } catch (RuntimeException e) {
            log.error("Event {} for hold {} not published: {}", event.eventName(), holdId, e.getMessage());
        }
    }

    private String currentTraceId() {
        Tracer t = tracer.getIfAvailable();
        Span span = (t == null) ? null : t.currentSpan();
        return (span == null) ? null : span.context().traceId();
    }

    private String toJson(CarsharingEvent event, Hold hold, String traceId) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "event", event.eventName()).append(',');
        field(sb, "holdId", hold.getId()).append(',');
        if (hold.getBookingRef() != null) {
            field(sb, "bookingRef", hold.getBookingRef()).append(',');
        }
        field(sb, "vehicleId", hold.getVehicleId()).append(',');
        field(sb, "operator", "CARSHARING").append(',');
        field(sb, "occurredAt", clock.instant().toString());
        if (traceId != null) {
            sb.append(',');
            field(sb, "traceId", traceId);
        }
        return sb.append('}').toString();
    }

    private StringBuilder field(StringBuilder sb, String name, String value) {
        return sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
