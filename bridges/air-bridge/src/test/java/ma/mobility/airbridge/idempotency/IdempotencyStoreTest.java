package ma.mobility.airbridge.idempotency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyStoreTest {

    @Test
    void sameKeyUsedConcurrently_createsOnlyOneHold() throws Exception {
        IdempotencyStore store = new IdempotencyStore();
        AtomicInteger creations = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                results.add(pool.submit(() -> store.getOrCreate("key-1", () -> {
                    creations.incrementAndGet();
                    pause(100);
                    return "HOLD-1";
                })));
            }
            for (Future<String> r : results) {
                assertEquals("HOLD-1", r.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, creations.get(), "le partenaire ne doit être appelé qu'une fois");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedCreation_isNotRemembered() {
        IdempotencyStore store = new IdempotencyStore();

        assertThrows(IllegalStateException.class,
                () -> store.getOrCreate("key-2", () -> { throw new IllegalStateException("refus"); }));

        assertEquals("HOLD-2", store.getOrCreate("key-2", () -> "HOLD-2"),
                "un hold refusé n'est pas mémorisé : le rejeu est réévalué");
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}