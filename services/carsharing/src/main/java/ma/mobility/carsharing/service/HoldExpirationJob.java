package ma.mobility.carsharing.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically releases holds older than the hold TTL so the vehicles become available again. */
@Component
public class HoldExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirationJob.class);

    private final CarsharingService service;
    private final ObjectProvider<Tracer> tracer;

    public HoldExpirationJob(CarsharingService service, ObjectProvider<Tracer> tracer) {
        this.service = service;
        this.tracer = tracer;
    }

    @Scheduled(fixedDelayString = "${carsharing.expiration.interval:PT15S}",
               initialDelayString = "${carsharing.expiration.initial-delay:PT30S}")
    public void run() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            runOnce();
            return;
        }
        // Each run is its own trace, so its log lines can be found in the trace viewer.
        Span span = t.nextSpan().name("hold-expiration").start();
        try (Tracer.SpanInScope ignored = t.withSpan(span)) {
            runOnce();
        } finally {
            span.end();
        }
    }

    private void runOnce() {
        int released = service.releaseExpiredHolds();
        if (released > 0) {
            log.info("HoldExpirationJob: {} expired hold(s) released", released);
        }
    }
}
