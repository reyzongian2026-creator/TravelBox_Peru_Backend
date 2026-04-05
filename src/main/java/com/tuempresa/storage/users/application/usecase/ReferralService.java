package com.tuempresa.storage.users.application.usecase;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.users.domain.ReferralCode;
import com.tuempresa.storage.users.domain.ReferralRedemption;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.ReferralCodeRepository;
import com.tuempresa.storage.users.infrastructure.out.persistence.ReferralRedemptionRepository;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ReferralService {

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRedemptionRepository referralRedemptionRepository;
    private final UserRepository userRepository;

    public ReferralService(ReferralCodeRepository referralCodeRepository,
            ReferralRedemptionRepository referralRedemptionRepository,
            UserRepository userRepository) {
        this.referralCodeRepository = referralCodeRepository;
        this.referralRedemptionRepository = referralRedemptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyReferralCode(AuthUserPrincipal principal) {
        ReferralCode code = referralCodeRepository.findByOwnerId(principal.getId())
                .orElse(null);
        if (code == null) {
            return Map.of("hasCode", false);
        }
        return Map.of(
                "hasCode", true,
                "code", code.getCode(),
                "rewardAmount", code.getRewardAmount(),
                "currentUses", code.getCurrentUses(),
                "maxUses", code.getMaxUses() != null ? code.getMaxUses() : -1);
    }

    @Transactional
    public Map<String, Object> generateMyReferralCode(AuthUserPrincipal principal) {
        referralCodeRepository.findByOwnerId(principal.getId())
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "REFERRAL_CODE_EXISTS",
                            "Ya tienes un codigo de referido activo.");
                });
        User owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));

        String code = generateUniqueCode();
        ReferralCode referralCode = new ReferralCode(owner, code);
        referralCodeRepository.save(referralCode);

        return Map.of(
                "code", referralCode.getCode(),
                "rewardAmount", referralCode.getRewardAmount());
    }

    @Transactional
    public Map<String, Object> redeemReferralCode(String code, AuthUserPrincipal principal) {
        ReferralCode referral = referralCodeRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_CODE_INVALID",
                        "El codigo de referido no existe."));

        if (!referral.isUsable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_CODE_EXHAUSTED",
                    "El codigo de referido ya no esta disponible.");
        }

        if (referral.getOwner().getId().equals(principal.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_SELF_USE",
                    "No puedes usar tu propio codigo de referido.");
        }

        if (referralRedemptionRepository.existsByReferralCodeIdAndReferredUserId(referral.getId(), principal.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REFERRAL_ALREADY_REDEEMED",
                    "Ya usaste este codigo de referido.");
        }

        User referredUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
        User ownerUser = referral.getOwner();

        // Credit both users
        referredUser.addWalletCredit(referral.getRewardAmount());
        ownerUser.addWalletCredit(referral.getRewardAmount());

        referral.incrementUses();
        referralRedemptionRepository.save(new ReferralRedemption(referral, referredUser));

        return Map.of(
                "message", "Codigo canjeado. Ambos reciben S/ " + referral.getRewardAmount() + " de credito.",
                "creditAmount", referral.getRewardAmount());
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = "INK" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            if (referralCodeRepository.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CODE_GENERATION_FAILED",
                "No se pudo generar un codigo unico.");
    }
}
