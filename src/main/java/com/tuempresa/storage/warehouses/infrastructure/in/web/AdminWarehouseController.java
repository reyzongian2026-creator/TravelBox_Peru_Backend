package com.tuempresa.storage.warehouses.infrastructure.in.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.warehouses.application.dto.AdminWarehouseRequest;
import com.tuempresa.storage.warehouses.application.dto.WarehouseRegistryResponse;
import com.tuempresa.storage.warehouses.application.dto.WarehouseResponse;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/admin/warehouses", "/api/v1/admin/almacenes"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminWarehouseController {

    private final WarehouseService warehouseService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;

    public AdminWarehouseController(
            WarehouseService warehouseService,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter
    ) {
        this.warehouseService = warehouseService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
    }

    @GetMapping
    public Mono<List<WarehouseResponse>> list(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active
    ) {
        return reactiveBlockingExecutor.call(() -> warehouseService.searchAdmin(cityId, query, active));
    }

    @GetMapping({"/registry", "/registros"})
    public Mono<PagedResponse<WarehouseRegistryResponse>> registry(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active
    ) {
        return reactiveBlockingExecutor.call(() -> warehouseService.registryPage(page, size, cityId, query, active));
    }

    @PostMapping
    public Mono<ResponseEntity<WarehouseResponse>> create(@Valid @RequestBody AdminWarehouseRequest request) {
        return reactiveBlockingExecutor.call(() -> warehouseService.create(request))
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<WarehouseResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminWarehouseRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> warehouseService.update(id, request))
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = {"/{id}/photo", "/{id}/imagen"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<WarehouseResponse>> updatePhoto(
            @PathVariable Long id,
            @RequestPart("file") FilePart file
    ) {
        return reactiveMultipartAdapter.toMultipartFile(file)
                .flatMap(multipartFile -> reactiveBlockingExecutor.call(
                        () -> warehouseService.updatePhoto(id, multipartFile)
                ))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return reactiveBlockingExecutor.call(() -> {
            warehouseService.delete(id);
            return ResponseEntity.noContent().build();
        });
    }
}
