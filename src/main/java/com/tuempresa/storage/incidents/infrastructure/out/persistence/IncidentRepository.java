package com.tuempresa.storage.incidents.infrastructure.out.persistence;

import com.tuempresa.storage.incidents.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
}
