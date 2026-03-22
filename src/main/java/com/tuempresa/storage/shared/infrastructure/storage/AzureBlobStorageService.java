package com.tuempresa.storage.shared.infrastructure.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.ListBlobsOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AzureBlobStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobStorageService.class);

    @Value("${azure.storage.images.connection-string:}")
    private String imagesConnectionString;

    @Value("${azure.storage.images.container.images:travelbox-images}")
    private String imagesContainerName;

    @Value("${azure.storage.images.container.profiles:travelbox-profiles}")
    private String profilesContainerName;

    @Value("${azure.storage.images.container.warehouses:travelbox-warehouses}")
    private String warehousesContainerName;

    @Value("${azure.storage.images.container.documents:travelbox-documents}")
    private String documentsContainerName;

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

    private BlobServiceClient imagesBlobServiceClient;
    private BlobServiceClient reportsBlobServiceClient;

    @PostConstruct
    public void init() {
        if (imagesConnectionString != null && !imagesConnectionString.isBlank()) {
            imagesBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(imagesConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for images initialized. Container: {}", imagesContainerName);
        } else {
            LOG.warn("Azure Blob Storage for images NOT configured. Using LocalFileStorageService fallback.");
        }

        if (reportsConnectionString != null && !reportsConnectionString.isBlank()) {
            reportsBlobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(reportsConnectionString)
                    .buildClient();
            LOG.info("Azure Blob Storage for reports initialized. Container: {}", reportsContainerName);
        } else {
            LOG.warn("Azure Blob Storage for reports NOT configured.");
        }
    }

    public String uploadImage(MultipartFile file, String containerType) throws IOException {
        return uploadImage(file.getInputStream(), file.getSize(), file.getOriginalFilename(), file.getContentType(), containerType);
    }

    public String uploadImage(InputStream data, long length, String filename, String contentType, String containerType) {
        String containerName = getContainerName(containerType);
        String blobName = generateBlobName(filename, containerType);

        if (imagesBlobServiceClient == null) {
            LOG.error("Azure Blob Storage not initialized for images");
            throw new RuntimeException("Azure Blob Storage not configured");
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        blobClient.upload(data, length, true);

        LOG.info("Uploaded image {} to container {}", blobName, containerName);
        return getImageUrl(blobName, containerType);
    }

    public String uploadImageBase64(String base64Data, String filename, String contentType, String containerType) {
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
        InputStream inputStream = new ByteArrayInputStream(bytes);
        return uploadImage(inputStream, bytes.length, filename, contentType, containerType);
    }

    public String uploadReport(byte[] data, String filename, String contentType, boolean isExport) {
        String containerName = isExport ? exportsContainerName : reportsContainerName;
        String blobName = generateBlobName(filename, "reports");

        if (reportsBlobServiceClient == null) {
            LOG.error("Azure Blob Storage not initialized for reports");
            throw new RuntimeException("Azure Blob Storage for reports not configured");
        }

        BlobContainerClient containerClient = reportsBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        blobClient.upload(new ByteArrayInputStream(data), data.length, true);

        LOG.info("Uploaded report {} to container {}", blobName, containerName);
        return getReportUrl(blobName, isExport);
    }

    public InputStream downloadImage(String blobName, String containerType) {
        String containerName = getContainerName(containerType);

        if (imagesBlobServiceClient == null) {
            throw new RuntimeException("Azure Blob Storage not configured");
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        return blobClient.openInputStream();
    }

    public byte[] downloadImageAsBytes(String blobName, String containerType) {
        String containerName = getContainerName(containerType);

        if (imagesBlobServiceClient == null) {
            throw new RuntimeException("Azure Blob Storage not configured");
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        return blobClient.downloadContent().toBytes();
    }

    public byte[] downloadReport(String blobName, boolean isExport) {
        String containerName = isExport ? exportsContainerName : reportsContainerName;

        if (reportsBlobServiceClient == null) {
            throw new RuntimeException("Azure Blob Storage for reports not configured");
        }

        BlobContainerClient containerClient = reportsBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        return blobClient.downloadContent().toBytes();
    }

    public String getImageUrl(String blobName, String containerType) {
        if (imagesUrlBase != null && !imagesUrlBase.isBlank()) {
            return imagesUrlBase + "/" + getContainerName(containerType) + "/" + blobName;
        }

        if (imagesBlobServiceClient == null) {
            return "/api/v1/files/" + containerType + "/" + blobName;
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(getContainerName(containerType));
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.getBlobUrl();
    }

    public String getReportUrl(String blobName, boolean isExport) {
        String containerName = isExport ? exportsContainerName : reportsContainerName;

        if (reportsUrlBase != null && !reportsUrlBase.isBlank()) {
            return reportsUrlBase + "/" + containerName + "/" + blobName;
        }

        if (reportsBlobServiceClient == null) {
            return "/api/v1/files/reports/" + blobName;
        }

        BlobContainerClient containerClient = reportsBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.getBlobUrl();
    }

    public String getThumbnailUrl(String blobName, String containerType) {
        return getImageUrl(blobName, containerType) + "?thumbnail=true";
    }

    public void deleteImage(String blobName, String containerType) {
        String containerName = getContainerName(containerType);

        if (imagesBlobServiceClient == null) {
            throw new RuntimeException("Azure Blob Storage not configured");
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.delete();

        LOG.info("Deleted image {} from container {}", blobName, containerName);
    }

    public void deleteReport(String blobName, boolean isExport) {
        String containerName = isExport ? exportsContainerName : reportsContainerName;

        if (reportsBlobServiceClient == null) {
            throw new RuntimeException("Azure Blob Storage for reports not configured");
        }

        BlobContainerClient containerClient = reportsBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.delete();

        LOG.info("Deleted report {} from container {}", blobName, containerName);
    }

    public boolean imageExists(String blobName, String containerType) {
        String containerName = getContainerName(containerType);

        if (imagesBlobServiceClient == null) {
            return false;
        }

        BlobContainerClient containerClient = imagesBlobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return blobClient.exists();
    }

    public String generateBlobName(String originalFilename, String prefix) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return prefix + "-" + UUID.randomUUID().toString() + extension;
    }

    private String getContainerName(String containerType) {
        return switch (containerType.toLowerCase()) {
            case "profiles", "profile" -> profilesContainerName;
            case "warehouses", "warehouse" -> warehousesContainerName;
            case "documents", "document" -> documentsContainerName;
            case "reports", "report" -> reportsContainerName;
            case "exports", "export" -> exportsContainerName;
            default -> imagesContainerName;
        };
    }
}
