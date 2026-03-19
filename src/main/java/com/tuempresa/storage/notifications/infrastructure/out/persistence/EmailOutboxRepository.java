package com.tuempresa.storage.notifications.infrastructure.out.persistence;

import com.tuempresa.storage.notifications.domain.EmailOutboxRecord;
import com.tuempresa.storage.notifications.domain.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxRecord, Long> {

    boolean existsByDedupKey(String dedupKey);

    @Query("""
            select e.id
            from EmailOutboxRecord e
            where e.status = :status
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.createdAt asc
            """)
    List<Long> findReadyIds(
            @Param("status") EmailOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOutboxRecord e where e.id = :id")
    Optional<EmailOutboxRecord> findByIdForUpdate(@Param("id") Long id);
}

