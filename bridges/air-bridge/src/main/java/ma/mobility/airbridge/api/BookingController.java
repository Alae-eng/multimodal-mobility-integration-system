package ma.mobility.airbridge.api;

import ma.mobility.airbridge.soap.AirClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** hold / confirm / cancel (contracts/operator-port.md, section 2). */
@RestController
@RequestMapping("/v1")
public class BookingController {

    private final AirClient air;

    public BookingController(AirClient air) {
        this.air = air;
    }

    @PostMapping("/holds")
    public ResponseEntity<HoldResponse> hold(@RequestBody HoldRequest req) {
        if (req.offerId() == null || req.offerId().isBlank()) {
            throw new IllegalArgumentException("offerId is required");
        }
        String holdId = air.holdFlight(req.offerId(), req.seats());
        return ResponseEntity.status(HttpStatus.CREATED).body(new HoldResponse(holdId));
    }

    @PostMapping("/holds/{holdId}/confirm")
    public ConfirmResponse confirm(@PathVariable String holdId) {
        return new ConfirmResponse(air.confirmFlight(holdId));
    }

    /** Compensation : toujours 204 (même référence inconnue), sauf partenaire injoignable (503). */
    @DeleteMapping("/references/{reference}")
    public ResponseEntity<Void> cancel(@PathVariable String reference) {
        air.cancelFlight(reference);
        return ResponseEntity.noContent().build();
    }
}