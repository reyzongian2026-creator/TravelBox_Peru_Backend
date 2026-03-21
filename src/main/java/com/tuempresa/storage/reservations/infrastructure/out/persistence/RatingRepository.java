package com.tuempresa.storage.reservations.infrastructure.out.persistence;

import com.tuempresa.storage.reservations.domain.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    Page<Rating> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);

    Optional<Rating> findByReservationId(Long reservationId);

    Optional<Rating> findByUserIdAndWarehouseId(Long userId, Long warehouseId);

    boolean existsByUserIdAndWarehouseId(Long userId, Long warehouseId);

    long countByWarehouseId(Long warehouseId);

    @Query("select avg(r.stars) from Rating r where r.warehouse.id = :warehouseId")
    BigDecimal getAverageStarsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("select avg(r.stars) from Rating r where r.warehouse.id = :warehouseId and r.type = :type")
    BigDecimal getAverageStarsByWarehouseIdAndType(@Param("warehouseId") Long warehouseId, @Param("type") Rating.RatingType type);

    List<Rating> findTop10ByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    @Query("SELECT r FROM Rating r JOIN FETCH r.user JOIN FETCH r.warehouse LEFT JOIN FETCH r.reservation ORDER BY r.createdAt DESC")
    List<Rating> findAllWithRelations();
}
