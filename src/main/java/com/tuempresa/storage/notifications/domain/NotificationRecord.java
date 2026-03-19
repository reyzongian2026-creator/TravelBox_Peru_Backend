package com.tuempresa.storage.notifications.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class NotificationRecord extends AuditableEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    public static NotificationRecord of(
            Long userId,
            String type,
            NotificationChannel channel,
            NotificationStatus status,
            String title,
            String message,
            String payloadJson
    ) {
        NotificationRecord record = new NotificationRecord();
        record.userId = userId;
        record.type = type;
        record.channel = channel;
        record.status = status;
        record.title = title;
        record.message = message;
        record.payloadJson = payloadJson;
        return record;
    }

    public Long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
