package ma.mobility.pricing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import ma.mobility.pricing.domain.Operator;

/** One priced leg of a quote. */
@Embeddable
public class QuoteLine {

    @Column(nullable = false, length = 100)
    private String offerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operator operator;

    @Column(nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amountMinor;

    protected QuoteLine() {
        // required by JPA
    }

    public QuoteLine(String offerId, Operator operator, long unitPriceMinor, int quantity, long amountMinor) {
        this.offerId = offerId;
        this.operator = operator;
        this.unitPriceMinor = unitPriceMinor;
        this.quantity = quantity;
        this.amountMinor = amountMinor;
    }

    public String getOfferId() { return offerId; }
    public Operator getOperator() { return operator; }
    public long getUnitPriceMinor() { return unitPriceMinor; }
    public int getQuantity() { return quantity; }
    public long getAmountMinor() { return amountMinor; }
}
