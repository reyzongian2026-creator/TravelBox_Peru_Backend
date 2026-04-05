package com.tuempresa.storage.users.infrastructure.out.persistence;

import com.tuempresa.storage.users.domain.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByCodeIgnoreCase(String code);

    Optional<ReferralCode> findByOwnerId(Long ownerId);
}
