package com.tuempresa.storage.delivery.infrastructure.out.persistence;

import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<DeliveryOrder> findAllByOrderByUpdatedAtDesc();

    List<DeliveryOrder> findByStatusInOrderByUpdatedAtDesc(Collection<DeliveryStatus> statuses);

    List<DeliveryOrder> findByReservationWarehouseIdInOrderByUpdatedAtDesc(Collection<Long> warehouseIds);

    List<DeliveryOrder> findByReservationWarehouseIdInAndStatusInOrderByUpdatedAtDesc(
            Collection<Long> warehouseIds,
            Collection<DeliveryStatus> statuses
    );

    List<DeliveryOrder> findByAssignedCourierIdOrderByUpdatedAtDesc(Long courierId);

    List<DeliveryOrder> findByAssignedCourierIdAndStatusInOrderByUpdatedAtDesc(Long courierId, Collection<DeliveryStatus> statuses);

    List<DeliveryOrder> findByAssignedCourierIsNullOrderByUpdatedAtDesc();

    List<DeliveryOrder> findByAssignedCourierIsNullAndStatusInOrderByUpdatedAtDesc(Collection<DeliveryStatus> statuses);

    List<DeliveryOrder> findByAssignedCourierIsNullAndReservationWarehouseIdInOrderByUpdatedAtDesc(Collection<Long> warehouseIds);

    List<DeliveryOrder> findByAssignedCourierIsNullAndReservationWarehouseIdInAndStatusInOrderByUpdatedAtDesc(
            Collection<Long> warehouseIds,
            Collection<DeliveryStatus> statuses
    );

    boolean existsByReservationIdAndTypeIgnoreCaseAndStatusIn(
            Long reservationId,
            String type,
            Collection<DeliveryStatus> statuses
    );

    @Query("""
            select d.assignedCourier.id as userId, count(d) as total
            from DeliveryOrder d
            where d.assignedCourier.id in :userIds
            group by d.assignedCourier.id
            """)
    List<UserDeliveryCountProjection> countAssignedByCourierIds(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select d.assignedCourier.id as userId, count(d) as total
            from DeliveryOrder d
            where d.assignedCourier.id in :userIds
              and d.status = :status
            group by d.assignedCourier.id
            """)
    List<UserDeliveryCountProjection> countByCourierIdsAndStatus(
            @Param("userIds") Collection<Long> userIds,
            @Param("status") DeliveryStatus status
    );

    @Query("""
            select d.assignedCourier.id as userId, count(d) as total
            from DeliveryOrder d
            where d.assignedCourier.id in :userIds
              and d.status in :statuses
            group by d.assignedCourier.id
            """)
    List<UserDeliveryCountProjection> countByCourierIdsAndStatuses(
            @Param("userIds") Collection<Long> userIds,
            @Param("statuses") Collection<DeliveryStatus> statuses
    );

    @Query("""
            select lower(trim(d.createdBy)) as username, count(d) as total
            from DeliveryOrder d
            where d.createdBy is not null
              and lower(trim(d.createdBy)) in :usernames
            group by lower(trim(d.createdBy))
            """)
    List<CreatorDeliveryCountProjection> countByCreatedByIn(@Param("usernames") Collection<String> usernames);

    interface UserDeliveryCountProjection {
        Long getUserId();

        long getTotal();
    }

    interface CreatorDeliveryCountProjection {
        String getUsername();

        long getTotal();
    }
}
