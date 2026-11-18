package ma.mobility.railbridge.corba;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import mobility.rail.NotAvailable;
import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;
import org.omg.CORBA.ORB;
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
        return traced("search", () -> List.of(stub().search(origin, destination, date)));
    }

    /** @return holdId */
    public String hold(String tripId, int seats) {
        return traced("hold", () -> {
            try {
                return stub().hold(tripId, seats);
            } catch (NotAvailable e) {
                throw new OperatorRefusedException(e.reason);
            }
        });
    }

    /** @return bookingRef */
    public String confirm(String holdId) {
        return traced("confirm", () -> {
            try {
                return stub().confirm(holdId);
            } catch (NotAvailable e) {
                throw new OperatorRefusedException(e.reason);
            }
        });
    }

    /** Idempotent côté legacy : une référence inconnue n'est pas une erreur. */
    public void cancel(String reference) {
        traced("cancel", () -> {
            stub().cancel(reference);
            return null;
        });
    }

    /** Initialisation paresseuse : le bridge peut démarrer avant que l'IOR existe. */
    private synchronized RailBooking stub() {
        if (rail == null) {
            try {
                Properties props = new Properties();
                props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
                props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
                // Délais (ms) : sans eux, un serveur mort bloque les threads indéfiniment
                props.setProperty("jacorb.connection.client.connect_timeout", "3000");
                props.setProperty("jacorb.connection.client.pending_reply_timeout", "5000");
                orb = ORB.init(new String[0], props);
                String ior = Files.readString(Path.of(iorFile)).trim();
                rail = RailBookingHelper.narrow(orb.string_to_object(ior));
            } catch (IOException e) {
                throw new RailUnavailableException("Cannot read IOR file " + iorFile, e);
            }
        }
        return rail;
    }
}