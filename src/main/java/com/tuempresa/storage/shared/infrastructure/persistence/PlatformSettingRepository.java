package com.tuempresa.storage.shared.infrastructure.persistence;

import com.tuempresa.storage.shared.domain.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
    List<PlatformSetting> findByKeyStartingWith(String prefix);
}
