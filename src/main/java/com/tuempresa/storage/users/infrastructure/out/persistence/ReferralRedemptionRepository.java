package com.tuempresa.storage.users.infrastructure.out.persistence;

import com.tuempresa.storage.users.domain.ReferralRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRedemptionRepository extends JpaRepository<ReferralRedemption, Long> {
    boolean existsByReferralCodeIdAndReferredUserId(Long referralCodeId, Long referredUserId);
}
