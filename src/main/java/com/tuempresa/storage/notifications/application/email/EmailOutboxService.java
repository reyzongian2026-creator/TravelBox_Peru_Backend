package com.tuempresa.storage.notifications.application.email;

import com.tuempresa.storage.notifications.domain.EmailOutboxRecord;
import com.tuempresa.storage.notifications.domain.EmailOutboxStatus;
import com.tuempresa.storage.notifications.infrastructure.out.persistence.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailDispatchService emailDispatchService;
    private final boolean queueEnabled;

    public EmailOutboxService(
            EmailOutboxRepository emailOutboxRepository,
            EmailDispatchService emailDispatchService,
            @org.springframework.beans.factory.annotation.Value("${app.email.queue.enabled:true}") boolean queueEnabled
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailDispatchService = emailDispatchService;
        this.queueEnabled = queueEnabled;
    }

    @Transactional
    public boolean enqueue(
            String recipient,
            String subject,
            String htmlBody,
            String textBody,
            String eventType,
            String dedupKey
    ) {
        String normalizedRecipient = normalize(recipient);
        String normalizedSubject = normalize(subject);
        String normalizedHtml = normalize(htmlBody);
        String normalizedEventType = normalize(eventType);
        String normalizedDedupKey = normalize(dedupKey);
        if (normalizedRecipient == null
                || normalizedSubject == null
                || normalizedHtml == null
                || normalizedEventType == null) {
            return false;
        }

        if (normalizedDedupKey != null && emailOutboxRepository.existsByDedupKey(normalizedDedupKey)) {
            return false;
        }

        if (!queueEnabled) {
            Instant now = Instant.now();
            EmailOutboxRecord record = EmailOutboxRecord.pending(
                    normalizedRecipient,
                    normalizedSubject,
                    normalizedHtml,
                    normalize(textBody),
                    normalizedEventType,
                    normalizedDedupKey
            );
            record.registerAttempt(now);
            EmailDispatchService.DispatchResult dispatchResult = emailDispatchService.sendHtml(
                    normalizedRecipient,
                    normalizedSubject,
                    normalizedHtml,
                    normalize(textBody)
            );
            if (dispatchResult.sent()) {
                record.markSent(dispatchResult.provider(), now);
            } else {
                record.markFailed(dispatchResult.provider(), dispatchResult.errorMessage());
            }

            try {
                emailOutboxRepository.save(record);
                return dispatchResult.sent();
            } catch (DataIntegrityViolationException ex) {
                if (normalizedDedupKey != null) {
                    log.info("Correo duplicado omitido por dedup_key={}", normalizedDedupKey);
                    return false;
                }
                throw ex;
            }
        }

        try {
            EmailOutboxRecord record = EmailOutboxRecord.pending(
                    normalizedRecipient,
                    normalizedSubject,
                    normalizedHtml,
                    normalize(textBody),
                    normalizedEventType,
                    normalizedDedupKey
            );
            emailOutboxRepository.save(record);
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (normalizedDedupKey != null) {
                log.info("Correo duplicado omitido por dedup_key={}", normalizedDedupKey);
                return false;
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyIds(int batchSize) {
        int size = Math.max(1, Math.min(batchSize, 500));
        return emailOutboxRepository.findReadyIds(
                EmailOutboxStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, size)
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
