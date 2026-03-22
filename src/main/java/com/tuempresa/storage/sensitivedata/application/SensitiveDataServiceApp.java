package com.tuempresa.storage.sensitivedata.application;

import com.tuempresa.storage.sensitivedata.application.dto.SensitiveDataDecryptRequest;
import com.tuempresa.storage.sensitivedata.application.dto.SensitiveDataResponse;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService.SensitiveFieldType;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensitiveDataServiceApp {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SensitiveDataService sensitiveDataService;

    public SensitiveDataServiceApp(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SensitiveDataService sensitiveDataService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sensitiveDataService = sensitiveDataService;
    }

    @Transactional(readOnly = true)
    public SensitiveDataResponse decryptSensitiveData(SensitiveDataDecryptRequest request, AuthUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_PASSWORD",
                    "La contrasena proporcionada es incorrecta."
            );
        }

        return new SensitiveDataResponse(
                user.getId(),
                decryptOrNull(user.getPhoneEncrypted(), SensitiveFieldType.PHONE),
                decryptOrNull(user.getAddressLineEncrypted(), SensitiveFieldType.ADDRESS),
                decryptOrNull(user.getPrimaryDocumentNumberEncrypted(), SensitiveFieldType.DNI),
                decryptOrNull(user.getSecondaryDocumentNumberEncrypted(), SensitiveFieldType.DNI),
                decryptOrNull(user.getEmergencyContactNameEncrypted(), SensitiveFieldType.EMERGENCY_CONTACT_NAME),
                decryptOrNull(user.getEmergencyContactPhoneEncrypted(), SensitiveFieldType.EMERGENCY_CONTACT_PHONE)
        );
    }

    private String decryptOrNull(String encrypted, SensitiveFieldType type) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return sensitiveDataService.decrypt(encrypted, type);
        } catch (Exception e) {
            return null;
        }
    }
}
