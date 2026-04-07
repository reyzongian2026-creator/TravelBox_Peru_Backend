package com.tuempresa.storage.shared.application;

import com.tuempresa.storage.shared.domain.PlatformSetting;
import com.tuempresa.storage.shared.infrastructure.persistence.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlatformSettingService {

    private final PlatformSettingRepository repository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public PlatformSettingService(PlatformSettingRepository repository) {
        this.repository = repository;
    }

    public Optional<String> get(String key) {
        String cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return repository.findById(key).map(s -> {
            cache.put(key, s.getValue());
            return s.getValue();
        });
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).filter(StringUtils::hasText).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        PlatformSetting setting = repository.findById(key)
                .orElse(new PlatformSetting(key, value));
        setting.setValue(value);
        repository.save(setting);
        cache.put(key, value);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getByPrefix(String prefix) {
        List<PlatformSetting> settings = repository.findByKeyStartingWith(prefix);
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (PlatformSetting s : settings) {
            result.put(s.getKey(), s.getValue());
        }
        return result;
    }

    public void evictCache(String key) {
        cache.remove(key);
    }
}
