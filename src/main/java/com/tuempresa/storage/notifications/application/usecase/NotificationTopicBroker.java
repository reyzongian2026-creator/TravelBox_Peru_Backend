package com.tuempresa.storage.notifications.application.usecase;

import com.tuempresa.storage.notifications.application.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class NotificationTopicBroker {

    private final ConcurrentMap<Long, Sinks.Many<NotificationResponse>> sinksByUserId = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<NotificationResponse>> subscribe(Long userId) {
        if (userId == null) {
            return Flux.empty();
        }

        // 1. Flujo principal de notificaciones del usuario
        Flux<ServerSentEvent<NotificationResponse>> userEvents = sinkForUser(userId)
                .asFlux()
                .map(notification -> ServerSentEvent.builder(notification)
                        .id(String.valueOf(notification.id()))
                        .event("notification")
                        .build());

        // 2. Flujo de Heartbeat (Latido) cada 30 segundos para evitar timeouts de NGINX/AWS
        Flux<ServerSentEvent<NotificationResponse>> keepAliveEvents = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<NotificationResponse>builder()
                        .comment("keepalive")
                        .build());

        // 3. Mezclar notificaciones reales con latidos
        return Flux.merge(userEvents, keepAliveEvents)
                .startWith(ServerSentEvent.<NotificationResponse>builder()
                        .event("connected")
                        .build())
                .doFinally(signalType -> cleanupIfUnused(userId));
    }

    public void publish(Long userId, NotificationResponse notification) {
        if (userId == null || notification == null) {
            return;
        }
        Sinks.Many<NotificationResponse> sink = sinksByUserId.get(userId);
        if (sink == null) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(notification);
        if (result == Sinks.EmitResult.FAIL_TERMINATED) {
            sinksByUserId.remove(userId, sink);
        }
    }

    private Sinks.Many<NotificationResponse> sinkForUser(Long userId) {
        // Usar onBackpressureBuffer en lugar de directBestEffort para no perder
        // notificaciones si el cliente tiene micro-cortes de red.
        return sinksByUserId.computeIfAbsent(
                userId,
                ignored -> Sinks.many().multicast().onBackpressureBuffer()
        );
    }

    private void cleanupIfUnused(Long userId) {
        Sinks.Many<NotificationResponse> sink = sinksByUserId.get(userId);
        if (sink == null) {
            return;
        }
        if (sink.currentSubscriberCount() == 0) {
            sinksByUserId.remove(userId, sink);
        }
    }
}