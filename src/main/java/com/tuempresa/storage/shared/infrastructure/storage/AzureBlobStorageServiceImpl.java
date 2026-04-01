package com.tuempresa.storage.shared.infrastructure.storage;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
public class AzureBlobStorageServiceImpl implements StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobStorageServiceImpl.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final long MIN_IMAGE_SIZE_BYTES = 5 * 1024;
    private static final int MIN_IMAGE_DIMENSION = 50;

    private LocalFileStorageService localFileStorageService;

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final Set<String> ALLOWED_REPORT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @Value("${azure.storage.images.connection-string:}")
    private String imagesConnectionString;

    @Value("${azure.storage.images.container.profiles:travelbox-profiles}")
    private String profilesContainerName;

    @Value("${azure.storage.images.container.warehouses:travelbox-warehouses}")
    private String warehousesContainerName;

    @Value("${azure.storage.images.container.documents:travelbox-documents}")
    private String documentsContainerName;

    @Value("${azure.storage.images.container.evidences:travelbox-evidences}")
    private String evidencesContainerName;

    @Value("${azure.storage.images.url-base:}")
    private String imagesUrlBase;

    @Value("${azure.storage.reports.connection-string:}")
    private String reportsConnectionString;

    @Value("${azure.storage.reports.container.reports:travelbox-reports}")
    private String reportsContainerName;

    @Value("${azure.storage.reports.container.exports:travelbox-exports}")
    private String exportsContainerName;

    @Value("${azure.storage.reports.url-base:}")
    private String reportsUrlBase;

    @Value("${azure.storage.sas.expiry-minutes:60}")
    private int sasExpiryMinutes;

    @Value("${azure.storage.images.endpoint:}")
    private String imagesEndpoint;

    @Value("${azure.storage.reports.endpoint:}")
    private String reportsEndpoint;

    private BlobServiceClient imagesBlobServiceClient;
    private BlobServiceClient reportsBlobServiceClient;

    public void setLocalFileStorageServiceFallback(LocalFileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
    }

    @PostConstruct
    public void init() {
        if (imagesConnectionString != null && !imagesConnectionString.isBlank()) {
            imagesBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(imagesConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for images initialized (connection string)");
        } else if (imagesEndpoint != null && !imagesEndpoint.isBlank()) {
            imagesBlobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(imagesEndpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            LOG.info("Azure Blob Storage for images initialized (Managed Identity, endpoint: {})", imagesEndpoint);
        } else {
            LOG.warn("Azure Blob Storage for images NOT configured");
        }

        if (reportsConnectionString != null && !reportsConnectionString.isBlank()) {
            reportsBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(reportsConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for reports initialized (connection string)");
        } else if (reportsEndpoint != null && !reportsEndpoint.isBlank()) {
            reportsBlobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(reportsEndpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            LOG.info("Azure Blob Storage for reports initialized (Managed Identity, endpoint: {})", reportsEndpoint);
        } else {
            LOG.warn("Azure Blob Storage for reports NOT configured");
        }
    }

    @Override
    public UploadResult upload(MultipartFile file, FileCategory category) throws IOException {
        validateImageFile(file, category);
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILENAME", "El nombre del archivo contiene caracteres no permitidos.");
        }
        return upload(file.getInputStream(), file.getSize(), originalFilename,
                file.getContentType(), category);
    }
    
    private void validateImageFile(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Debes enviar un archivo.");
        }
        
        if (category == FileCategory.PROFILES || category == FileCategory.WAREHOUSES || category == FileCategory.EVIDENCES) {
            if (file.getSize() < MIN_IMAGE_SIZE_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_SMALL", "La imagen es demasiado pequena. Tamano minimo: 5KB.");
            }
            
            try (InputStream imageStream = file.getInputStream()) {
                BufferedImage image = ImageIO.read(imageStream);
                if (image == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "No se pudo leer la imagen. Formato invalido.");
                }
                if (image.getWidth() < MIN_IMAGE_DIMENSION || image.getHeight() < MIN_IMAGE_DIMENSION) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "IMAGE_DIMENSION_TOO_SMALL",
                            "La imagen es demasiado pequena. Minimo: " + MIN_IMAGE_DIMENSION + "x" + MIN_IMAGE_DIMENSION + " pixels."
                    );
                }
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_READ_ERROR", "Error al validar la imagen.");
            }
        }
    }

    @Override
    public UploadResult upload(InputStream data, long length, String filename,
                               String contentType, FileCategory category) {
        BlobServiceClient client = getBlobServiceClient(category);
        
        if (client == null || !isAzureConfigured(category)) {
            return uploadToLocalFallback(data, length, filename, contentType, category);
        }
        
        String containerName = getContainerName(category);
        String blobName = generateBlobName(filename, category);

        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
        ensureContainerExists(containerClient);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        blobClient.upload(data, length, true);
        blobClient.setHttpHeaders(
                new BlobHttpHeaders().setContentType(resolveContentType(contentType, blobName))
        );

        String url = getUrl(blobName, category);
        LOG.info("Uploaded file {} to container {} via Azure", blobName, containerName);

        return new UploadResult(blobName, url, contentType, length);
    }
    
    private boolean isAzureConfigured(FileCategory category) {
        if (category == FileCategory.REPORTS || category == FileCategory.EXPORTS) {
            return reportsBlobServiceClient != null;
        }
        return imagesBlobServiceClient != null;
    }
    
    private UploadResult uploadToLocalFallback(InputStream data, long length, String filename,
                                               String contentType, FileCategory category) {
        if (localFileStorageService == null) {
            throw new RuntimeException("Azure Blob Storage not configured and no local fallback available");
        }
        LOG.warn("Azure Blob Storage not configured. Using local file storage fallback for {} upload", category);
        
        byte[] bytes;
        try {
            bytes = data.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream", e);
        }
        
        MultipartFile multipartFile = new InMemoryMultipartFile(filename, filename, contentType, bytes);
        String localPath = switch (category) {
            case WAREHOUSES -> localFileStorageService.saveWarehouseImage(multipartFile);
            case PROFILES -> localFileStorageService.saveProfileImage(multipartFile);
            case DOCUMENTS -> localFileStorageService.saveDocumentFile(multipartFile);
            default -> localFileStorageService.saveEvidenceImage(multipartFile);
        };
        
        String blobName = localPath.substring(localPath.lastIndexOf("/") + 1);
        return new UploadResult(blobName, localPath, contentType, length);
    }
    
    @SuppressWarnings("null")
    private record InMemoryMultipartFile(
            String name,
            String originalFilename,
            String contentType,
            byte[] bytes
    ) implements MultipartFile {
        @Override
        public String getName() { return name; }
        @Override
        public String getOriginalFilename() { return originalFilename; }
        @Override
        public String getContentType() { return contentType; }
        @Override
        public boolean isEmpty() { return bytes.length == 0; }
        @Override
        public long getSize() { return bytes.length; }
        @Override
        public byte[] getBytes() { return bytes; }
        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    @Override
    public Optional<DownloadResult> download(String filename, FileCategory category) {
        BlobServiceClient client = getBlobServiceClient(category);
        if (client == null) {
            return downloadFromLocalFallback(filename, category);
        }

        try {
            String containerName = getContainerName(category);
            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(filename);

            if (!blobClient.exists()) {
                return Optional.empty();
            }

            byte[] content = blobClient.downloadContent().toBytes();
            ByteArrayResource resource = new ByteArrayResource(content);

            return Optional.of(new DownloadResult(resource, filename,
                    blobClient.getProperties().getContentType(), content.length));
        } catch (Exception ex) {
            LOG.warn("Azure download failed for {}. Falling back to local storage: {}", filename, ex.getMessage());
            return downloadFromLocalFallback(filename, category);
        }
    }

    @Override
    public boolean delete(String filename, FileCategory category) {
        try {
            BlobServiceClient client = getBlobServiceClient(category);
            if (client == null) {
                return deleteFromLocalFallback(filename, category);
            }
            String containerName = getContainerName(category);

            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(filename);

            boolean deleted = blobClient.deleteIfExists();
            if (deleted) {
                LOG.info("Deleted file {} from container {}", filename, containerName);
            }
            return deleted;
        } catch (Exception e) {
            LOG.error("Error deleting file {} from container {}: {}",
                    filename, getContainerName(category), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String filename, FileCategory category) {
        BlobServiceClient client = getBlobServiceClient(category);
        if (client == null) {
            return existsInLocalFallback(filename, category);
        }
        String containerName = getContainerName(category);

        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(filename);

        return blobClient.exists();
    }

    @Override
    public String getUrl(String filename, FileCategory category) {
        return "/api/v1/files/" + category.getApiPath() + "/" + filename;
    }

    @Override
    public String getUrlWithSas(String filename, FileCategory category, int expiryMinutes) {
        BlobServiceClient client = getBlobServiceClient(category);
        if (client == null) {
            return getUrl(filename, category);
        }
        try {
            String containerName = getContainerName(category);
            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(filename);
            int ttl = expiryMinutes > 0 ? expiryMinutes : sasExpiryMinutes;
            OffsetDateTime expiresOn = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(ttl);
            OffsetDateTime startsOn = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiresOn, permission)
                    .setStartTime(startsOn);
            String sasToken = blobClient.generateSas(sasValues);
            return blobClient.getBlobUrl() + "?" + sasToken;
        } catch (Exception ex) {
            LOG.warn("Could not generate SAS URL for {}; returning proxy URL: {}", filename, ex.getMessage());
            return getUrl(filename, category);
        }
    }

    @Override
    public boolean isContentTypeAllowed(String contentType, FileCategory category) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();

        return switch (category) {
            case PROFILES, WAREHOUSES, EVIDENCES -> ALLOWED_IMAGE_TYPES.contains(lower);
            case DOCUMENTS -> ALLOWED_DOCUMENT_TYPES.contains(lower);
            case REPORTS, EXPORTS -> ALLOWED_REPORT_TYPES.contains(lower);
        };
    }

    @Override
    public String generateBlobName(String originalFilename, FileCategory category) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return category.getApiPath() + "-" + UUID.randomUUID() + extension;
    }

    private BlobServiceClient getBlobServiceClient(FileCategory category) {
        return switch (category) {
            case REPORTS, EXPORTS -> reportsBlobServiceClient;
            default -> imagesBlobServiceClient;
        };
    }

    private String getContainerName(FileCategory category) {
        return switch (category) {
            case PROFILES -> profilesContainerName;
            case WAREHOUSES -> warehousesContainerName;
            case DOCUMENTS -> documentsContainerName;
            case EVIDENCES -> evidencesContainerName;
            case REPORTS -> reportsContainerName;
            case EXPORTS -> exportsContainerName;
        };
    }

    private String resolveContentType(String rawContentType, String filename) {
        if (rawContentType != null && !rawContentType.isBlank()) {
            return rawContentType;
        }
        String normalized = filename == null ? "" : filename.trim().toLowerCase();
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalized.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private void ensureContainerExists(BlobContainerClient containerClient) {
        if (!containerClient.exists()) {
            containerClient.create();
            LOG.info("Created missing Azure Blob container {}", containerClient.getBlobContainerName());
        }
    }

    private Optional<DownloadResult> downloadFromLocalFallback(String filename, FileCategory category) {
        if (localFileStorageService == null) {
            return Optional.empty();
        }
        try {
            java.nio.file.Path path = localFileStorageService.resolveForRead(category.getApiPath(), filename);
            byte[] content = java.nio.file.Files.readAllBytes(path);
            String contentType = java.nio.file.Files.probeContentType(path);
            return Optional.of(new DownloadResult(
                    new ByteArrayResource(content),
                    filename,
                    contentType,
                    content.length
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private boolean deleteFromLocalFallback(String filename, FileCategory category) {
        if (localFileStorageService == null) {
            return false;
        }
        try {
            java.nio.file.Path path = localFileStorageService.resolveForRead(category.getApiPath(), filename);
            return java.nio.file.Files.deleteIfExists(path);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean existsInLocalFallback(String filename, FileCategory category) {
        if (localFileStorageService == null) {
            return false;
        }
        try {
            localFileStorageService.resolveForRead(category.getApiPath(), filename);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
