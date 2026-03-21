package com.tuempresa.storage.shared.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class RealtimeWebSocketHandler implements WebSocketHandler {

    private final WebSocketBroker webSocketBroker;

    public RealtimeWebSocketHandler(WebSocketBroker webSocketBroker) {
        this.webSocketBroker = webSocketBroker;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractToken(query);
        webSocketBroker.handleConnection(session, token);
        return Mono.never();
    }

    private String extractToken(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return "";
    }
}
