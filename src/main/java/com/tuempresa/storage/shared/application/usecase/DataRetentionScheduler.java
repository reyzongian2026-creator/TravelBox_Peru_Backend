package com.tuempresa.storage.shared.application.usecase;

import com.tuempresa.storage.notifications.domain.EmailOutboxStatus;
import com.tuempresa.storage.notifications.infrastructure.out.persistence.EmailOutboxRepository;
import com.tuempresa.storage.notifications.infrastructure.out.persistence.NotificationRecordRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Daily data-retention sweep. Runs at 03:00 server time.
 *
 * Retention windows:
 *  - Processed payment webhook events: 60 days
 *  - Notification records:             90 days
 *  - Sent email outbox records:        30 days
 */
@Component
@Lazy(false)
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    private static final long WEBHOOK_RETENTION_DAYS      = 60;
    private static final long NOTIFICATION_RETENTION_DAYS = 90;
    private static final long EMAIL_RETENTION_DAYS        = 30;

    private final PaymentWebhookEventRepository  webhookRepo;
    private final NotificationRecordRepository   notifRepo;
    private final EmailOutboxRepository          emailOutboxRepo;

    public DataRetentionScheduler(
            PaymentWebhookEventRepository webhookRepo,
            NotificationRecordRepository notifRepo,
            EmailOutboxRepository emailOutboxRepo) {
        this.webhookRepo     = webhookRepo;
        this.notifRepo       = notifRepo;
        this.emailOutboxRepo = emailOutboxRepo;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runRetention() {
        Instant now = Instant.now();

        int webhooks = webhookRepo.deleteProcessedBefore(
                now.minus(WEBHOOK_RETENTION_DAYS, ChronoUnit.DAYS));

        int notifications = notifRepo.deleteCreatedBefore(
                now.minus(NOTIFICATION_RETENTION_DAYS, ChronoUnit.DAYS));

        int emails = emailOutboxRepo.deleteSentBefore(
                EmailOutboxStatus.SENT,
                now.minus(EMAIL_RETENTION_DAYS, ChronoUnit.DAYS));

        log.info("Data retention complete — webhooks={}, notifications={}, emails={}",
                webhooks, notifications, emails);
    }
}
