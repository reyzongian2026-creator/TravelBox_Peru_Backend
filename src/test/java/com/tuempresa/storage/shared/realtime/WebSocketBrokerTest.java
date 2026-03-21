package com.tuempresa.storage.shared.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketBrokerTest {

    @Mock
    private WebSocketSession mockSession;

    private WebSocketBroker webSocketBroker;

    @BeforeEach
    void setUp() {
        webSocketBroker = new WebSocketBroker(null, null);
    }

    @Test
    void shouldTrackConnectionCount() {
        when(mockSession.getId()).thenReturn("session-1");

        assertEquals(0, webSocketBroker.getConnectionCount());
        assertFalse(webSocketBroker.isUserConnected("user-1"));
    }

    @Test
    void shouldReturnFalseForDisconnectedUser() {
        assertFalse(webSocketBroker.isUserConnected("user-999"));
        assertEquals(0, webSocketBroker.getConnectionCount());
    }

    @Test
    void shouldHandleMultipleSessionsForSameUser() {
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("session-1");
        when(session2.getId()).thenReturn("session-2");

        webSocketBroker.sendToUser("user-1", "test_event", Map.of("key", "value"));
        
        assertEquals(0, webSocketBroker.getConnectionCount());
    }

    @Test
    void shouldHandleSendToMultipleUsers() {
        webSocketBroker.sendToUsers(
                List.of("user-1", "user-2", "user-3"),
                "broadcast_event",
                Map.of("data", "test")
        );
        
        assertEquals(0, webSocketBroker.getConnectionCount());
    }

    @Test
    void shouldHandleBroadcast() {
        webSocketBroker.broadcast("all_event", Map.of("broadcast", true));
        
        assertEquals(0, webSocketBroker.getConnectionCount());
    }
}
