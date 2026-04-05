package com.tuempresa.storage.warehouses.infrastructure.in.web;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.domain.FavoriteWarehouse;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.FavoriteWarehouseRepository;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteWarehouseRepository favoriteRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public FavoriteController(FavoriteWarehouseRepository favoriteRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor) {
        this.favoriteRepository = favoriteRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> list() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(principal -> reactiveBlockingExecutor.call(() -> {
                    List<FavoriteWarehouse> favs = favoriteRepository
                            .findByUserIdOrderByCreatedAtDesc(principal.getId());
                    return favs.stream().map(f -> {
                        Warehouse w = f.getWarehouse();
                        return Map.<String, Object>of(
                                "id", f.getId(),
                                "warehouseId", w.getId(),
                                "warehouseName", w.getName(),
                                "warehouseAddress", w.getAddress(),
                                "cityName", w.getCity() != null ? w.getCity().getName() : "",
                                "createdAt", f.getCreatedAt().toString());
                    }).toList();
                }))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{warehouseId}")
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> add(@PathVariable Long warehouseId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(principal -> reactiveBlockingExecutor.call(() -> {
                    if (favoriteRepository.existsByUserIdAndWarehouseId(principal.getId(), warehouseId)) {
                        return Map.<String, Object>of("added", false, "message", "Already in favorites.");
                    }
                    User user = userRepository.findById(principal.getId())
                            .orElseThrow(
                                    () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND",
                                    "Warehouse not found."));
                    favoriteRepository.save(new FavoriteWarehouse(user, warehouse));
                    return Map.<String, Object>of("added", true);
                }))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{warehouseId}")
    @Transactional
    public Mono<ResponseEntity<Map<String, Object>>> remove(@PathVariable Long warehouseId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(principal -> reactiveBlockingExecutor.call(() -> {
                    favoriteRepository.deleteByUserIdAndWarehouseId(principal.getId(), warehouseId);
                    return Map.<String, Object>of("removed", true);
                }))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{warehouseId}/check")
    public Mono<ResponseEntity<Map<String, Object>>> check(@PathVariable Long warehouseId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(principal -> reactiveBlockingExecutor.call(() -> {
                    Map<String, Object> result = Map.of("isFavorite",
                            (Object) favoriteRepository.existsByUserIdAndWarehouseId(principal.getId(), warehouseId));
                    return result;
                }))
                .map(ResponseEntity::ok);
    }
}
