package com.tuempresa.storage.notifications.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.notifications.application.dto.NotificationResponse;
import com.tuempresa.storage.notifications.application.dto.NotificationStreamResponse;
import com.tuempresa.storage.notifications.domain.NotificationChannel;
import com.tuempresa.storage.notifications.domain.NotificationRecord;
import com.tuempresa.storage.notifications.domain.NotificationStatus;
import com.tuempresa.storage.notifications.infrastructure.out.persistence.NotificationRecordRepository;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_STREAM_SIZE = 200;

    private final NotificationRecordRepository notificationRecordRepository;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final NotificationTopicBroker notificationTopicBroker;

    public NotificationService(
            NotificationRecordRepository notificationRecordRepository,
            ObjectMapper objectMapper,
            NotificationTopicBroker notificationTopicBroker,
            @Value("${app.notifications.provider:mock}") String provider
    ) {
        this.notificationRecordRepository = notificationRecordRepository;
        this.objectMapper = objectMapper;
        this.notificationTopicBroker = notificationTopicBroker;
        this.provider = provider == null ? "mock" : provider.trim().toLowerCase();
    }

    @Transactional
    public void notifyReservationCreated(
            Long userId,
            Long reservationId,
            String qrCode,
            String warehouseName,
            Instant startAt,
            Instant endAt,
            BigDecimal totalPrice
    ) {
        notifyUser(
                userId,
                "RESERVATION_CREATED",
                "Reserva creada",
                "Tu reserva " + qrCode + " fue creada y esta pendiente de pago.",
                Map.of(
                        "reservationId", reservationId,
                        "warehouseName", warehouseName,
                        "startAt", startAt,
                        "endAt", endAt,
                        "totalPrice", totalPrice
                )
        );
    }

    @Transactional
    public void notifyPaymentConfirmed(Long userId, Long reservationId, String qrCode, String paymentMethod) {
        notifyUser(
                userId,
                "PAYMENT_CONFIRMED",
                "Pago confirmado",
                "Tu pago de la reserva " + qrCode + " fue confirmado.",
                Map.of(
                        "reservationId", reservationId,
                        "paymentMethod", paymentMethod
                )
        );
    }

    @Transactional
    public void notifyPaymentPendingCashValidation(Long userId, Long reservationId, String qrCode) {
        notifyUser(
                userId,
                "PAYMENT_PENDING_CASH_VALIDATION",
                "Pago en caja pendiente",
                "Tu reserva " + qrCode + " quedo pendiente de validacion por operador.",
                Map.of("reservationId", reservationId)
        );
    }

    @Transactional
    public void notifyPaymentRejected(Long userId, Long reservationId, String qrCode, String reason) {
        notifyUser(
                userId,
                "PAYMENT_REJECTED",
                "Pago rechazado",
                "Tu pago de la reserva " + qrCode + " fue rechazado.",
                Map.of(
                        "reservationId", reservationId,
                        "reason", reason != null ? reason : ""
                )
        );
    }

    @Transactional
    public void notifyReservationExpired(Long userId, Long reservationId, String qrCode) {
        notifyUser(
                userId,
                "RESERVATION_EXPIRED",
                "Reserva expirada",
                "La reserva " + qrCode + " expiro por falta de pago.",
                Map.of("reservationId", reservationId)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> listMyNotifications(AuthUserPrincipal principal, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<NotificationResponse> result = notificationRecordRepository
                .findByUserIdOrderByCreatedAtDesc(principal.getId(), pageRequest)
                .map(this::toResponse);
        return PagedResponse.from(result);
    }

    @Transactional(readOnly = true)
    public NotificationStreamResponse streamMyNotifications(AuthUserPrincipal principal, Long afterId, int limit) {
        int effectiveLimit = clampStreamSize(limit);
        List<NotificationRecord> records;
        if (afterId != null && afterId > 0) {
            records = notificationRecordRepository
                    .findByUserIdAndIdGreaterThanOrderByIdAsc(
                            principal.getId(),
                            afterId,
                            PageRequest.of(0, effectiveLimit)
                    )
                    .getContent();
        } else {
            records = new ArrayList<>(notificationRecordRepository
                    .findByUserIdOrderByIdDesc(principal.getId(), PageRequest.of(0, effectiveLimit))
                    .getContent());
            java.util.Collections.reverse(records);
        }
        List<NotificationResponse> responseItems = records.stream().map(this::toResponse).toList();
        long cursor = responseItems.isEmpty()
                ? (afterId == null ? 0L : afterId)
                : responseItems.get(responseItems.size() - 1).id();
        return new NotificationStreamResponse(cursor, responseItems);
    }

    @Transactional
    public boolean deleteMyNotification(AuthUserPrincipal principal, Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            return false;
        }
        return notificationRecordRepository.deleteByUserIdAndId(principal.getId(), notificationId) > 0;
    }

    @Transactional
    public long deleteAllMyNotifications(AuthUserPrincipal principal) {
        return notificationRecordRepository.deleteByUserId(principal.getId());
    }

    @Transactional
    public void notifyUser(
            Long userId,
            String type,
            String title,
            String message,
            Map<String, ?> payload
    ) {
        if (userId == null) {
            return;
        }
        log.debug("Sending notification: userId={}, type={}, title={}", userId, type, title);
        NotificationStatus status = "mock".equals(provider) ? NotificationStatus.SENT : NotificationStatus.SENT;
        Map<String, Object> normalizedPayload = enrichPayload(payload);
        NotificationRecord record = NotificationRecord.of(
                userId,
                type,
                NotificationChannel.IN_APP,
                status,
                title,
                message,
                toJson(normalizedPayload)
        );
        NotificationRecord saved = notificationRecordRepository.save(record);
        publishAfterCommit(userId, toResponse(saved));
    }

    @Transactional
    public void emitSilentRealtimeEvent(
            Long userId,
            String type,
            Map<String, ?> payload
    ) {
        if (userId == null) {
            return;
        }
        Map<String, Object> normalizedPayload = new LinkedHashMap<>(enrichPayload(payload));
        normalizedPayload.put("silent", true);
        NotificationRecord record = NotificationRecord.of(
                userId,
                type,
                NotificationChannel.IN_APP,
                NotificationStatus.SENT,
                "",
                "",
                toJson(normalizedPayload)
        );
        NotificationRecord saved = notificationRecordRepository.save(record);
        publishAfterCommit(userId, toResponse(saved));
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(),
                record.getUserId(),
                record.getType(),
                record.getChannel(),
                record.getStatus(),
                record.getTitle(),
                record.getMessage(),
                record.getPayloadJson(),
                record.getCreatedAt()
        );
    }

    private int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 20;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private int clampStreamSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 40;
        }
        return Math.min(requestedSize, MAX_STREAM_SIZE);
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return payload == null ? null : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<String, Object> enrichPayload(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : payload.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue());
        }
        if (!normalized.containsKey("route")) {
            Object reservationId = normalized.get("reservationId");
            if (reservationId != null) {
                normalized.put("route", "/reservation/" + reservationId);
            } else if (normalized.get("approvalId") != null) {
                normalized.put("route", "/ops/qr-handoff");
            } else if (normalized.get("incidentId") != null) {
                normalized.put("route", "/operator/incidents");
            }
        }
        if (normalized.get("events") instanceof List<?>) {
            return normalized;
        }
        List<String> eventTags = new ArrayList<>();
        if (normalized.get("reservationId") != null) {
            eventTags.add("reservation");
        }
        if (normalized.get("paymentIntentId") != null) {
            eventTags.add("payment");
        }
        if (normalized.get("deliveryOrderId") != null) {
            eventTags.add("delivery");
        }
        if (!eventTags.isEmpty()) {
            normalized.put("events", eventTags);
        }
        return normalized;
    }

    private void publishAfterCommit(Long userId, NotificationResponse response) {
        if (userId == null || response == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationTopicBroker.publish(userId, response);
                }
            });
            return;
        }
        notificationTopicBroker.publish(userId, response);
    }
}
