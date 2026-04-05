package com.tuempresa.storage.warehouses.infrastructure.out.persistence;

import com.tuempresa.storage.warehouses.domain.FavoriteWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface FavoriteWarehouseRepository extends JpaRepository<FavoriteWarehouse, Long> {

    @EntityGraph(attributePaths = { "warehouse", "warehouse.city" })
    List<FavoriteWarehouse> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteWarehouse> findByUserIdAndWarehouseId(Long userId, Long warehouseId);

    boolean existsByUserIdAndWarehouseId(Long userId, Long warehouseId);

    void deleteByUserIdAndWarehouseId(Long userId, Long warehouseId);
}
