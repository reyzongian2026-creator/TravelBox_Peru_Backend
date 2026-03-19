package com.tuempresa.storage.auth.application.usecase;

import com.tuempresa.storage.auth.application.dto.AuthTokenResponse;
import com.tuempresa.storage.auth.application.dto.AuthUserSummaryResponse;
import com.tuempresa.storage.auth.application.dto.EmailVerificationResponse;
import com.tuempresa.storage.auth.application.dto.FirebaseSocialAuthRequest;
import com.tuempresa.storage.auth.application.dto.LoginRequest;
import com.tuempresa.storage.auth.application.dto.LogoutRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetConfirmRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetResponse;
import com.tuempresa.storage.auth.application.dto.RefreshRequest;
import com.tuempresa.storage.auth.application.dto.RegisterRequest;
import com.tuempresa.storage.auth.application.dto.VerifyEmailRequest;
import com.tuempresa.storage.auth.domain.RefreshToken;
import com.tuempresa.storage.auth.infrastructure.out.persistence.RefreshTokenRepository;
import com.tuempresa.storage.firebase.application.FirebaseAdminService;
import com.tuempresa.storage.firebase.application.FirebaseClientIdentity;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.JwtTokenProvider;
import com.tuempresa.storage.users.domain.AuthProvider;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("es", "en", "de", "fr", "it", "pt");
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final CustomerEmailService customerEmailService;
    private final FirebaseAdminService firebaseAdminService;
    private final String emailProvider;
    private final boolean exposeCodePreview;
    private final boolean allowInternalSelfRegister;

    public AuthService(
            ObjectProvider<AuthenticationManager> authenticationManagerProvider,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            CustomerEmailService customerEmailService,
            FirebaseAdminService firebaseAdminService,
            @Value("${app.auth.email-provider:mock}") String emailProvider,
            @Value("${app.auth.expose-code-preview:false}") boolean exposeCodePreview,
            @Value("${app.auth.allow-internal-self-register:false}") boolean allowInternalSelfRegister
    ) {
        this.authenticationManager = authenticationManagerProvider.getIfAvailable();
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.customerEmailService = customerEmailService;
        this.firebaseAdminService = firebaseAdminService;
        this.emailProvider = emailProvider == null ? "mock" : emailProvider.trim().toLowerCase(Locale.ROOT);
        this.exposeCodePreview = exposeCodePreview;
        this.allowInternalSelfRegister = allowInternalSelfRegister;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(User::isSocialClientAuth)
                .ifPresent(user -> {
                    throw new ApiException(
                            HttpStatus.UNAUTHORIZED,
                            "AUTH_USE_SOCIAL_LOGIN",
                            "Este cliente debe ingresar con Google o Facebook."
                    );
                });

        Authentication authentication;
        try {
            if (authenticationManager != null) {
                authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.email(), request.password())
                );
            } else {
                authentication = authenticateLocally(request);
            }
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Credenciales invalidas.");
        }
        AuthUserPrincipal principal = (AuthUserPrincipal) authentication.getPrincipal();
        User user = requireUser(principal.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken(principal);
        refreshTokenRepository.save(RefreshToken.of(refreshTokenValue, user, jwtTokenProvider.refreshTokenExpiry()));
        return buildAuthTokenResponse(user, principal, accessToken, refreshTokenValue, null);
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_REQUIRED", "Debes enviar un correo valido.");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "El email ya esta registrado.");
        }
        if (request.confirmPassword() != null && !request.password().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_MISMATCH", "La confirmacion de contrasena no coincide.");
        }
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_TERMS_REQUIRED", "Debes aceptar terminos y condiciones.");
        }

        String[] names = resolveNames(request);
        if (names[0] == null || names[1] == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_NAME_REQUIRED", "Debes enviar nombres y apellidos.");
        }
        if (normalize(request.nationality()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_NATIONALITY_REQUIRED", "Debes seleccionar nacionalidad.");
        }
        String preferredLanguage = normalizePreferredLanguage(request.preferredLanguage());
        if (preferredLanguage == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_LANGUAGE_REQUIRED", "Debes seleccionar idioma preferido.");
        }
        String phone = normalizePhone(request.phone());
        if (phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PHONE_REQUIRED", "Debes enviar telefono.");
        }

        Set<Role> registrationRoles = resolveRegistrationRoles(normalizedEmail);
        User user = User.of(
                (names[0] + " " + names[1]).trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                phone,
                registrationRoles
        );
        user.applyRegistrationDetails(
                names[0],
                names[1],
                request.nationality(),
                preferredLanguage,
                phone,
                true,
                request.profilePhotoPath()
        );
        user.markManagedByAdmin(isInternalRoleSet(registrationRoles));
        if (isInternalRoleSet(registrationRoles)) {
            user.markEmailVerified();
        }
        if (registrationRoles.contains(Role.COURIER)) {
            user.updateVehiclePlate("TBX-" + Math.abs(normalizedEmail.hashCode() % 9000 + 1000));
        }
        user = userRepository.save(user);
        String verificationCodePreview = issueVerificationCode(user);
        String firebaseUid = firebaseAdminService.syncUserAccount(user, request.password());
        if (firebaseUid != null && !firebaseUid.equals(user.getFirebaseUid())) {
            user.linkFirebaseIdentity(AuthProvider.LOCAL, firebaseUid);
        }
        user = userRepository.save(user);
        firebaseAdminService.mirrorClientProfile(user);

        AuthUserPrincipal principal = AuthUserPrincipal.from(user);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken(principal);
        refreshTokenRepository.save(RefreshToken.of(refreshTokenValue, user, jwtTokenProvider.refreshTokenExpiry()));
        return buildAuthTokenResponse(user, principal, accessToken, refreshTokenValue, verificationCodePreview);
    }

    @Transactional
    public AuthTokenResponse firebaseSocialLogin(FirebaseSocialAuthRequest request) {
        FirebaseClientIdentity identity = firebaseAdminService.verifyClientIdToken(request.idToken());
        AuthProvider expectedProvider = resolveRequestedProvider(request.provider());
        if (identity.authProvider() != expectedProvider) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIREBASE_PROVIDER_MISMATCH",
                    "El token no corresponde al proveedor solicitado."
            );
        }

        String normalizedEmail = normalizeEmail(identity.email());
        if (normalizedEmail == null && expectedProvider == AuthProvider.FIREBASE_FACEBOOK) {
            normalizedEmail = buildFacebookFallbackEmail(identity.uid());
        }
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIREBASE_EMAIL_REQUIRED", "Firebase no devolvio un correo valido.");
        }
        final String resolvedEmail = normalizedEmail;

        User user = userRepository.findByFirebaseUid(identity.uid())
                .or(() -> userRepository.findByEmailIgnoreCase(resolvedEmail))
                .map(existing -> updateFirebaseClient(existing, identity))
                .orElseGet(() -> createFirebaseClient(identity, resolvedEmail, request));

        firebaseAdminService.mirrorClientProfile(user);

        AuthUserPrincipal principal = AuthUserPrincipal.from(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken(principal);
        refreshTokenRepository.save(RefreshToken.of(refreshTokenValue, user, jwtTokenProvider.refreshTokenExpiry()));
        return buildAuthTokenResponse(user, principal, accessToken, refreshTokenValue, null);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID", "Refresh token invalido."));
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshToken.revoke();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_EXPIRED", "Refresh token expirado.");
        }
        User user = refreshToken.getUser();
        AuthUserPrincipal principal = AuthUserPrincipal.from(user);
        refreshToken.revoke();
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal);
        refreshTokenRepository.save(RefreshToken.of(newRefreshToken, user, jwtTokenProvider.refreshTokenExpiry()));
        return buildAuthTokenResponse(user, principal, accessToken, newRefreshToken, null);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken())
                .ifPresent(token -> refreshTokenRepository.revokeAllByUserId(token.getUser().getId()));
    }

    @Transactional
    public EmailVerificationResponse verifyEmail(VerifyEmailRequest request, AuthUserPrincipal principal) {
        User user = resolveUserForVerification(request, principal);
        if (user.isEmailVerified()) {
            return new EmailVerificationResponse(true, "El correo ya estaba verificado.", null, null);
        }
        boolean verified = user.verifyEmailCode(request.code(), Instant.now());
        if (!verified) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_VERIFICATION_INVALID", "Codigo de verificacion invalido o expirado.");
        }
        userRepository.save(user);
        return new EmailVerificationResponse(true, "Correo verificado correctamente.", null, null);
    }

    @Transactional
    public EmailVerificationResponse resendVerification(AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        if (user.isEmailVerified()) {
            return new EmailVerificationResponse(true, "El correo ya esta verificado.", null, null);
        }
        String verificationCodePreview = issueVerificationCode(user);
        userRepository.save(user);
        return new EmailVerificationResponse(
                false,
                resendVerificationMessage(verificationCodePreview),
                verificationCodePreview,
                user.getEmailVerificationExpiresAt()
        );
    }

    @Transactional
    public PasswordResetResponse requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_REQUIRED", "Debes enviar un correo valido.");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.isSocialClientAuth()) {
            return new PasswordResetResponse(
                    "Si el correo existe, se envio un codigo de recuperacion.",
                    null,
                    null
            );
        }

        String resetCodePreview = issuePasswordResetCode(user);
        userRepository.save(user);

        return new PasswordResetResponse(
                passwordResetMessage(resetCodePreview),
                resetCodePreview,
                user.getPasswordResetExpiresAt()
        );
    }

    @Transactional
    public PasswordResetResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_REQUIRED", "Debes enviar un correo valido.");
        }
        if (request.confirmPassword() != null && !request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_MISMATCH", "La confirmacion de contrasena no coincide.");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "AUTH_PASSWORD_RESET_INVALID",
                        "Codigo de recuperacion invalido o expirado."
                ));
        if (user.isSocialClientAuth()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTH_PASSWORD_RESET_SOCIAL_ONLY",
                    "Esta cuenta usa acceso social. Debes ingresar con Google/Facebook."
            );
        }
        if (!user.verifyPasswordResetCode(request.code(), Instant.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTH_PASSWORD_RESET_INVALID",
                    "Codigo de recuperacion invalido o expirado."
            );
        }

        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        String firebaseUid = firebaseAdminService.syncUserAccount(user, request.newPassword());
        if (firebaseUid != null && !firebaseUid.equals(user.getFirebaseUid())) {
            user.linkFirebaseIdentity(AuthProvider.LOCAL, firebaseUid);
        }
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        customerEmailService.sendPasswordChangedConfirmation(user);

        return new PasswordResetResponse("Contrasena actualizada correctamente.", null, null);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
    }

    private User resolveUserForVerification(VerifyEmailRequest request, AuthUserPrincipal principal) {
        if (principal != null) {
            return requireUser(principal.getId());
        }
        String normalizedEmail = normalizeEmail(request.email());
        if (normalizedEmail == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTH_EMAIL_REQUIRED",
                    "Debes enviar el correo para verificar sin sesion activa."
            );
        }
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "AUTH_EMAIL_VERIFICATION_INVALID",
                        "Codigo de verificacion invalido o expirado."
                ));
    }

    private Authentication authenticateLocally(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (normalizedEmail == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Credenciales invalidas.");
        }
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Credenciales invalidas."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Credenciales invalidas.");
        }
        AuthUserPrincipal principal = AuthUserPrincipal.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private AuthTokenResponse buildAuthTokenResponse(
            User user,
            AuthUserPrincipal principal,
            String accessToken,
            String refreshToken,
            String verificationCodePreview
    ) {
        return new AuthTokenResponse(
                user.getId(),
                user.getEmail(),
                principal.roleNames(),
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.accessTokenExpiry(),
                toSummary(user),
                user.isEmailVerified(),
                user.isProfileCompleted(),
                !user.isEmailVerified(),
                verificationCodePreview,
                accountState(user)
        );
    }

    private AuthUserSummaryResponse toSummary(User user) {
        return new AuthUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getNationality(),
                user.getPreferredLanguage(),
                user.getAuthProvider().name(),
                user.isManagedByAdmin(),
                user.isClientSelfManaged(),
                user.getVehiclePlate(),
                user.getProfilePhotoPath(),
                user.isEmailVerified(),
                user.isProfileCompleted(),
                user.remainingEmailChanges(),
                user.remainingPhoneChanges(),
                user.remainingDocumentChanges(),
                user.getRoles().stream().map(Role::name).sorted(Comparator.naturalOrder()).toList(),
                user.getWarehouseAssignments().stream().map(warehouse -> warehouse.getId()).sorted().toList(),
                user.getWarehouseAssignments().stream().map(warehouse -> warehouse.getName()).sorted(String::compareToIgnoreCase).toList()
        );
    }

    private String accountState(User user) {
        if (!user.isEmailVerified()) {
            return "PENDING_EMAIL_VERIFICATION";
        }
        if (!user.isProfileCompleted()) {
            return "PROFILE_INCOMPLETE";
        }
        return "ACTIVE";
    }

    private String issueVerificationCode(User user) {
        String verificationCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        user.prepareEmailVerification(verificationCode, expiresAt);
        if (user.getId() != null) {
            notificationService.notifyUser(
                    user.getId(),
                    "EMAIL_VERIFICATION",
                    "Verifica tu correo",
                    "Usa el codigo para verificar tu cuenta TravelBox.",
                    java.util.Map.of(
                            "email", user.getEmail(),
                            "code", verificationCode,
                            "expiresAt", expiresAt
                    )
            );
        }
        customerEmailService.sendEmailVerification(
                user,
                verificationCode,
                expiresAt,
                "verificar tu cuenta"
        );
        return shouldExposeCodePreview() ? verificationCode : null;
    }

    private String issuePasswordResetCode(User user) {
        String resetCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        user.preparePasswordReset(resetCode, expiresAt);
        if (user.getId() != null) {
            notificationService.notifyUser(
                    user.getId(),
                    "PASSWORD_RESET",
                    "Recuperacion de contrasena",
                    "Usa el codigo para actualizar tu contrasena de TravelBox.",
                    java.util.Map.of(
                            "email", user.getEmail(),
                            "code", resetCode,
                            "expiresAt", expiresAt
                    )
            );
        }
        customerEmailService.sendPasswordResetCode(user, resetCode, expiresAt);
        return shouldExposeCodePreview() ? resetCode : null;
    }

    private boolean shouldExposeCodePreview() {
        return "mock".equals(emailProvider) || exposeCodePreview;
    }

    private String resendVerificationMessage(String verificationCodePreview) {
        if (verificationCodePreview == null || verificationCodePreview.isBlank()) {
            return "Se envio un nuevo codigo de verificacion.";
        }
        return "Se envio un nuevo codigo de verificacion. Si el correo no llega, usa el codigo mostrado en pantalla.";
    }

    private String passwordResetMessage(String resetCodePreview) {
        if (resetCodePreview == null || resetCodePreview.isBlank()) {
            return "Se envio un codigo de recuperacion.";
        }
        return "Se envio un codigo de recuperacion. Si el correo no llega, usa el codigo mostrado en pantalla.";
    }

    private User updateFirebaseClient(User existing, FirebaseClientIdentity identity) {
        if (!existing.getRoles().contains(Role.CLIENT)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FIREBASE_EMAIL_CONFLICT",
                    "El correo ya pertenece a un usuario interno administrado por la plataforma."
            );
        }
        existing.linkFirebaseIdentity(identity.authProvider(), identity.uid());
        existing.markEmailVerified();
        existing.setActive(true);
        if (normalize(existing.getProfilePhotoPath()) == null && normalize(identity.photoUrl()) != null) {
            existing.updateProfile(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    identity.photoUrl(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return userRepository.save(existing);
    }

    private User createFirebaseClient(
            FirebaseClientIdentity identity,
            String normalizedEmail,
            FirebaseSocialAuthRequest request
    ) {
        if (!Boolean.TRUE.equals(request.termsAccepted())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_TERMS_REQUIRED", "Debes aceptar terminos y condiciones.");
        }
        String[] names = splitDisplayName(
                normalize(request.displayName()) != null ? request.displayName() : identity.displayName(),
                normalizedEmail
        );
        User user = User.of(
                (names[0] + " " + names[1]).trim(),
                normalizedEmail,
                passwordEncoder.encode(generateSystemPassword()),
                null,
                Set.of(Role.CLIENT)
        );
        user.linkFirebaseIdentity(identity.authProvider(), identity.uid());
        user.applyRegistrationDetails(
                names[0],
                names[1],
                "Peru",
                "es",
                null,
                true,
                normalize(request.profilePhotoUrl()) != null ? request.profilePhotoUrl() : identity.photoUrl()
        );
        user.markEmailVerified();
        user.setActive(true);
        return userRepository.save(user);
    }

    private AuthProvider resolveRequestedProvider(String provider) {
        String normalized = normalize(provider);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIREBASE_PROVIDER_REQUIRED", "Debes indicar el proveedor.");
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "GOOGLE" -> AuthProvider.FIREBASE_GOOGLE;
            case "FACEBOOK" -> AuthProvider.FIREBASE_FACEBOOK;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIREBASE_PROVIDER_UNSUPPORTED",
                    "Solo Google o Facebook estan permitidos."
            );
        };
    }

    private String[] resolveNames(RegisterRequest request) {
        String firstName = normalize(request.firstName());
        String lastName = normalize(request.lastName());
        if (firstName != null && lastName != null) {
            return new String[]{firstName, lastName};
        }
        String name = normalize(request.name());
        if (name == null) {
            return new String[]{firstName, lastName};
        }
        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            return new String[]{parts[0], lastName};
        }
        int middle = Math.max(1, parts.length / 2);
        String resolvedFirst = String.join(" ", Arrays.copyOfRange(parts, 0, middle));
        String resolvedLast = String.join(" ", Arrays.copyOfRange(parts, middle, parts.length));
        return new String[]{resolvedFirst, resolvedLast};
    }

    private String[] splitDisplayName(String displayName, String fallbackEmail) {
        String normalized = normalize(displayName);
        if (normalized == null) {
            String base = fallbackEmail == null ? "Cliente TravelBox" : fallbackEmail.split("@")[0];
            normalized = base.replace('.', ' ').replace('_', ' ').trim();
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return new String[]{parts[0], "TravelBox"};
        }
        int middle = Math.max(1, parts.length / 2);
        String first = String.join(" ", Arrays.copyOfRange(parts, 0, middle));
        String last = String.join(" ", Arrays.copyOfRange(parts, middle, parts.length));
        return new String[]{normalize(first), normalize(last)};
    }

    private String generateSystemPassword() {
        return "Fb!" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "9a";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String buildFacebookFallbackEmail(String uid) {
        String normalizedUid = normalize(uid);
        if (normalizedUid == null) {
            return null;
        }
        String safe = normalizedUid.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase(Locale.ROOT);
        if (safe.isEmpty()) {
            return null;
        }
        return "fb-" + safe + "@facebook.local";
    }

    private String normalizePreferredLanguage(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        return SUPPORTED_LANGUAGES.contains(lowered) ? lowered : null;
    }

    private String normalizePhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String compact = normalized.replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
        if (!compact.startsWith("+")) {
            return null;
        }
        return INTERNATIONAL_PHONE_PATTERN.matcher(compact).matches() ? compact : null;
    }

    private Set<Role> resolveRegistrationRoles(String normalizedEmail) {
        if (!allowInternalSelfRegister || normalizedEmail == null) {
            return Set.of(Role.CLIENT);
        }
        String email = normalizedEmail.toLowerCase(Locale.ROOT);
        if (email.startsWith("admin@")) {
            return Set.of(Role.ADMIN, Role.SUPPORT);
        }
        if (email.startsWith("operator@")) {
            return Set.of(Role.OPERATOR);
        }
        if (email.startsWith("courier@")) {
            return Set.of(Role.COURIER);
        }
        if (email.startsWith("support@")) {
            return Set.of(Role.SUPPORT);
        }
        return Set.of(Role.CLIENT);
    }

    private boolean isInternalRoleSet(Set<Role> roles) {
        return roles.stream().anyMatch(role -> role != Role.CLIENT);
    }
}
