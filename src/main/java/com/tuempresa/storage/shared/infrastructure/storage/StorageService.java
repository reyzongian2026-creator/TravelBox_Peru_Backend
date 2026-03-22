package com.tuempresa.storage.shared.infrastructure.storage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface StorageService {

    enum FileCategory {
        PROFILES("profiles", "travelbox-profiles"),
        WAREHOUSES("warehouses", "travelbox-warehouses"),
        DOCUMENTS("documents", "travelbox-documents"),
        EVIDENCES("evidences", "travelbox-evidences"),
        REPORTS("reports", "travelbox-reports"),
        EXPORTS("exports", "travelbox-exports");

        private final String apiPath;
        private final String containerName;

        FileCategory(String apiPath, String containerName) {
            this.apiPath = apiPath;
            this.containerName = containerName;
        }

        public String getApiPath() {
            return apiPath;
        }

        public String getContainerName() {
            return containerName;
        }
    }

    record UploadResult(
            String filename,
            String url,
            String contentType,
            long size
    ) {}

    record DownloadResult(
            ByteArrayResource resource,
            String filename,
            String contentType,
            long size
    ) {}

    UploadResult upload(MultipartFile file, FileCategory category) throws IOException;

    UploadResult upload(InputStream data, long length, String filename, String contentType, FileCategory category);

    Optional<DownloadResult> download(String filename, FileCategory category);

    boolean delete(String filename, FileCategory category);

    boolean exists(String filename, FileCategory category);

    String getUrl(String filename, FileCategory category);

    String getUrlWithSas(String filename, FileCategory category, int expiryMinutes);

    boolean isContentTypeAllowed(String contentType, FileCategory category);

    String generateBlobName(String originalFilename, FileCategory category);
}