package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(Long reservationId, PaymentStatus status);

    Optional<PaymentAttempt> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<PaymentAttempt> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    Optional<PaymentAttempt> findByProviderReference(String providerReference);

    boolean existsByProviderReference(String providerReference);

    Page<PaymentAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PaymentAttempt> findByReservationUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select p from PaymentAttempt p
            where p.status = :status
              and (
                lower(p.providerReference) like 'offline-counter-%'
                or lower(p.providerReference) like 'offline-cash-%'
                or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_OFFLINE_VALIDATION'
              )
            order by p.createdAt desc
            """)
    Page<PaymentAttempt> findOfflineCashPending(PaymentStatus status, Pageable pageable);

    @Query("""
            select p from PaymentAttempt p
            where p.status = :status
              and p.reservation.warehouse.id in :warehouseIds
              and (
                lower(p.providerReference) like 'offline-counter-%'
                or lower(p.providerReference) like 'offline-cash-%'
                or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_OFFLINE_VALIDATION'
              )
            order by p.createdAt desc
            """)
    Page<PaymentAttempt> findOfflineCashPendingByWarehouses(
            PaymentStatus status,
            java.util.Collection<Long> warehouseIds,
            Pageable pageable
    );
}
