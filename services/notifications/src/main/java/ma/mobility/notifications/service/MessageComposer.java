package ma.mobility.notifications.service;

import java.util.stream.Collectors;
import ma.mobility.notifications.events.IncomingEvent;
import org.springframework.stereotype.Component;

/** Writes the message the traveler reads. Amounts are stored in centimes and shown in MAD. */
@Component
public class MessageComposer {

    public record Message(String subject, String body) {
    }

    public Message compose(IncomingEvent event) {
        return switch (event.type()) {
            case BOOKING_CONFIRMED -> new Message(
                    "Your trip is confirmed",
                    "Your booking " + event.bookingId() + " is confirmed." + total(event) + references(event));
            case BOOKING_CANCELLED -> new Message(
                    "Your trip was cancelled",
                    "Your booking " + event.bookingId() + " was cancelled." + references(event));
            case SAGA_FAILED -> new Message(
                    "We could not complete your booking",
                    "Your booking " + event.bookingId() + " could not be completed. Nothing was charged"
                            + " and every reservation was released. You can search again.");
        };
    }

    /** 196200 centimes -> "1962.00 MAD", with integer arithmetic only. */
    static String formatMad(long minor) {
        long abs = Math.abs(minor);
        return (minor < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100) + " MAD";
    }

    private String total(IncomingEvent event) {
        return event.totalMinor() == null ? "" : " Total: " + formatMad(event.totalMinor()) + ".";
    }

    private String references(IncomingEvent event) {
        if (event.legs().isEmpty()) {
            return "";
        }
        return " References: " + event.legs().stream()
                .map(l -> l.operator() + " " + l.reference())
                .collect(Collectors.joining(", ")) + ".";
    }
}
