package com.tuempresa.storage.inventory.infrastructure.out.persistence;

import com.tuempresa.storage.inventory.domain.CheckoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutRecordRepository extends JpaRepository<CheckoutRecord, Long> {

    Optional<CheckoutRecord> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);
}
