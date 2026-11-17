package ma.mobility.railbridge.api;

import ma.mobility.railbridge.corba.RailClient;
import ma.mobility.railbridge.idempotency.IdempotencyStore;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** hold / confirm / cancel (contracts/operator-port.md, section 2). */
@RestController
@RequestMapping("/v1")
public class BookingController {

    private final RailClient rail;
    private final IdempotencyStore idempotency;

    public BookingController(RailClient rail, IdempotencyStore idempotency) {
        this.rail = rail;
        this.idempotency = idempotency;
    }

    @PostMapping("/holds")
    public ResponseEntity<HoldResponse> hold(@RequestBody HoldRequest req,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (req.offerId() == null || req.offerId().isBlank()) {
            throw new IllegalArgumentException("offerId is required");
        }
        String holdId = (idempotencyKey == null || idempotencyKey.isBlank())
                ? rail.hold(req.offerId(), req.seats())
                : idempotency.getOrCreate(idempotencyKey, () -> rail.hold(req.offerId(), req.seats()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new HoldResponse(holdId));
    }

    @PostMapping("/holds/{holdId}/confirm")
    public ConfirmResponse confirm(@PathVariable String holdId) {
        return new ConfirmResponse(rail.confirm(holdId));
    }

    /** Compensation : toujours 204 (même référence inconnue), sauf opérateur injoignable (503). */
    @DeleteMapping("/references/{reference}")
    public ResponseEntity<Void> cancel(@PathVariable String reference) {
        rail.cancel(reference);
        return ResponseEntity.noContent().build();
    }
}