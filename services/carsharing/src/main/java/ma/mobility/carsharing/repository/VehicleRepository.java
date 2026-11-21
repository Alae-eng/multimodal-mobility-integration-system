package ma.mobility.carsharing.repository;

import java.util.List;
import ma.mobility.carsharing.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    /** Search: free vehicles in a city (case-insensitive). */
    List<Vehicle> findByCityIgnoreCaseAndAvailableTrue(String city);
}