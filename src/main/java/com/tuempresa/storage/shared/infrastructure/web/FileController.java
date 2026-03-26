package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService.DownloadResult;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService.FileCategory;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final StorageService storageService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public FileController(
            StorageService storageService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.storageService = storageService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/{category}/{filename:.+}")
    public Mono<ResponseEntity<ByteArrayResource>> readFile(
            @PathVariable String category,
            @PathVariable String filename
    ) {
        return reactiveBlockingExecutor.call(() -> {
            FileCategory fileCategory = parseCategory(category);
            DownloadResult result = storageService.download(filename, fileCategory)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado."));
            MediaType mediaType = detectMediaType(result.contentType());
            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=300")
                    .contentType(mediaType)
                    .body(result.resource());
        });
    }

    private FileCategory parseCategory(String rawCategory) {
        String normalized = rawCategory == null ? "" : rawCategory.trim().toLowerCase(Locale.ROOT);
        for (FileCategory category : FileCategory.values()) {
            if (category.getApiPath().equals(normalized)) {
                return category;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria de archivo no soportada.");
    }

    private MediaType detectMediaType(String mime) {
        if (mime == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(mime);
    }
}
