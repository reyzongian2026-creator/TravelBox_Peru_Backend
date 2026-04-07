package com.tuempresa.storage.shared.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String QR_PNG_CACHE = "qrPng";
    public static final String QR_DATA_URL_CACHE = "qrDataUrl";
    public static final String WAREHOUSE_BY_ID_CACHE = "warehouseById";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache(QR_PNG_CACHE, Duration.ofHours(24), 500),
                buildCache(QR_DATA_URL_CACHE, Duration.ofHours(24), 500),
                buildCache(WAREHOUSE_BY_ID_CACHE, Duration.ofMinutes(10), 200)));
        return manager;
    }

    private CaffeineCache buildCache(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }
}
