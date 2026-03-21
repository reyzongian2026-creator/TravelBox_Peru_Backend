package com.tuempresa.storage.incidents.infrastructure.out.persistence;

import com.tuempresa.storage.incidents.domain.Incident;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    long countByStatus(IncidentStatus status);

    List<Incident> findByReservationIdIn(Collection<Long> reservationIds);

    List<Incident> findByReservationIdInAndStatus(Collection<Long> reservationIds, IncidentStatus status);
}
