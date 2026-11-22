package ma.mobility.saga.steps;

import ma.mobility.saga.api.model.BookingRequest;
import ma.mobility.saga.api.model.BookingResponse;
import ma.mobility.saga.clients.OperatorClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {
    private OperatorClient client;
    private SagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        client = mock(OperatorClient.class);
        orchestrator = new SagaOrchestrator(
                client,
                "http://rail",
                "http://air",
                "http://car");
    }

    @Test
    void holdsAndConfirmsInOrder() {
        BookingRequest request = new BookingRequest(
                List.of("RAIL-CAS-RAB-0730", "AIR-CMN-MAD-1800"), 2);
        when(client.hold("http://rail", "RAIL-CAS-RAB-0730", 2,
                "booking-1:hold:0", "trace")).thenReturn("RAIL-HOLD-1");
        when(client.hold("http://air", "AIR-CMN-MAD-1800", 2,
                "booking-1:hold:1", "trace")).thenReturn("AIR-HOLD-1");
        when(client.confirm("http://rail", "RAIL-HOLD-1",
                "booking-1:confirm:0", "trace")).thenReturn("RAIL-BOOK-1");
        when(client.confirm("http://air", "AIR-HOLD-1",
                "booking-1:confirm:1", "trace")).thenReturn("AIR-BOOK-1");

        BookingResponse response = orchestrator.execute(request, "booking-1", "trace");

        assertEquals(true, response.success());
        assertEquals(List.of("RAIL-BOOK-1", "AIR-BOOK-1"), response.bookingRefs());
        var order = inOrder(client);
        order.verify(client).hold("http://rail", "RAIL-CAS-RAB-0730", 2,
                "booking-1:hold:0", "trace");
        order.verify(client).hold("http://air", "AIR-CMN-MAD-1800", 2,
                "booking-1:hold:1", "trace");
        order.verify(client).confirm("http://rail", "RAIL-HOLD-1",
                "booking-1:confirm:0", "trace");
        order.verify(client).confirm("http://air", "AIR-HOLD-1",
                "booking-1:confirm:1", "trace");
    }

    @Test
    void holdFailureCancelsPreviouslyHeldOffers() {
        BookingRequest request = new BookingRequest(
                List.of("RAIL-CAS-RAB-0730", "AIR-CMN-MAD-1800"), 4);
        when(client.hold("http://rail", "RAIL-CAS-RAB-0730", 4,
                "booking-2:hold:0", "trace")).thenReturn("RAIL-HOLD-1");
        when(client.hold("http://air", "AIR-CMN-MAD-1800", 4,
                "booking-2:hold:1", "trace"))
                .thenThrow(new RuntimeException("NotAvailable"));

        assertThrows(SagaFailureException.class,
                () -> orchestrator.execute(request, "booking-2", "trace"));

        verify(client).cancel("http://rail", "RAIL-HOLD-1", "trace");
    }

    @Test
    void confirmFailureCancelsConfirmedAndRemainingReferences() {
        BookingRequest request = new BookingRequest(
                List.of("RAIL-CAS-RAB-0730", "AIR-CMN-MAD-1800"), 1);
        when(client.hold("http://rail", "RAIL-CAS-RAB-0730", 1,
                "booking-3:hold:0", "trace")).thenReturn("RAIL-HOLD-1");
        when(client.hold("http://air", "AIR-CMN-MAD-1800", 1,
                "booking-3:hold:1", "trace")).thenReturn("AIR-HOLD-1");
        when(client.confirm("http://rail", "RAIL-HOLD-1",
                "booking-3:confirm:0", "trace")).thenReturn("RAIL-BOOK-1");
        when(client.confirm("http://air", "AIR-HOLD-1",
                "booking-3:confirm:1", "trace"))
                .thenThrow(new RuntimeException("NotAvailable"));

        assertThrows(SagaFailureException.class,
                () -> orchestrator.execute(request, "booking-3", "trace"));

        verify(client).cancel("http://rail", "RAIL-BOOK-1", "trace");
        verify(client).cancel("http://air", "AIR-HOLD-1", "trace");
    }

    @Test
    void unknownOfferOperatorFailsBeforeClientCalls() {
        BookingRequest request = new BookingRequest(List.of("UNKNOWN-1"), 1);

        assertThrows(SagaFailureException.class,
                () -> orchestrator.execute(request, null, null));
        verifyNoInteractions(client);
    }
}
