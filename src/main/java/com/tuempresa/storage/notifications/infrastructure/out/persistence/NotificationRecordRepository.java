package com.tuempresa.storage.notifications.infrastructure.out.persistence;

import com.tuempresa.storage.notifications.domain.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    Page<NotificationRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<NotificationRecord> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<NotificationRecord> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long id, Pageable pageable);

    long deleteByUserIdAndId(Long userId, Long id);

    long deleteByUserId(Long userId);
}
