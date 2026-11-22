package ma.mobility.carsharing.controller;

import jakarta.validation.Valid;
import ma.mobility.carsharing.dto.ConfirmResponse;
import ma.mobility.carsharing.dto.HoldRequest;
import ma.mobility.carsharing.dto.HoldResponse;
import ma.mobility.carsharing.model.Hold;
import ma.mobility.carsharing.service.CarsharingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/holds")
public class HoldController {

    private final CarsharingService service;

    public HoldController(CarsharingService service) {
        this.service = service;
    }

    /** POST /v1/holds   body: {"offerId":"CAR-RAB-001","seats":1}   optional header: Idempotency-Key */
    @PostMapping
    public ResponseEntity<HoldResponse> hold(
            @Valid @RequestBody HoldRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Hold hold = service.hold(request.offerId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(new HoldResponse(hold.getId()));
    }

    /** POST /v1/holds/{holdId}/confirm */
    @PostMapping("/{holdId}/confirm")
    public ConfirmResponse confirm(@PathVariable("holdId") String holdId) {
        return new ConfirmResponse(service.confirm(holdId));
    }
}