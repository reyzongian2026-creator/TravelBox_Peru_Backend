package com.tuempresa.storage.inventory.infrastructure.out.persistence;

import com.tuempresa.storage.inventory.domain.StoredItemEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoredItemEvidenceRepository extends JpaRepository<StoredItemEvidence, Long> {

    List<StoredItemEvidence> findByReservationIdOrderByCreatedAtAsc(Long reservationId);

    List<StoredItemEvidence> findByReservationIdAndTypeOrderByCreatedAtAsc(Long reservationId, String type);

    boolean existsByReservationIdAndTypeIgnoreCase(Long reservationId, String type);
}
