package com.tuempresa.storage.notifications.infrastructure.in.web;

import com.tuempresa.storage.notifications.application.dto.NotificationResponse;
import com.tuempresa.storage.notifications.application.dto.NotificationStreamResponse;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.notifications.application.usecase.NotificationTopicBroker;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationTopicBroker notificationTopicBroker;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public NotificationController(
            NotificationService notificationService,
            NotificationTopicBroker notificationTopicBroker,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.notificationService = notificationService;
        this.notificationTopicBroker = notificationTopicBroker;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping({"/my", "/mine"})
    public Mono<PagedResponse<NotificationResponse>> myNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> notificationService.listMyNotifications(currentUser, page, size)
                ));
    }

    @GetMapping("/stream")
    public Mono<NotificationStreamResponse> streamNotifications(
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "40") int limit
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> notificationService.streamMyNotifications(currentUser, afterId, limit)
                ));
    }

    @GetMapping(value = {"/events", "/sse"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationResponse>> subscribeToNotificationEvents() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMapMany(currentUser -> notificationTopicBroker.subscribe(currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteNotification(@PathVariable Long id) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> notificationService.deleteMyNotification(currentUser, id)
                ))
                .map(deleted -> deleted
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.notFound().build());
    }

    @DeleteMapping({"/my", "/mine"})
    public Mono<ResponseEntity<Map<String, Object>>> deleteMyNotifications() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> notificationService.deleteAllMyNotifications(currentUser)
                ))
                .map(deleted -> ResponseEntity.ok(Map.of("deleted", deleted)));
    }
}
