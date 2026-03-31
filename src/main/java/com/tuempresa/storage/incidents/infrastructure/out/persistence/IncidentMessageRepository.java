package com.tuempresa.storage.incidents.infrastructure.out.persistence;

import com.tuempresa.storage.incidents.domain.IncidentMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentMessageRepository extends JpaRepository<IncidentMessage, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<IncidentMessage> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
