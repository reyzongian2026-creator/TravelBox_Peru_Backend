package com.tuempresa.storage.shared.infrastructure.storage;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class StorageFallbackConfig {

    private final AzureBlobStorageServiceImpl azureBlobStorageService;
    private final LocalFileStorageService localFileStorageService;

    public StorageFallbackConfig(
            AzureBlobStorageServiceImpl azureBlobStorageService,
            LocalFileStorageService localFileStorageService
    ) {
        this.azureBlobStorageService = azureBlobStorageService;
        this.localFileStorageService = localFileStorageService;
    }

    @PostConstruct
    public void configureFallback() {
        azureBlobStorageService.setLocalFileStorageServiceFallback(localFileStorageService);
    }
}
