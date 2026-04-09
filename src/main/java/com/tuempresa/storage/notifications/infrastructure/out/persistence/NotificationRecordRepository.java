package com.tuempresa.storage.notifications.infrastructure.out.persistence;

import com.tuempresa.storage.notifications.domain.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    Page<NotificationRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<NotificationRecord> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<NotificationRecord> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long id, Pageable pageable);

    long deleteByUserIdAndId(Long userId, Long id);

    long deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM NotificationRecord n WHERE n.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
