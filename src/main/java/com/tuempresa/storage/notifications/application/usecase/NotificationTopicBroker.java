package com.tuempresa.storage.notifications.application.usecase;

import com.tuempresa.storage.notifications.application.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class NotificationTopicBroker {

    private final ConcurrentMap<Long, Sinks.Many<NotificationResponse>> sinksByUserId = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<NotificationResponse>> subscribe(Long userId) {
        if (userId == null) {
            return Flux.empty();
        }
        return sinkForUser(userId)
                .asFlux()
                .map(notification -> ServerSentEvent.builder(notification)
                        .id(String.valueOf(notification.id()))
                        .event("notification")
                        .build())
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
        return sinksByUserId.computeIfAbsent(
                userId,
                ignored -> Sinks.many().multicast().directBestEffort()
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
