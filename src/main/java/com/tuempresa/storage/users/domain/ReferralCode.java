package com.tuempresa.storage.users.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "referral_codes")
public class ReferralCode extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "reward_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rewardAmount = new BigDecimal("5.00");

    @Column(name = "max_uses")
    private Integer maxUses = 50;

    @Column(name = "current_uses", nullable = false)
    private int currentUses = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected ReferralCode() {
    }

    public ReferralCode(User owner, String code) {
        this.owner = owner;
        this.code = code.toUpperCase();
    }

    public boolean isUsable() {
        return active && (maxUses == null || currentUses < maxUses);
    }

    public void incrementUses() {
        this.currentUses++;
    }

    public User getOwner() {
        return owner;
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getRewardAmount() {
        return rewardAmount;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getCurrentUses() {
        return currentUses;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
