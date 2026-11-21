package ma.mobility.carsharing.controller;

import java.time.LocalDate;
import java.util.List;
import ma.mobility.carsharing.dto.OfferResponse;
import ma.mobility.carsharing.service.CarsharingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/offers")
public class OfferController {

    private final CarsharingService service;

    public OfferController(CarsharingService service) {
        this.service = service;
    }

    /** GET /v1/offers?origin=Casablanca&destination=Rabat&date=2026-11-15 */
    @GetMapping
    public List<OfferResponse> search(
            @RequestParam("origin") String origin,
            @RequestParam("destination") String destination,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.search(origin, destination, date);
    }
}