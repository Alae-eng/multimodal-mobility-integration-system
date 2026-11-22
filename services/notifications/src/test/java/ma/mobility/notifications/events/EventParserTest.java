package ma.mobility.notifications.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import ma.mobility.notifications.model.NotificationType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The parser alone, fed with the exact payload the bookings service really publishes. */
class EventParserTest {

    private final EventParser parser = new EventParser(JsonMapper.builder().build());

    /** A real payload, copied from bookings' outbox table. */
    private static final String CONFIRMED = """
            {"eventId":"EVT-2654b510-b50e-4490-96eb-05c7e48ef40a","event":"BookingConfirmed",
             "bookingId":"BK-57084650-41f6-4824-a494-823984c03e80","travelerId":"t1","status":"CONFIRMED",
             "totalMinor":8000,"legs":[{"operator":"RAIL","offerId":"R1","reference":"RBK1","priceMinor":8000}],
             "occurredAt":"2026-11-19T02:20:04.343278526Z"}""";

    private IncomingEvent parse(String key, String body) {
        return parser.parse(key, body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aRealBookingsPayloadIsUnderstood() {
        IncomingEvent e = parse("booking.confirmed", CONFIRMED);

        assertThat(e.type()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(e.eventId()).isEqualTo("EVT-2654b510-b50e-4490-96eb-05c7e48ef40a");
        assertThat(e.bookingId()).isEqualTo("BK-57084650-41f6-4824-a494-823984c03e80");
        assertThat(e.travelerId()).isEqualTo("t1");
        assertThat(e.totalMinor()).isEqualTo(8000L);
        assertThat(e.legs()).containsExactly(new IncomingEvent.Leg("RAIL", "RBK1"));
    }

    @Test
    void allThreeEventsMapToTheirType() {
        assertThat(parse("booking.cancelled", CONFIRMED.replace("BookingConfirmed", "BookingCancelled")).type())
                .isEqualTo(NotificationType.BOOKING_CANCELLED);
        assertThat(parse("saga.failed", CONFIRMED.replace("BookingConfirmed", "SagaFailed")).type())
                .isEqualTo(NotificationType.SAGA_FAILED);
    }

    @Test
    void unknownFieldsAreIgnoredSoTheProducerCanAddSome() {
        IncomingEvent e = parse("booking.confirmed", CONFIRMED.replace("\"status\"", "\"somethingNew\":[1,2],\"status\""));

        assertThat(e.bookingId()).startsWith("BK-");
    }

    @Test
    void legsAndTotalAreOptional() {
        IncomingEvent e = parse("saga.failed", """
                {"eventId":"E1","event":"SagaFailed","bookingId":"BK-1","travelerId":"t1","reason":"no seats"}""");

        assertThat(e.legs()).isEmpty();
        assertThat(e.totalMinor()).isNull();
    }

    @Test
    void unusableMessagesAreRefused() {
        String[][] bad = {
                {"booking.unknown", CONFIRMED},                                                       // wrong key
                {"booking.confirmed", "not json"},
                {"booking.confirmed", "[1,2]"},
                {"booking.confirmed", ""},
                {"booking.confirmed", CONFIRMED.replace("BookingConfirmed", "BookingCancelled")},     // label mismatch
                {"booking.confirmed", CONFIRMED.replaceAll("\"eventId\":\"[^\"]*\",", "")},
                {"booking.confirmed", CONFIRMED.replace("\"bookingId\"", "\"bookingIdX\"")},
                {"booking.confirmed", CONFIRMED.replace("\"travelerId\":\"t1\"", "\"travelerId\":\"  \"")},
                {"booking.confirmed", CONFIRMED.replace("\"travelerId\":\"t1\"", "\"travelerId\":42")},
        };
        for (String[] b : bad) {
            assertThatThrownBy(() -> parse(b[0], b[1])).as(b[0] + " / " + b[1]).isInstanceOf(InvalidEventException.class);
        }
    }
}
