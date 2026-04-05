package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.CancellationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CancellationRecordRepository extends JpaRepository<CancellationRecord, Long> {

    List<CancellationRecord> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    Optional<CancellationRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<CancellationRecord> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(
            Long reservationId,
            CancellationRecord.CancellationStatus status);

    boolean existsByReservationIdAndStatusIn(
            Long reservationId,
            List<CancellationRecord.CancellationStatus> statuses);
}
