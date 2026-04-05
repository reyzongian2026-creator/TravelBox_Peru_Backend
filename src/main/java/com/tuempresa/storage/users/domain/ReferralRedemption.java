package com.tuempresa.storage.users.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "referral_redemptions", uniqueConstraints = @UniqueConstraint(columnNames = { "referral_code_id",
        "referred_user_id" }))
public class ReferralRedemption extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_code_id", nullable = false)
    private ReferralCode referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false)
    private User referredUser;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    protected ReferralRedemption() {
    }

    public ReferralRedemption(ReferralCode referralCode, User referredUser) {
        this.referralCode = referralCode;
        this.referredUser = referredUser;
        this.redeemedAt = Instant.now();
    }

    public ReferralCode getReferralCode() {
        return referralCode;
    }

    public User getReferredUser() {
        return referredUser;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
