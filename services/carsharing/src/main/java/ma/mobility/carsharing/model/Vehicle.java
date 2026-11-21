package ma.mobility.carsharing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    /** Business id, also used as offerId in the canonical Offer. Example: "CAR-RAB-001". */
    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String model;

    /** Price of one trip, in centimes of MAD (8000 = 80.00 MAD). Never decimals. */
    @Column(nullable = false)
    private long pricePerTripMinor;

    /** false while the vehicle is held or booked. */
    @Column(nullable = false)
    private boolean available = true;

    /** Optimistic locking: if two holds hit the same vehicle at once, the second one fails. */
    @Version
    private Long version;

    protected Vehicle() {
        // required by JPA
    }

    public Vehicle(String id, String city, String model, long pricePerTripMinor) {
        this.id = id;
        this.city = city;
        this.model = model;
        this.pricePerTripMinor = pricePerTripMinor;
        this.available = true;
    }

    public String getId() { return id; }
    public String getCity() { return city; }
    public String getModel() { return model; }
    public long getPricePerTripMinor() { return pricePerTripMinor; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}