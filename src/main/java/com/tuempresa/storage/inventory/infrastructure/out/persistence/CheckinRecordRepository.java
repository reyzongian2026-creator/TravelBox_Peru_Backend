package com.tuempresa.storage.inventory.infrastructure.out.persistence;

import com.tuempresa.storage.inventory.domain.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

    Optional<CheckinRecord> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);
}
