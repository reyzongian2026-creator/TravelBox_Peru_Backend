package com.tuempresa.storage.payments.application.usecase;

import com.tuempresa.storage.payments.domain.PaymentWebhookEvent;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the initial idempotency INSERT for webhook events in its own transaction
 * (REQUIRES_NEW) so that a ConstraintViolationException on duplicate eventId does NOT
 * poison the outer transaction's Hibernate session with a rollback-only flag.
 */
@Component
class WebhookEventInserter {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventInserter.class);

    private final PaymentWebhookEventRepository repository;

    WebhookEventInserter(PaymentWebhookEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Tries to insert a new webhook event row and immediately flushes so the unique
     * constraint is evaluated inside this inner transaction.
     *
     * @return the newly saved event, or — if a concurrent thread already inserted the
     *         same (provider, eventId) — the existing row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentWebhookEvent insertOrGetExisting(
            String provider,
            String eventId,
            String eventType,
            String providerReference,
            String payloadJson
    ) {
        try {
            PaymentWebhookEvent event = repository.save(PaymentWebhookEvent.received(
                    provider, eventId, eventType, providerReference, payloadJson));
            repository.flush();
            log.info("Webhook event inserted: provider={}, eventId={}, type={}", provider, eventId, eventType);
            return event;
        } catch (DataIntegrityViolationException raceLost) {
            log.warn("Webhook event duplicate (race): provider={}, eventId={}", provider, eventId);
            // This inner transaction is cleanly rolled back by REQUIRES_NEW.
            // The caller's outer transaction is unaffected.
            return repository.findByProviderAndEventId(provider, eventId)
                    .orElseThrow(() -> raceLost); // safety net — should never happen
        }
    }
}
