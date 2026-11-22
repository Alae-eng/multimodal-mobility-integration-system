package ma.mobility.notifications.controller;

import java.util.List;
import ma.mobility.notifications.dto.NotificationResponse;
import ma.mobility.notifications.exception.NotificationNotFoundException;
import ma.mobility.notifications.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: notifications are created only by the events, never by a request. */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** GET /v1/notifications?travelerId=... : the traveler's latest notifications, most recent first. */
    @GetMapping
    public List<NotificationResponse> list(@RequestParam("travelerId") String travelerId) {
        return service.listForTraveler(travelerId).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse get(@PathVariable("notificationId") String notificationId) {
        return service.find(notificationId).map(NotificationResponse::from)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
