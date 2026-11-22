package ma.mobility.notifications.events;

import java.util.List;
import ma.mobility.notifications.model.NotificationType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a platform event and refuses anything unusable. The type comes from the routing key and
 * must agree with the "event" field of the body, so a mislabelled message is not trusted.
 */
@Component
public class EventParser {

    private final ObjectMapper json;

    public EventParser(ObjectMapper json) {
        this.json = json;
    }

    public IncomingEvent parse(String routingKey, byte[] body) {
        NotificationType type = NotificationType.fromRoutingKey(routingKey)
                .orElseThrow(() -> new InvalidEventException("unexpected routing key '" + routingKey + "'"));

        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (RuntimeException e) {
            throw new InvalidEventException("body is not valid JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("body is not a JSON object");
        }

        String event = text(root, "event");
        if (!type.eventName().equals(event)) {
            throw new InvalidEventException("event '" + event + "' does not match routing key '" + routingKey + "'");
        }
        String eventId = required(root, "eventId");
        String bookingId = required(root, "bookingId");
        String travelerId = required(root, "travelerId");

        Long totalMinor = null;
        JsonNode total = root.get("totalMinor");
        if (total != null && total.isIntegralNumber()) {
            totalMinor = total.asLong();
        }

        List<IncomingEvent.Leg> legs = new java.util.ArrayList<>();
        JsonNode legsNode = root.get("legs");
        if (legsNode != null && legsNode.isArray()) {
            for (JsonNode leg : legsNode) {
                String operator = text(leg, "operator");
                String reference = text(leg, "reference");
                if (operator != null && reference != null) {
                    legs.add(new IncomingEvent.Leg(operator, reference));
                }
            }
        }
        return new IncomingEvent(eventId, type, bookingId, travelerId, totalMinor, legs);
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("missing field '" + field + "'");
        }
        return value.trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || !value.isString()) ? null : value.asString();
    }
}
