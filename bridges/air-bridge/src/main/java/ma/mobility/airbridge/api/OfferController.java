package ma.mobility.airbridge.api;

import ma.mobility.airbridge.mapping.FlightMapper;
import ma.mobility.airbridge.soap.AirClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class OfferController {

    private final AirClient air;

    public OfferController(AirClient air) {
        this.air = air;
    }

    /** search : renvoie [] (jamais 404) si aucun vol. */
    @GetMapping("/offers")
    public List<Offer> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return air.searchFlights(origin, destination, date.toString())
                .stream()
                .map(FlightMapper::toOffer)
                .toList();
    }
}