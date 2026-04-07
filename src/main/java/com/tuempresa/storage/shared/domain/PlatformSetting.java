package com.tuempresa.storage.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_settings")
public class PlatformSetting {

    @Id
    @Column(name = "setting_key", length = 128, nullable = false)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PlatformSetting() {
    }

    public PlatformSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
