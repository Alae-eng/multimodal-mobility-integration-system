package ma.mobility.railbridge.api;

import ma.mobility.railbridge.corba.RailClient;
import ma.mobility.railbridge.mapping.TripMapper;
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

    private final RailClient rail;

    public OfferController(RailClient rail) {
        this.rail = rail;
    }

    /** search : renvoie [] (jamais 404) si aucun trajet. */
    @GetMapping("/offers")
    public List<Offer> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return rail.search(origin, destination, date.toString())
                .stream()
                .map(TripMapper::toOffer)
                .toList();
    }
}