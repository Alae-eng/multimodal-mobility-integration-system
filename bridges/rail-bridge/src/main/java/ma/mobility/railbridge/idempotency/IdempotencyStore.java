package ma.mobility.railbridge.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Mémorise Idempotency-Key -> holdId, car le hold CORBA legacy n'est pas idempotent.
 *
 * Limites assumées :
 *  - stockage en mémoire : perdu si le bridge redémarre ;
 *  - un hold refusé (409) n'est pas mémorisé : le rejeu est donc réévalué ;
 *  - la clé n'est pas liée au contenu de la requête.
 */
@Component
public class IdempotencyStore {

    private record Entry(String holdId, Instant createdAt) {}

    private static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Renvoie le holdId déjà associé à la clé, sinon exécute {@code creator} UNE seule fois.
     * computeIfAbsent est atomique par clé : deux requêtes simultanées avec la même clé
     * ne créent jamais deux holds. Si {@code creator} lève une exception, rien n'est mémorisé.
     */
    public String getOrCreate(String key, Supplier<String> creator) {
        purgeExpired();
        return entries.computeIfAbsent(key, k -> new Entry(creator.get(), Instant.now())).holdId();
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        entries.values().removeIf(e -> e.createdAt().isBefore(cutoff));
    }
}