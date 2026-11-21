package ma.mobility.carsharing.config;

import java.util.List;
import ma.mobility.carsharing.model.Vehicle;
import ma.mobility.carsharing.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Inserts the demo vehicles at startup, only if the table is empty. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final VehicleRepository vehicleRepository;

    public DataSeeder(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public void run(String... args) {
        if (vehicleRepository.count() > 0) {
            return; // already seeded, so restarts never duplicate data
        }

        List<Vehicle> vehicles = List.of(
                new Vehicle("CAR-RAB-001", "Rabat",       "Dacia Spring (electric)", 15000),
                new Vehicle("CAR-RAB-002", "Rabat",       "Renault Clio",            18000),
                new Vehicle("CAR-RAB-003", "Rabat",       "Peugeot 208",             20000),
                new Vehicle("CAR-CAS-001", "Casablanca",  "Dacia Sandero",           16000),
                new Vehicle("CAR-CAS-002", "Casablanca",  "Renault Captur",          22000),
                new Vehicle("CAR-MAR-001", "Marrakech",   "Dacia Duster",            25000),
                new Vehicle("CAR-PAR-001", "Paris",       "Renault Zoe (electric)",  30000),
                new Vehicle("CAR-PAR-002", "Paris",       "Peugeot e-208",           32000)
        );

        vehicleRepository.saveAll(vehicles);
        log.info("DataSeeder: {} demo vehicles inserted", vehicles.size());
    }
}