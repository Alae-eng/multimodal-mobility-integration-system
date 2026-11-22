package ma.mobility.carsharing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import ma.mobility.carsharing.dto.OfferResponse;
import ma.mobility.carsharing.events.CarsharingEvent;
import ma.mobility.carsharing.events.CarsharingEventPublisher;
import ma.mobility.carsharing.exception.NotAvailableException;
import ma.mobility.carsharing.model.Hold;
import ma.mobility.carsharing.model.HoldStatus;
import ma.mobility.carsharing.model.Vehicle;
import ma.mobility.carsharing.repository.HoldRepository;
import ma.mobility.carsharing.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carsharing operator: search / hold / confirm / cancel (contracts/operator-port.md).
 *
 * Consistency rules (local ACID transaction per call, the saga provides the federation-wide part):
 * - hold: the Vehicle row is versioned, so two concurrent holds on one vehicle cannot both win;
 * - confirm / cancel / expiry lock the Hold row (SELECT ... FOR UPDATE), so replays and races
 *   see the result of the first call: same bookingRef, a single transition, no lost update.
 */
@Service
public class CarsharingService {

    private static final Logger log = LoggerFactory.getLogger(CarsharingService.class);

    private final VehicleRepository vehicleRepository;
    private final HoldRepository holdRepository;
    private final CarsharingEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    /** Same 10 minutes as the legacy systems; configurable so tests need not wait. */
    private final Duration holdTtl;

    public CarsharingService(VehicleRepository vehicleRepository,
                             HoldRepository holdRepository,
                             CarsharingEventPublisher events,
                             Clock clock,
                             PlatformTransactionManager txManager,
                             @Value("${carsharing.hold-ttl:PT10M}") Duration holdTtl) {
        if (holdTtl.compareTo(Duration.ofSeconds(1)) < 0 || holdTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("carsharing.hold-ttl must be between 1 second and 24 hours");
        }
        this.vehicleRepository = vehicleRepository;
        this.holdRepository = holdRepository;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.holdTtl = holdTtl;
    }

    // ------------------------------------------------------------------ search

    /**
     * A car is useful where the traveler arrives, so we search by destination city
     * ("origin" is accepted for contract symmetry but does not filter).
     * Returns an empty list (never an error) when nothing matches.
     */
    @Transactional(readOnly = true)
    public List<OfferResponse> search(String origin, String destination, LocalDate date) {
        return vehicleRepository.findByCityIgnoreCaseAndAvailableTrue(destination).stream()
                .map(v -> toOffer(v, date))
                .toList();
    }

    private OfferResponse toOffer(Vehicle v, LocalDate date) {
        // A rental is available during the day of the search; one offer = one vehicle.
        return new OfferResponse(
                v.getId(),
                "CARSHARING",
                v.getCity(),
                v.getCity(),
                date + "T08:00:00",
                date + "T20:00:00",
                v.getPricePerTripMinor(),
                1);
    }

    // -------------------------------------------------------------------- hold

    /**
     * Temporary reservation. Same idempotencyKey -> same hold, nothing is held twice.
     * (A vehicle is one unit, so "seats" is not used.)
     */
    @Transactional
    public Hold hold(String offerId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Hold> existing = holdRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                // Contract: the same key always returns the same holdId.
                return existing.get();
            }
        }

        Vehicle vehicle = vehicleRepository.findById(offerId)
                .orElseThrow(() -> new NotAvailableException("Unknown offer: " + offerId));

        if (!vehicle.isAvailable()) {
            throw new NotAvailableException("Vehicle " + offerId + " is no longer available");
        }

        try {
            vehicle.setAvailable(false);
            vehicleRepository.saveAndFlush(vehicle); // @Version check happens here
        } catch (OptimisticLockingFailureException e) {
            throw new NotAvailableException("Vehicle " + offerId + " was just taken");
        }

        return holdRepository.save(new Hold(offerId, blankToNull(idempotencyKey), clock.instant()));
    }

    // ----------------------------------------------------------------- confirm

    /**
     * HELD -> CONFIRMED. Calling it again on a confirmed hold returns the same bookingRef.
     * noRollbackFor: when the hold is found expired we still want the "EXPIRED" status
     * and the vehicle release to be saved, even though we then throw.
     */
    @Transactional(noRollbackFor = NotAvailableException.class)
    public String confirm(String holdId) {
        Hold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new NotAvailableException("Unknown hold: " + holdId));

        if (hold.getStatus() == HoldStatus.CONFIRMED) {
            return hold.getBookingRef(); // idempotent replay, no second event
        }

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new NotAvailableException("Hold " + holdId + " is " + hold.getStatus());
        }

        if (isExpired(hold, clock.instant())) {
            expire(hold);
            throw new NotAvailableException("Hold " + holdId + " has expired");
        }

        hold.confirm();
        holdRepository.save(hold);
        events.publishAfterCommit(CarsharingEvent.RESERVATION_CONFIRMED, hold);
        return hold.getBookingRef();
    }

    // ------------------------------------------------------------------ cancel

    /**
     * Compensation. The reference can be a holdId or a bookingRef.
     * Idempotent: unknown or already cancelled references do nothing and never fail.
     */
    @Transactional
    public void cancel(String reference) {
        Optional<Hold> found = holdRepository.findByIdForUpdate(reference);
        if (found.isEmpty()) {
            found = holdRepository.findByBookingRefForUpdate(reference);
        }
        if (found.isEmpty() || !found.get().isActive()) {
            return; // nothing to do, still a success (204)
        }

        Hold hold = found.get();
        boolean wasConfirmed = hold.getStatus() == HoldStatus.CONFIRMED;
        hold.cancel();
        holdRepository.save(hold);
        releaseVehicle(hold.getVehicleId());

        // Only a firm reservation is announced; releasing an unconfirmed hold is not an event.
        if (wasConfirmed) {
            events.publishAfterCommit(CarsharingEvent.RESERVATION_CANCELLED, hold);
        }
    }

    // -------------------------------------------------------------- expiration

    /**
     * Called by HoldExpirationJob. Each hold is released in its own transaction, so one failure
     * does not block the others and the failed one is simply retried on the next run.
     * Returns how many holds were released.
     */
    public int releaseExpiredHolds() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(holdTtl);
        int released = 0;
        for (Hold candidate : holdRepository.findByStatusAndCreatedAtBefore(HoldStatus.HELD, cutoff)) {
            try {
                Boolean done = tx.execute(status -> expireIfStillHeld(candidate.getId(), now));
                if (Boolean.TRUE.equals(done)) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.error("Could not release expired hold {}, will retry on the next run: {}",
                        candidate.getId(), e.getMessage());
            }
        }
        return released;
    }

    private boolean expireIfStillHeld(String holdId, Instant now) {
        // Re-read under lock: a confirm or cancel may have won the race since the candidate query.
        Optional<Hold> locked = holdRepository.findByIdForUpdate(holdId);
        if (locked.isEmpty() || locked.get().getStatus() != HoldStatus.HELD || !isExpired(locked.get(), now)) {
            return false;
        }
        expire(locked.get());
        return true;
    }

    // ----------------------------------------------------------------- helpers

    private boolean isExpired(Hold hold, Instant now) {
        return hold.getCreatedAt().plus(holdTtl).isBefore(now);
    }

    private void expire(Hold hold) {
        hold.expire();
        holdRepository.save(hold);
        releaseVehicle(hold.getVehicleId());
    }

    private void releaseVehicle(String vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(v -> {
            v.setAvailable(true);
            vehicleRepository.save(v);
        });
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
