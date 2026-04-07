package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.application.PlatformSettingService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "payments.yape.phone", "payments.yape.name", "payments.yape.qr_url",
            "payments.plin.phone", "payments.plin.name", "payments.plin.qr_url",
            "payments.qr.phone", "payments.qr.name", "payments.qr.qr_url");

    private static final long MAX_QR_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp");

    private final PlatformSettingService settingService;
    private final StorageService storageService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;

    public AdminSettingsController(
            PlatformSettingService settingService,
            StorageService storageService,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter) {
        this.settingService = settingService;
        this.storageService = storageService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
    }

    @GetMapping
    public Mono<Map<String, String>> getPaymentSettings() {
        return reactiveBlockingExecutor.call(() -> settingService.getByPrefix("payments."));
    }

    @PutMapping("/{key}")
    public Mono<ResponseEntity<Map<String, String>>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        if (!ALLOWED_KEYS.contains(key)) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Clave no permitida: " + key)));
        }
        String value = body.getOrDefault("value", "");
        if (value.length() > 2000) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "El valor excede los 2000 caracteres permitidos.")));
        }
        return reactiveBlockingExecutor.call(() -> {
            settingService.set(key, value);
            return Map.of("key", key, "value", value);
        }).map(ResponseEntity::ok);
    }

    @PostMapping(value = "/upload-qr/{method}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> uploadQr(
            @PathVariable String method,
            @RequestPart("file") FilePart file) {
        String normalizedMethod = method.trim().toLowerCase();
        if (!Set.of("yape", "plin", "qr").contains(normalizedMethod)) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Metodo no soportado: " + method)));
        }
        return reactiveMultipartAdapter.toMultipartFile(file)
                .flatMap(multipartFile -> {
                    if (multipartFile.getSize() > MAX_QR_FILE_SIZE) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.of("error", "El archivo excede el tamaño maximo de 2 MB.")));
                    }
                    String contentType = multipartFile.getContentType();
                    if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.of("error", "Tipo de archivo no soportado. Usa PNG, JPEG o WebP.")));
                    }
                    return reactiveBlockingExecutor.call(() -> {
                        StorageService.UploadResult result = storageService.upload(
                                multipartFile, StorageService.FileCategory.DOCUMENTS);
                        String settingKey = "payments." + normalizedMethod + ".qr_url";
                        settingService.set(settingKey, result.url());
                        return Map.of(
                                "method", normalizedMethod,
                                "qrUrl", result.url(),
                                "filename", result.filename());
                    }).map(ResponseEntity::ok);
                });
    }
}
