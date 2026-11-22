package ma.mobility.bookings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One operator's part of the trip. The reference is what the operator returned: the holdId while
 * the booking is PENDING, the bookingRef once the operator confirmed. It is exactly what the saga
 * must send to DELETE /v1/references/{reference} to compensate.
 */
@Embeddable
public class BookingLeg {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operator operator;

    @Column(nullable = false, length = 100)
    private String offerId;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false)
    private long priceMinor;

    protected BookingLeg() {
        // required by JPA
    }

    public BookingLeg(Operator operator, String offerId, String reference, long priceMinor) {
        this.operator = operator;
        this.offerId = offerId;
        this.reference = reference;
        this.priceMinor = priceMinor;
    }

    public Operator getOperator() { return operator; }
    public String getOfferId() { return offerId; }
    public String getReference() { return reference; }
    public long getPriceMinor() { return priceMinor; }
}
