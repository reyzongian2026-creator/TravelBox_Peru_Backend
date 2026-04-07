package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.YapeReconciliationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YapeReconciliationAuditRepository extends JpaRepository<YapeReconciliationAudit, Long> {

    List<YapeReconciliationAudit> findByPaymentAttemptIdOrderByCreatedAtDesc(Long paymentAttemptId);

    Optional<YapeReconciliationAudit> findTopByPaymentAttemptIdOrderByCreatedAtDesc(Long paymentAttemptId);

    boolean existsByMessageId(String messageId);
}
