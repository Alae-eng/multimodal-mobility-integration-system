package ma.mobility.bookings.controller;

import jakarta.validation.Valid;
import java.util.List;
import ma.mobility.bookings.dto.BookingResponse;
import ma.mobility.bookings.dto.ConfirmBookingRequest;
import ma.mobility.bookings.dto.CreateBookingRequest;
import ma.mobility.bookings.dto.FailBookingRequest;
import ma.mobility.bookings.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    /**
     * POST /v1/bookings: opens a PENDING travel file with the holdId of each operator.
     * Optional header: Idempotency-Key.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookingResponse.from(service.create(request, idempotencyKey)));
    }

    /** POST /v1/bookings/{id}/confirm: PENDING -> CONFIRMED, with the bookingRef of each operator. */
    @PostMapping("/{bookingId}/confirm")
    public BookingResponse confirm(@PathVariable("bookingId") String bookingId,
                                   @Valid @RequestBody ConfirmBookingRequest request) {
        return BookingResponse.from(service.confirm(bookingId, request));
    }

    /** POST /v1/bookings/{id}/fail: PENDING -> FAILED, the saga gave up. */
    @PostMapping("/{bookingId}/fail")
    public BookingResponse fail(@PathVariable("bookingId") String bookingId,
                                @Valid @RequestBody FailBookingRequest request) {
        return BookingResponse.from(service.fail(bookingId, request.reason()));
    }

    /** DELETE /v1/bookings/{id}: always 204, even for an unknown or already cancelled file (idempotent). */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable("bookingId") String bookingId) {
        service.cancel(bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable("bookingId") String bookingId) {
        return BookingResponse.from(service.get(bookingId));
    }

    /** GET /v1/bookings?travelerId=... : the traveler's latest travel files (most recent first). */
    @GetMapping
    public List<BookingResponse> list(@RequestParam("travelerId") String travelerId) {
        return service.listForTraveler(travelerId).stream().map(BookingResponse::from).toList();
    }
}
