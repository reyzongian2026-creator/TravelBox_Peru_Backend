package com.tuempresa.storage.ops.infrastructure.out.persistence;

import com.tuempresa.storage.ops.domain.QrHandoffApproval;
import com.tuempresa.storage.ops.domain.QrHandoffApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QrHandoffApprovalRepository extends JpaRepository<QrHandoffApproval, Long> {

    Optional<QrHandoffApproval> findByIdAndStatus(Long id, QrHandoffApprovalStatus status);

    List<QrHandoffApproval> findTop100ByStatusOrderByCreatedAtDesc(QrHandoffApprovalStatus status);

    List<QrHandoffApproval> findTop100ByReservationIdOrderByCreatedAtDesc(Long reservationId);

    boolean existsByReservationIdAndStatus(Long reservationId, QrHandoffApprovalStatus status);
}
