package com.tuempresa.storage.shared.infrastructure.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.UserDelegationKey;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AzureBlobStorageServiceImpl implements StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobStorageServiceImpl.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

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

    private BlobServiceClient imagesBlobServiceClient;
    private BlobServiceClient reportsBlobServiceClient;

    @PostConstruct
    public void init() {
        if (imagesConnectionString != null && !imagesConnectionString.isBlank()) {
            imagesBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(imagesConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for images initialized");
        } else {
            LOG.warn("Azure Blob Storage for images NOT configured");
        }

        if (reportsConnectionString != null && !reportsConnectionString.isBlank()) {
            reportsBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(reportsConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for reports initialized");
        } else {
            LOG.warn("Azure Blob Storage for reports NOT configured");
        }
    }

    @Override
    public UploadResult upload(MultipartFile file, FileCategory category) throws IOException {
        return upload(file.getInputStream(), file.getSize(), file.getOriginalFilename(),
                file.getContentType(), category);
    }

    @Override
    public UploadResult upload(InputStream data, long length, String filename,
                               String contentType, FileCategory category) {
        BlobServiceClient client = getBlobServiceClient(category);
        String containerName = getContainerName(category);
        String blobName = generateBlobName(filename, category);

        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        blobClient.upload(data, length, true);

        String url = getUrl(blobName, category);
        LOG.info("Uploaded file {} to container {} via Azure", blobName, containerName);

        return new UploadResult(blobName, url, contentType, length);
    }

    @Override
    public Optional<DownloadResult> download(String filename, FileCategory category) {
        BlobServiceClient client = getBlobServiceClient(category);
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
    }

    @Override
    public boolean delete(String filename, FileCategory category) {
        try {
            BlobServiceClient client = getBlobServiceClient(category);
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
        String containerName = getContainerName(category);

        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(filename);

        return blobClient.exists();
    }

    @Override
    public String getUrl(String filename, FileCategory category) {
        String urlBase = getUrlBase(category);
        if (urlBase != null && !urlBase.isBlank()) {
            return urlBase + "/" + getContainerName(category) + "/" + filename;
        }

        BlobServiceClient client = getBlobServiceClient(category);
        if (client == null) {
            return "/api/v1/files/" + category.getApiPath() + "/" + filename;
        }

        BlobContainerClient containerClient = client.getBlobContainerClient(getContainerName(category));
        BlobClient blobClient = containerClient.getBlobClient(filename);
        return blobClient.getBlobUrl();
    }

    @Override
    public String getUrlWithSas(String filename, FileCategory category, int expiryMinutes) {
        return getUrl(filename, category);
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

    private String getUrlBase(FileCategory category) {
        return switch (category) {
            case REPORTS, EXPORTS -> reportsUrlBase;
            default -> imagesUrlBase;
        };
    }
}