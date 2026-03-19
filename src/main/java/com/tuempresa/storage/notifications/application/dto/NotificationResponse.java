package com.tuempresa.storage.notifications.application.dto;

import com.tuempresa.storage.notifications.domain.NotificationChannel;
import com.tuempresa.storage.notifications.domain.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        String type,
        NotificationChannel channel,
        NotificationStatus status,
        String title,
        String message,
        String payloadJson,
        Instant createdAt
) {
}
