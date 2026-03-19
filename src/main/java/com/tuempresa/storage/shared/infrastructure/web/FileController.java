package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.storage.LocalFileStorageService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final LocalFileStorageService localFileStorageService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public FileController(
            LocalFileStorageService localFileStorageService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.localFileStorageService = localFileStorageService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/{category}/{filename:.+}")
    public Mono<ResponseEntity<ByteArrayResource>> readFile(
            @PathVariable String category,
            @PathVariable String filename
    ) {
        return reactiveBlockingExecutor.call(() -> {
            Path path = localFileStorageService.resolveForRead(category, filename);
            MediaType mediaType = detectMediaType(path);
            byte[] content = Files.readAllBytes(path);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(new ByteArrayResource(content));
        });
    }

    private MediaType detectMediaType(Path path) throws IOException {
        String mime = Files.probeContentType(path);
        if (mime == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(mime);
    }
}
