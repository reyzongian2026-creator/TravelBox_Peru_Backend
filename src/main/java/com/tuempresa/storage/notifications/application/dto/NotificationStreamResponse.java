package com.tuempresa.storage.notifications.application.dto;

import java.util.List;

public record NotificationStreamResponse(
        long cursor,
        List<NotificationResponse> items
) {
}
