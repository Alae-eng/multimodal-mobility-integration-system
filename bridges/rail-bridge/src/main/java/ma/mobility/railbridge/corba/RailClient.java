package ma.mobility.railbridge.corba;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import mobility.rail.NotAvailable;
import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/** Seul endroit qui parle CORBA à l'opérateur ferroviaire. */
@Component
public class RailClient {

    private static final Logger log = LoggerFactory.getLogger(RailClient.class);

    /** Appel CORBA pouvant lever le refus métier NotAvailable. */
    @FunctionalInterface
    private interface CorbaCall<T> {
        T apply(RailBooking rail) throws NotAvailable;
    }

    private final String iorFile;
    private final ObservationRegistry observations;
    private ORB orb;
    private RailBooking rail;

    public RailClient(@Value("${rail.ior-file}") String iorFile, ObservationRegistry observations) {
        this.iorFile = iorFile;
        this.observations = observations;
    }

    /** Un appel CORBA = un span dans la trace (le legacy, lui, ne propage rien). */
    private <T> T traced(String operation, Supplier<T> call) {
        log.info("CORBA call: {}", operation);
        return Observation.createNotStarted("corba.rail." + operation, observations)
                .lowCardinalityKeyValue("rpc.system", "corba")
                .lowCardinalityKeyValue("rpc.method", operation)
                .observe(call);
    }

    public List<Trip> search(String origin, String destination, String date) {
        return traced("search", () -> invoke(true, r -> List.of(r.search(origin, destination, date))));
    }

    /** @return holdId. Non idempotent : rejoué seulement si la requête n'a pas été exécutée. */
    public String hold(String tripId, int seats) {
        return traced("hold", () -> invoke(false, r -> r.hold(tripId, seats)));
    }

    /** @return bookingRef. Non idempotent (le legacy consomme le hold). */
    public String confirm(String holdId) {
        return traced("confirm", () -> invoke(false, r -> r.confirm(holdId)));
    }

    /** Idempotent côté legacy : une référence inconnue n'est pas une erreur. */
    public void cancel(String reference) {
        traced("cancel", () -> invoke(true, r -> {
            r.cancel(reference);
            return null;
        }));
    }

    /**
     * Exécute l'appel ; sur erreur technique CORBA, oublie la référence (l'IOR a pu changer
     * si le serveur a redémarré) puis réessaie UNE fois si c'est sans danger.
     */
    private <T> T invoke(boolean idempotent, CorbaCall<T> call) {
        try {
            return attempt(call);
        } catch (SystemException first) {
            reset();
            boolean safeToRetry = idempotent || first.completed == CompletionStatus.COMPLETED_NO;
            if (!safeToRetry) {
                log.warn("CORBA {} (completed={}) : pas de nouvel essai, risque de doublon",
                        first.getClass().getSimpleName(), first.completed);
                throw first;
            }
            log.warn("CORBA {} : nouvel essai après relecture de l'IOR", first.getClass().getSimpleName());
            try {
                return attempt(call);
            } catch (SystemException second) {
                reset();
                throw second;
            }
        }
    }

    private <T> T attempt(CorbaCall<T> call) {
        try {
            return call.apply(stub());
        } catch (NotAvailable e) {
            throw new OperatorRefusedException(e.reason);
        }
    }

    /** Oublie la référence : le prochain appel relira l'IOR depuis le fichier. */
    private synchronized void reset() {
        rail = null;
    }

    /** Initialisation paresseuse : le bridge peut démarrer avant que l'IOR existe. */
    private synchronized RailBooking stub() {
        if (rail == null) {
            try {
                if (orb == null) {
                    Properties props = new Properties();
                    props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
                    props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
                    // Délais (ms) : sans eux, un serveur mort bloque les threads indéfiniment
                    props.setProperty("jacorb.connection.client.connect_timeout", "3000");
                    props.setProperty("jacorb.connection.client.pending_reply_timeout", "5000");
                    orb = ORB.init(new String[0], props);
                }
                String ior = Files.readString(Path.of(iorFile)).trim();
                rail = RailBookingHelper.narrow(orb.string_to_object(ior));
            } catch (IOException e) {
                throw new RailUnavailableException("Cannot read IOR file " + iorFile, e);
            }
        }
        return rail;
    }
}