package com.tuempresa.storage.payments.infrastructure.out.persistence;

import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
        Optional<PaymentAttempt> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(Long reservationId,
                        PaymentStatus status);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM PaymentAttempt p WHERE p.id = :id")
        Optional<PaymentAttempt> findByIdForUpdate(@Param("id") Long id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM PaymentAttempt p WHERE p.reservation.id = :reservationId AND p.status = :status ORDER BY p.createdAt DESC LIMIT 1")
        Optional<PaymentAttempt> findFirstByReservationIdAndStatusForUpdate(@Param("reservationId") Long reservationId,
                        @Param("status") PaymentStatus status);

        Optional<PaymentAttempt> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);

        List<PaymentAttempt> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

        Optional<PaymentAttempt> findByProviderReference(String providerReference);

        boolean existsByProviderReference(String providerReference);

        List<PaymentAttempt> findByReservationIdInAndStatusOrderByCreatedAtDesc(
                        Collection<Long> reservationIds,
                        PaymentStatus status);

        @Query("""
                        select coalesce(sum(p.amount), 0)
                        from PaymentAttempt p
                        where p.status = :status
                        """)
        BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

        Page<PaymentAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);

        Page<PaymentAttempt> findByReservationUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

        Page<PaymentAttempt> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

        Page<PaymentAttempt> findByReservationUserIdAndStatusOrderByCreatedAtDesc(
                        Long userId,
                        PaymentStatus status,
                        Pageable pageable);

        @Query("""
                        select p from PaymentAttempt p
                        where p.status = :status
                          and (
                            lower(p.providerReference) like 'offline-counter-%'
                            or lower(p.providerReference) like 'offline-cash-%'
                            or lower(p.providerReference) like 'transfer-%'
                            or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_OFFLINE_VALIDATION'
                            or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_MANUAL_TRANSFER'
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
                            or lower(p.providerReference) like 'transfer-%'
                            or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_OFFLINE_VALIDATION'
                            or upper(coalesce(p.gatewayStatus, '')) = 'WAITING_MANUAL_TRANSFER'
                          )
                        order by p.createdAt desc
                        """)
        Page<PaymentAttempt> findOfflineCashPendingByWarehouses(
                        PaymentStatus status,
                        java.util.Collection<Long> warehouseIds,
                        Pageable pageable);

        Optional<PaymentAttempt> findFirstByProviderReferenceStartingWithOrderByCreatedAtDesc(String prefix);

        @Query("SELECT COUNT(p) FROM PaymentAttempt p WHERE p.gatewayStatus = 'OFFLINE_CONFIRMED_BY_OPERATOR' AND p.updatedBy = :operatorId AND p.confirmedAt >= :since")
        long countCashApprovalsBy(@Param("operatorId") String operatorId, @Param("since") java.time.Instant since);

        @Query("SELECT COUNT(p) FROM PaymentAttempt p WHERE p.status = 'PENDING' AND p.reservation.user.id = :userId AND p.providerReference LIKE 'TRANSFER-%'")
        long countPendingManualTransfersByUserId(@Param("userId") Long userId);

        @Query("""
                        select p from PaymentAttempt p
                        where p.status = :status
                          and p.providerReference like 'TRANSFER-%'
                          and p.amount = :amount
                          and p.createdAt >= :since
                        order by p.createdAt desc
                        """)
        List<PaymentAttempt> findPendingTransfersByAmountSince(
                        @Param("status") PaymentStatus status,
                        @Param("amount") java.math.BigDecimal amount,
                        @Param("since") java.time.Instant since);
}
