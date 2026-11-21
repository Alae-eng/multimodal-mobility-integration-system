package ma.mobility.carsharing.controller;

import ma.mobility.carsharing.service.CarsharingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/references")
public class ReferenceController {

    private final CarsharingService service;

    public ReferenceController(CarsharingService service) {
        this.service = service;
    }

    /** DELETE /v1/references/{reference}: holdId or bookingRef. Always 204, even if unknown or already cancelled. */
    @DeleteMapping("/{reference}")
    public ResponseEntity<Void> cancel(@PathVariable("reference") String reference) {
        service.cancel(reference);
        return ResponseEntity.noContent().build();
    }
}