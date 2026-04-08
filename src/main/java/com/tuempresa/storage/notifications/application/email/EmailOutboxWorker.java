package com.tuempresa.storage.notifications.application.email;

import com.tuempresa.storage.notifications.domain.EmailOutboxRecord;
import com.tuempresa.storage.notifications.infrastructure.out.persistence.EmailOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailDispatchService emailDispatchService;
    private final int maxAttempts;
    private final long retryBackoffSeconds;

    public EmailOutboxWorker(
            EmailOutboxRepository emailOutboxRepository,
            EmailDispatchService emailDispatchService,
            @Value("${app.email.queue.max-attempts:5}") int maxAttempts,
            @Value("${app.email.queue.retry-backoff-seconds:30}") long retryBackoffSeconds
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailDispatchService = emailDispatchService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffSeconds = Math.max(5L, retryBackoffSeconds);
    }

    @Transactional
    public void processById(Long emailOutboxId) {
        if (emailOutboxId == null) {
            return;
        }
        EmailOutboxRecord record = emailOutboxRepository.findByIdForUpdate(emailOutboxId).orElse(null);
        if (record == null) {
            return;
        }

        Instant now = Instant.now();
        if (!record.isReadyToSend(now)) {
            return;
        }
        record.registerAttempt(now);

        EmailDispatchService.DispatchResult dispatchResult = emailDispatchService.sendHtml(
                record.getRecipient(),
                record.getSubject(),
                record.getHtmlBody(),
                record.getTextBody()
        );
        if (dispatchResult.sent()) {
            record.markSent(dispatchResult.provider(), now);
            log.info("Email sent: id={}, recipient={}, provider={}", emailOutboxId, record.getRecipient(), dispatchResult.provider());
            return;
        }
        if (record.getAttemptCount() >= maxAttempts) {
            record.markFailed(dispatchResult.provider(), dispatchResult.errorMessage());
            log.error("Email FAILED after max attempts: id={}, recipient={}, error={}",
                    emailOutboxId, record.getRecipient(), dispatchResult.errorMessage());
            return;
        }
        long delay = retryBackoffSeconds * Math.max(1L, record.getAttemptCount());
        record.markRetry(
                dispatchResult.provider(),
                dispatchResult.errorMessage(),
                now.plusSeconds(delay)
        );
    }
}

