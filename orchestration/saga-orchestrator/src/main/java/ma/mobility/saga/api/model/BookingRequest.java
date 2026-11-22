package ma.mobility.saga.api.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BookingRequest(
        @NotEmpty List<String> offerIds,
        @Min(1) int seats) {
}
