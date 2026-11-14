package ma.mobility.railbridge;

import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;
import org.omg.CORBA.ORB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Lot 0 : appel CORBA minimal (search) vers l'opérateur ferroviaire. Jetable. */
public final class SmokeTest {

    public static void main(String[] args) throws Exception {
        String iorPath = args.length > 0 ? args[0] : "/work/ior/RailBooking.ior";

        Properties props = new Properties();
        props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
        props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        ORB orb = ORB.init(new String[0], props);

        String ior = Files.readString(Path.of(iorPath)).trim();
        RailBooking rail = RailBookingHelper.narrow(orb.string_to_object(ior));

        Trip[] trips = rail.search("Casablanca", "Rabat", "2026-11-15");
        System.out.println("Trajets trouvés : " + trips.length);
        for (Trip t : trips) {
            System.out.printf("  %s  %s -> %s  %s  %d centimes  %d places%n",
                    t.tripId, t.origin, t.destination, t.departure, t.priceMinor, t.seatsAvailable);
        }
        orb.shutdown(true);
        System.exit(0);
    }

    private SmokeTest() {}
}