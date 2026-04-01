package com.tuempresa.storage.auth.application;

import com.tuempresa.storage.auth.application.dto.EmailChangeRequest;
import com.tuempresa.storage.auth.application.dto.EmailChangeResponse;
import com.tuempresa.storage.auth.application.dto.EmailChangeVerifyRequest;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmailChangeService {

    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerEmailService customerEmailService;
    private final SensitiveDataService sensitiveDataService;

    public EmailChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CustomerEmailService customerEmailService,
            SensitiveDataService sensitiveDataService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerEmailService = customerEmailService;
        this.sensitiveDataService = sensitiveDataService;
    }

    @Transactional
    public EmailChangeResponse initiateEmailChange(EmailChangeRequest request, AuthUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));

        if (!user.isClientSelfManaged()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_CHANGE_ADMIN_MANAGED",
                    "Este perfil es administrado por un administrador y no puede cambiar el correo desde esta cuenta."
            );
        }

        if (user.getAuthProvider() != com.tuempresa.storage.users.domain.AuthProvider.LOCAL) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_CHANGE_NOT_ALLOWED",
                    "El correo no puede cambiarse para cuentas sociales."
            );
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_PASSWORD",
                    "La contrasena proporcionada es incorrecta."
            );
        }

        String normalizedEmail = request.newEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "El correo electronico ya esta registrado."
            );
        }

        if (user.remainingEmailChanges() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_CHANGE_LIMIT_REACHED",
                    "Ya alcanzaste el maximo de cambios permitidos para el correo."
            );
        }

        String verificationCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        user.prepareEmailVerification(verificationCode, expiresAt);
        user.setEmail(normalizedEmail);
        user.incrementEmailChangeCount();
        userRepository.save(user);

        String maskedEmail = sensitiveDataService.maskEmail(normalizedEmail);
        customerEmailService.sendEmailChangeVerification(
                user,
                maskedEmail,
                verificationCode,
                expiresAt
        );

        return new EmailChangeResponse(
                user.getId(),
                normalizedEmail,
                maskedEmail,
                expiresAt,
                MAX_CODE_ATTEMPTS
        );
    }

    @Transactional
    public EmailChangeResponse verifyEmailChange(EmailChangeVerifyRequest request, AuthUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));

        String normalizedEmail = request.email().trim().toLowerCase();
        if (!normalizedEmail.equals(user.getEmail())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_MISMATCH",
                    "El correo electronico no coincide con el cambio solicitado."
            );
        }

        if (!user.verifyEmailCode(request.verificationCode(), Instant.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_VERIFICATION_CODE",
                    "El codigo de verificacion es invalido o ha expirado."
            );
        }

        user.markEmailVerified();
        userRepository.save(user);

        customerEmailService.sendEmailChangeConfirmation(user);

        String maskedEmail = sensitiveDataService.maskEmail(user.getEmail());
        return new EmailChangeResponse(
                user.getId(),
                user.getEmail(),
                maskedEmail,
                null,
                0
        );
    }
}
