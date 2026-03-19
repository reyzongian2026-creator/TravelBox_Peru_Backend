package com.tuempresa.storage.ops.infrastructure.out.persistence;

import com.tuempresa.storage.ops.domain.QrHandoffCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QrHandoffCaseRepository extends JpaRepository<QrHandoffCase, Long> {

    Optional<QrHandoffCase> findByReservationId(Long reservationId);

    Optional<QrHandoffCase> findByBagTagIdIgnoreCase(String bagTagId);
}
