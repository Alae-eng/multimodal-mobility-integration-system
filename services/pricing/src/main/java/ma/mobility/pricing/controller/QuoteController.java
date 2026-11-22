package ma.mobility.pricing.controller;

import jakarta.validation.Valid;
import ma.mobility.pricing.dto.QuoteRequest;
import ma.mobility.pricing.dto.QuoteResponse;
import ma.mobility.pricing.service.PricingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quotes")
public class QuoteController {

    private final PricingService service;

    public QuoteController(PricingService service) {
        this.service = service;
    }

    /**
     * POST /v1/quotes
     * Body: {"offers":[{"offerId":"RAIL-CAS-RAB-0730","operator":"RAIL","priceMinor":8000}, ...],"travelers":2}
     * Optional header: Idempotency-Key. The offers are the canonical Offers returned by the
     * operators; extra fields (origin, departure...) are ignored.
     */
    @PostMapping
    public ResponseEntity<QuoteResponse> quote(
            @Valid @RequestBody QuoteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QuoteResponse.from(service.quote(request, idempotencyKey)));
    }

    /** GET /v1/quotes/{quoteId} */
    @GetMapping("/{quoteId}")
    public QuoteResponse get(@PathVariable("quoteId") String quoteId) {
        return QuoteResponse.from(service.get(quoteId));
    }
}
