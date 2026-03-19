package com.tuempresa.storage.shared.infrastructure.storage;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Path rootPath;

    public LocalFileStorageService(@Value("${app.storage.local-dir:uploads}") String localDir) {
        this.rootPath = Path.of(localDir).toAbsolutePath().normalize();
    }

    public String saveEvidenceImage(MultipartFile file) {
        return saveImage(file, "evidences", "evidence-");
    }

    public String saveWarehouseImage(MultipartFile file) {
        return saveImage(file, "warehouses", "warehouse-");
    }

    public Path resolveForRead(String category, String filename) {
        Path filePath = rootPath.resolve(category).resolve(filename).normalize();
        if (!filePath.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "Archivo invalido.");
        }
        if (!Files.exists(filePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "Archivo no encontrado.");
        }
        return filePath;
    }

    private String saveImage(MultipartFile file, String category, String prefix) {
        validate(file);
        try {
            Path targetDir = rootPath.resolve(category);
            Files.createDirectories(targetDir);
            String extension = extensionFromContentType(file.getContentType());
            String filename = prefix + UUID.randomUUID() + extension;
            Path destination = targetDir.resolve(filename).normalize();
            if (!destination.startsWith(targetDir)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "Ruta de archivo no valida.");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return "/api/v1/files/" + category + "/" + filename;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "No se pudo guardar la imagen.");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Debes enviar un archivo.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "El archivo excede el tamano maximo permitido (5MB).");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", "Solo se permiten imagenes JPG, PNG o WEBP.");
        }
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
