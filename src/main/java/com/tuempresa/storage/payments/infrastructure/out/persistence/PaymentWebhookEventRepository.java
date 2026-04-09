package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByProviderAndEventId(String provider, String eventId);

    @Modifying
    @Query("DELETE FROM PaymentWebhookEvent e WHERE e.processed = true AND e.receivedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
