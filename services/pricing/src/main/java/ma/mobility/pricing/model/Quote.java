package ma.mobility.pricing.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A computed price, kept so that it can be fetched again and replayed with the same key. */
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @Column(length = 50)
    private String id;

    /** From the Idempotency-Key header. Unique: the same key can never create two quotes. */
    @Column(unique = true, length = 100)
    private String idempotencyKey;

    /** Fingerprint of the request, to detect a key reused with different offers. */
    @Column(length = 64)
    private String requestHash;

    @Column(nullable = false)
    private int travelers;

    @Column(nullable = false)
    private long subtotalMinor;

    @Column(nullable = false)
    private int discountBps;

    @Column(nullable = false)
    private long discountMinor;

    @Column(nullable = false)
    private long totalMinor;

    @Column(nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quote_lines", joinColumns = @JoinColumn(name = "quote_id"))
    @OrderColumn(name = "position")
    private List<QuoteLine> lines = new ArrayList<>();

    protected Quote() {
        // required by JPA
    }

    public Quote(String idempotencyKey, String requestHash, int travelers, long subtotalMinor,
                 int discountBps, long discountMinor, long totalMinor, Instant createdAt,
                 List<QuoteLine> lines) {
        this.id = "QUOTE-" + UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.travelers = travelers;
        this.subtotalMinor = subtotalMinor;
        this.discountBps = discountBps;
        this.discountMinor = discountMinor;
        this.totalMinor = totalMinor;
        this.createdAt = createdAt;
        this.lines = new ArrayList<>(lines);
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public int getTravelers() { return travelers; }
    public long getSubtotalMinor() { return subtotalMinor; }
    public int getDiscountBps() { return discountBps; }
    public long getDiscountMinor() { return discountMinor; }
    public long getTotalMinor() { return totalMinor; }
    public Instant getCreatedAt() { return createdAt; }
    public List<QuoteLine> getLines() { return List.copyOf(lines); }
}
