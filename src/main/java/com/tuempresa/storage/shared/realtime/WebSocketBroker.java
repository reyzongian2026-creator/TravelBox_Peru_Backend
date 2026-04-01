package com.tuempresa.storage.shared.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketBroker {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroker.class);
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final int MAX_TOTAL_CONNECTIONS = 10000;

    private final Map<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<WebSocketMessage>> userSinks = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketBroker(ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void handleConnection(WebSocketSession session, String token) {
        try {
            if (totalConnections.get() >= MAX_TOTAL_CONNECTIONS) {
                log.warn("WebSocket rejected: max connections reached");
                closeSessionSafely(session);
                return;
            }

            Long userIdLong = jwtTokenProvider.extractUserId(token);
            if (userIdLong == null) {
                log.warn("WebSocket rejected: invalid token");
                closeSessionSafely(session);
                return;
            }
            String userId = userIdLong.toString();

            Set<WebSocketSession> existingSessions = sessionsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
            if (existingSessions.size() >= MAX_CONNECTIONS_PER_USER) {
                log.warn("WebSocket rejected: max connections per user reached for userId={}", userId);
                closeSessionSafely(session);
                return;
            }

            String sessionId = session.getId();
            sessionToUserId.put(sessionId, userId);
            existingSessions.add(session);
            totalConnections.incrementAndGet();
            
            userSinks.computeIfAbsent(userId, k -> Sinks.many().multicast().onBackpressureBuffer(100));

            log.info("WebSocket connected: userId={}, sessionId={}, totalConnections={}", userId, sessionId, totalConnections.get());

            sendMessage(session, new WebSocketMessage("connected", Map.of("status", "ok", "userId", userId)));

            session.receive()
                    .doOnNext(msg -> handleMessage(session, msg.getPayloadAsText()))
                    .doOnComplete(() -> disconnect(session))
                    .doOnError(e -> {
                        log.error("WebSocket receive error for session {}: {}", sessionId, e.getMessage());
                        disconnect(session);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("WebSocket connection error", e);
            closeSessionSafely(session);
        }
    }

    private void handleMessage(WebSocketSession session, String text) {
        try {
            Map<String, Object> json = objectMapper.readValue(text, Map.class);
            String type = json.get("type") != null ? json.get("type").toString() : "";
            
            switch (type) {
                case "ping":
                    sendMessage(session, new WebSocketMessage("pong", Map.of("timestamp", System.currentTimeMillis())));
                    break;
                case "pong":
                    log.debug("Received pong from session {}", session.getId());
                    break;
                default:
                    log.debug("Received message type '{}' from session {}", type, session.getId());
            }
        } catch (Exception e) {
            log.warn("Error parsing WebSocket message from session {}: {}", session.getId(), e.getMessage());
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
        
        totalConnections.decrementAndGet();
        log.info("WebSocket disconnected: sessionId={}, remainingConnections={}", sessionId, totalConnections.get());
    }

    public void sendToUser(String userId, String eventType, Map<String, Object> payload) {
        WebSocketMessage message = new WebSocketMessage(eventType, payload);
        
        Sinks.Many<WebSocketMessage> sink = userSinks.get(userId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
        
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions != null) {
            sessions.removeIf(session -> {
                try {
                    sendMessage(session, message);
                    return false;
                } catch (Exception e) {
                    log.warn("Removing failed session {} for user {}", session.getId(), userId);
                    disconnect(session);
                    return true;
                }
            });
        }
    }

    public void sendToUsers(List<String> userIds, String eventType, Map<String, Object> payload) {
        for (String userId : userIds) {
            sendToUser(userId, eventType, payload);
        }
    }

    public void broadcast(String eventType, Map<String, Object> payload) {
        List<String> userIds = new ArrayList<>(sessionsByUserId.keySet());
        for (String userId : userIds) {
            sendToUser(userId, eventType, payload);
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.send(Mono.just(session.textMessage(json)))
                    .doOnError(e -> {
                        log.error("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                        disconnect(session);
                    })
                    .onErrorResume(e -> Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            log.error("Error serializing WebSocket message: {}", e.getMessage());
        }
    }

    private void closeSessionSafely(WebSocketSession session) {
        session.close()
                .doOnError(e -> log.warn("Error closing WebSocket session {}: {}", session.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    public int getConnectionCount() {
        return totalConnections.get();
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
