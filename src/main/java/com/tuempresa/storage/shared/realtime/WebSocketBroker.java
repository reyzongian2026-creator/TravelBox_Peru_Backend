package com.tuempresa.storage.shared.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketBroker {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroker.class);

    private final Map<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<WebSocketMessage>> userSinks = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketBroker(ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void handleConnection(WebSocketSession session, String token) {
        try {
            Long userIdLong = jwtTokenProvider.extractUserId(token);
            if (userIdLong == null) {
                log.warn("WebSocket rejected: invalid token");
                session.close().subscribe();
                return;
            }
            String userId = userIdLong.toString();

            String sessionId = session.getId();
            sessionToUserId.put(sessionId, userId);
            
            sessionsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            userSinks.computeIfAbsent(userId, k -> Sinks.many().multicast().onBackpressureBuffer(100));

            log.info("WebSocket connected: userId={}, sessionId={}", userId, sessionId);

            sendMessage(session, new WebSocketMessage("connected", Map.of("status", "ok", "userId", userId)));

            session.receive()
                    .doOnComplete(() -> disconnect(session))
                    .doOnError(e -> disconnect(session))
                    .subscribe();

        } catch (Exception e) {
            log.error("WebSocket connection error", e);
            session.close().subscribe();
        }
    }

    public void disconnect(WebSocketSession session) {
        String sessionId = session.getId();
        String userId = sessionToUserId.remove(sessionId);
        
        if (userId != null) {
            Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByUserId.remove(userId);
                    Sinks.Many<WebSocketMessage> sink = userSinks.remove(userId);
                    if (sink != null) {
                        sink.tryEmitComplete();
                    }
                }
            }
        }
        
        log.info("WebSocket disconnected: sessionId={}", sessionId);
    }

    public void sendToUser(String userId, String eventType, Map<String, Object> payload) {
        WebSocketMessage message = new WebSocketMessage(eventType, payload);
        
        Sinks.Many<WebSocketMessage> sink = userSinks.get(userId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
        
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                sendMessage(session, message);
            }
        }
    }

    public void sendToUsers(List<String> userIds, String eventType, Map<String, Object> payload) {
        for (String userId : userIds) {
            sendToUser(userId, eventType, payload);
        }
    }

    public void broadcast(String eventType, Map<String, Object> payload) {
        for (String userId : sessionsByUserId.keySet()) {
            sendToUser(userId, eventType, payload);
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.send(Mono.just(session.textMessage(json)))
                    .doOnError(e -> log.error("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage()))
                    .subscribe();
        } catch (Exception e) {
            log.error("Error serializing WebSocket message: {}", e.getMessage());
        }
    }

    public int getConnectionCount() {
        return sessionsByUserId.values().stream().mapToInt(Set::size).sum();
    }

    public boolean isUserConnected(String userId) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public record WebSocketMessage(String type, Map<String, Object> data) {
        public static WebSocketMessage fromJson(Map<String, Object> json) {
            String type = json.get("type") != null ? json.get("type").toString() : "unknown";
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) json.get("data");
            return new WebSocketMessage(type, data != null ? data : Map.of());
        }
    }
}
