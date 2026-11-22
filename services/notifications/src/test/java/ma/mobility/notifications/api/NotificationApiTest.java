package ma.mobility.notifications.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import ma.mobility.notifications.events.IncomingEvent;
import ma.mobility.notifications.model.NotificationType;
import ma.mobility.notifications.service.NotificationService;
import ma.mobility.notifications.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationApiTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired NotificationService service;

    private void receive(String eventId, NotificationType type, String bookingId, String travelerId) {
        service.handle(new IncomingEvent(eventId, type, bookingId, travelerId, 8000L,
                List.of(new IncomingEvent.Leg("RAIL", "RAILBK-1"))));
    }

    @Test
    void aTravelerReadsTheirNotifications() throws Exception {
        receive("E1", NotificationType.BOOKING_CONFIRMED, "BK-1", "traveler-1");
        clock.advance(java.time.Duration.ofMinutes(1));     // the clock is frozen: give each event its own moment
        receive("E2", NotificationType.BOOKING_CANCELLED, "BK-1", "traveler-1");
        receive("E3", NotificationType.BOOKING_CONFIRMED, "BK-2", "someone-else");

        mvc.perform(get("/v1/notifications").param("travelerId", "traveler-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("BookingCancelled"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].subject").value("Your trip was cancelled"))
                .andExpect(jsonPath("$[1].type").value("BookingConfirmed"));
        mvc.perform(get("/v1/notifications").param("travelerId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oneNotificationCanBeReadById() throws Exception {
        receive("E1", NotificationType.BOOKING_CONFIRMED, "BK-1", "traveler-1");
        String id = notifications.findAll().get(0).getId();

        mvc.perform(get("/v1/notifications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(id))
                .andExpect(jsonPath("$.bookingId").value("BK-1"));
        mvc.perform(get("/v1/notifications/NOTIF-nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void errorsKeepTheCommonBodyInsteadOfAGeneric500() throws Exception {
        mvc.perform(get("/v1/notifications")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        mvc.perform(get("/v1/nothing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        // notifications are created only by events: no way to write through the API
        mvc.perform(post("/v1/notifications")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mvc.perform(delete("/v1/notifications/x")).andExpect(status().isMethodNotAllowed());
    }
}
