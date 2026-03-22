package com.tuempresa.storage.profile.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tuempresa.storage.firebase.application.FirebaseAdminService;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.profile.application.dto.OnboardingStatusResponse;
import com.tuempresa.storage.profile.application.dto.UpdateProfileRequest;
import com.tuempresa.storage.profile.application.dto.UserProfileResponse;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService.SensitiveFieldType;
import com.tuempresa.storage.shared.infrastructure.storage.AzureBlobStorageService;
import com.tuempresa.storage.users.domain.AuthProvider;
import com.tuempresa.storage.users.domain.DocumentType;
import com.tuempresa.storage.users.domain.Gender;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class ProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("es", "en", "de", "fr", "it", "pt");
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final CustomerEmailService customerEmailService;
    private final FirebaseAdminService firebaseAdminService;
    private final AzureBlobStorageService azureBlobStorageService;
    private final SensitiveDataService sensitiveDataService;
    private final String emailProvider;

    public ProfileService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            CustomerEmailService customerEmailService,
            FirebaseAdminService firebaseAdminService,
            AzureBlobStorageService azureBlobStorageService,
            SensitiveDataService sensitiveDataService,
            @Value("${app.auth.email-provider:mock}") String emailProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.customerEmailService = customerEmailService;
        this.firebaseAdminService = firebaseAdminService;
        this.azureBlobStorageService = azureBlobStorageService;
        this.sensitiveDataService = sensitiveDataService;
        this.emailProvider = emailProvider == null ? "mock" : emailProvider.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse myProfile(AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        return toResponse(user, null);
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse myOnboardingStatus(AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        return new OnboardingStatusResponse(user.getId(), user.isOnboardingCompleted());
    }

    @Transactional
    public OnboardingStatusResponse completeMyOnboarding(AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        if (!user.isOnboardingCompleted()) {
            user.markOnboardingCompleted();
            userRepository.save(user);
        }
        return new OnboardingStatusResponse(user.getId(), true);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request, AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        ensureSelfManagedClient(user, principal);
        ProfileSnapshot before = ProfileSnapshot.from(user);

        String normalizedEmail = normalize(request.email());
        String normalizedPreferredLanguage = normalizeSupportedLanguage(request.preferredLanguage());
        if (request.preferredLanguage() != null && normalizedPreferredLanguage == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_LANGUAGE_INVALID", "Idioma no soportado.");
        }
        String normalizedPhone = normalizePhone(request.phone());
        if (request.phone() != null && normalizedPhone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_PHONE_INVALID", "Telefono invalido en formato internacional.");
        }
        String normalizedEmergencyPhone = normalizePhone(request.emergencyContactPhone());
        if (request.emergencyContactPhone() != null && normalizedEmergencyPhone == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_EMERGENCY_PHONE_INVALID",
                    "Telefono de emergencia invalido en formato internacional."
            );
        }

        DocumentType nextPrimaryDocumentType = parseDocumentType(request.documentType());
        String normalizedPrimaryDocumentNumber = normalize(request.documentNumber());

        boolean emailChanged = request.email() != null && isTrackedFieldChange(user.getEmail(), normalizedEmail);
        boolean phoneChanged = request.phone() != null && isTrackedFieldChange(user.getPhone(), normalizedPhone);
        boolean documentChanged = (request.documentType() != null || request.documentNumber() != null)
                && isTrackedDocumentChange(
                user.getPrimaryDocumentType(),
                user.getPrimaryDocumentNumber(),
                nextPrimaryDocumentType,
                normalizedPrimaryDocumentNumber
        );
        boolean sensitiveChange = emailChanged || phoneChanged || documentChanged;

        if (sensitiveChange
                && user.getAuthProvider() == AuthProvider.LOCAL
                && !passwordMatches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PROFILE_REAUTH_REQUIRED",
                    "Para cambiar correo, telefono o documento debes confirmar tu contrasena actual."
            );
        }
        if (emailChanged && user.remainingEmailChanges() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_EMAIL_CHANGE_LIMIT_REACHED",
                    "Ya alcanzaste el maximo de cambios permitidos para el correo."
            );
        }
        if (phoneChanged && user.remainingPhoneChanges() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_PHONE_CHANGE_LIMIT_REACHED",
                    "Ya alcanzaste el maximo de cambios permitidos para el telefono."
            );
        }
        if (documentChanged && user.remainingDocumentChanges() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_DOCUMENT_CHANGE_LIMIT_REACHED",
                    "Ya alcanzaste el maximo de cambios permitidos para el documento."
            );
        }
        if (emailChanged && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new ApiException(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_EXISTS", "El email ya esta registrado.");
                    });
        }

        user.updateProfile(
                normalize(request.firstName()),
                normalize(request.lastName()),
                normalizedEmail,
                normalizedPhone,
                normalize(request.nationality()),
                normalizedPreferredLanguage,
                request.birthDate(),
                parseGender(request.gender()),
                normalize(request.profilePhotoPath()),
                normalize(request.address()),
                normalize(request.city()),
                normalize(request.country()),
                nextPrimaryDocumentType,
                normalizedPrimaryDocumentNumber,
                parseDocumentType(request.secondaryDocumentType()),
                normalize(request.secondaryDocumentNumber()),
                normalize(request.emergencyContactName()),
                normalizedEmergencyPhone
        );

        encryptSensitiveFields(user);

        if (emailChanged) {
            user.incrementEmailChangeCount();
        }
        if (phoneChanged) {
            user.incrementPhoneChangeCount();
        }
        if (documentChanged) {
            user.incrementDocumentChangeCount();
        }

        List<String> changedFields = before.changedFields(user);
        boolean requiresEmailValidation = user.getAuthProvider() == AuthProvider.LOCAL && !changedFields.isEmpty();

        String verificationCodePreview = null;
        if (requiresEmailValidation) {
            verificationCodePreview = issueVerificationCode(user);
        }

        User saved = userRepository.save(user);

        if (emailChanged) {
            notifyRemainingChanges(saved, "correo", "PROFILE_EMAIL_CHANGE_REMAINING", saved.remainingEmailChanges());
        }
        if (phoneChanged) {
            notifyRemainingChanges(saved, "telefono", "PROFILE_PHONE_CHANGE_REMAINING", saved.remainingPhoneChanges());
        }
        if (documentChanged) {
            notifyRemainingChanges(saved, "documento", "PROFILE_DOCUMENT_CHANGE_REMAINING", saved.remainingDocumentChanges());
        }
        if (requiresEmailValidation) {
            customerEmailService.sendProfileUpdateVerification(
                    saved,
                    changedFields,
                    verificationCodePreview,
                    saved.getEmailVerificationExpiresAt()
            );
        } else if (!changedFields.isEmpty()) {
            customerEmailService.sendProfileUpdatedNotice(saved, changedFields);
        }

        return toResponse(saved, verificationCodePreview);
    }

    @Transactional
    public UserProfileResponse uploadMyProfilePhoto(MultipartFile file, AuthUserPrincipal principal) {
        User user = requireUser(principal.getId());
        ensureSelfManagedClient(user, principal);
        String photoUrl;
        try {
            photoUrl = azureBlobStorageService.uploadImage(file, "profiles");
        } catch (Exception e) {
            LOG.warn("Azure Blob Storage upload failed, falling back to Firebase: {}", e.getMessage());
            photoUrl = firebaseAdminService.uploadPublicImage(file, "profiles", "profile-");
        }
        user.updateProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                photoUrl,
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
        User saved = userRepository.save(user);
        firebaseAdminService.mirrorClientProfile(saved);
        return toResponse(saved, null);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
    }

    private void ensureSelfManagedClient(User user, AuthUserPrincipal principal) {
        if (principal.roleNames().contains("ADMIN")) {
            return;
        }
        if (!user.isClientSelfManaged()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PROFILE_ADMIN_MANAGED",
                    "Este perfil es administrado por un administrador y no puede editarse desde esta cuenta."
            );
        }
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        return rawPassword != null
                && !rawPassword.isBlank()
                && passwordEncoder.matches(rawPassword.trim(), encodedPassword);
    }

    private DocumentType parseDocumentType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        DocumentType type = DocumentType.fromNullable(rawValue);
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_DOCUMENT_TYPE_INVALID", "Tipo de documento no soportado.");
        }
        return type;
    }

    private Gender parseGender(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        Gender gender = Gender.fromNullable(rawValue);
        if (gender == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_GENDER_INVALID", "Genero no soportado.");
        }
        return gender;
    }

    private String issueVerificationCode(User user) {
        String verificationCode = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        user.prepareEmailVerification(verificationCode, expiresAt);
        return "mock".equals(emailProvider) ? verificationCode : null;
    }

    private void notifyRemainingChanges(User user, String fieldLabel, String type, int remaining) {
        notificationService.notifyUser(
                user.getId(),
                type,
                "Cambios disponibles para " + fieldLabel,
                "Te quedan " + remaining + " cambios para el campo " + fieldLabel + ".",
                java.util.Map.of(
                        "field", fieldLabel,
                        "remaining", remaining,
                        "route", "/profile"
                )
        );
    }

    private boolean isTrackedFieldChange(String currentValue, String nextValue) {
        String current = normalize(currentValue);
        String next = normalize(nextValue);
        return current != null && !Objects.equals(current, next);
    }

    private boolean isTrackedDocumentChange(
            DocumentType currentType,
            String currentNumber,
            DocumentType nextType,
            String nextNumber
    ) {
        String normalizedCurrentNumber = normalize(currentNumber);
        if (normalizedCurrentNumber == null) {
            return false;
        }
        return !Objects.equals(currentType, nextType) || !Objects.equals(normalizedCurrentNumber, normalize(nextNumber));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSupportedLanguage(String value) {
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

    private void encryptSensitiveFields(User user) {
        if (user.getPhone() != null) {
            user.setPhoneEncrypted(sensitiveDataService.encrypt(user.getPhone(), SensitiveFieldType.PHONE));
        }
        if (user.getAddressLine() != null) {
            user.setAddressLineEncrypted(sensitiveDataService.encrypt(user.getAddressLine(), SensitiveFieldType.ADDRESS));
        }
        if (user.getPrimaryDocumentNumber() != null) {
            user.setPrimaryDocumentNumberEncrypted(
                    sensitiveDataService.encrypt(user.getPrimaryDocumentNumber(), SensitiveFieldType.DNI));
        }
        if (user.getSecondaryDocumentNumber() != null) {
            user.setSecondaryDocumentNumberEncrypted(
                    sensitiveDataService.encrypt(user.getSecondaryDocumentNumber(), SensitiveFieldType.DNI));
        }
        if (user.getEmergencyContactName() != null) {
            user.setEmergencyContactNameEncrypted(
                    sensitiveDataService.encrypt(user.getEmergencyContactName(), SensitiveFieldType.EMERGENCY_CONTACT_NAME));
        }
        if (user.getEmergencyContactPhone() != null) {
            user.setEmergencyContactPhoneEncrypted(
                    sensitiveDataService.encrypt(user.getEmergencyContactPhone(), SensitiveFieldType.EMERGENCY_CONTACT_PHONE));
        }
    }

    private UserProfileResponse toResponse(User user, String verificationCodePreview) {
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList();
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                maskSensitive(user.getPhone(), SensitiveFieldType.PHONE),
                user.getNationality(),
                user.getPreferredLanguage(),
                user.getAuthProvider().name(),
                user.isManagedByAdmin(),
                user.isClientSelfManaged(),
                user.getVehiclePlate(),
                user.isEmailVerified(),
                user.isProfileCompleted(),
                user.getBirthDate(),
                user.getGender() == null ? null : user.getGender().name(),
                user.getProfilePhotoPath(),
                maskSensitive(user.getAddressLine(), SensitiveFieldType.ADDRESS),
                user.getCityName(),
                user.getCountryName(),
                user.getPrimaryDocumentType() == null ? null : user.getPrimaryDocumentType().name(),
                maskSensitive(user.getPrimaryDocumentNumber(), SensitiveFieldType.DNI),
                user.getSecondaryDocumentType() == null ? null : user.getSecondaryDocumentType().name(),
                maskSensitive(user.getSecondaryDocumentNumber(), SensitiveFieldType.DNI),
                maskSensitive(user.getEmergencyContactName(), SensitiveFieldType.EMERGENCY_CONTACT_NAME),
                maskSensitive(user.getEmergencyContactPhone(), SensitiveFieldType.EMERGENCY_CONTACT_PHONE),
                user.getTermsAcceptedAt(),
                user.getEmailVerificationExpiresAt(),
                verificationCodePreview,
                user.remainingEmailChanges(),
                user.remainingPhoneChanges(),
                user.remainingDocumentChanges(),
                roles,
                user.getWarehouseAssignments().stream().map(warehouse -> warehouse.getId()).sorted().toList(),
                user.getWarehouseAssignments().stream().map(warehouse -> warehouse.getName()).sorted(String::compareToIgnoreCase).toList()
        );
    }

    private String maskSensitive(String value, SensitiveFieldType type) {
        if (value == null) {
            return null;
        }
        return sensitiveDataService.mask(value, type);
    }

    private record ProfileSnapshot(
            String firstName,
            String lastName,
            String email,
            String phone,
            String nationality,
            String preferredLanguage,
            java.time.LocalDate birthDate,
            Gender gender,
            String profilePhotoPath,
            String addressLine,
            String cityName,
            String countryName,
            DocumentType primaryDocumentType,
            String primaryDocumentNumber,
            DocumentType secondaryDocumentType,
            String secondaryDocumentNumber,
            String emergencyContactName,
            String emergencyContactPhone
    ) {
        private static ProfileSnapshot from(User user) {
            return new ProfileSnapshot(
                    normalizeText(user.getFirstName()),
                    normalizeText(user.getLastName()),
                    normalizeText(user.getEmail()),
                    normalizeText(user.getPhone()),
                    normalizeText(user.getNationality()),
                    normalizeText(user.getPreferredLanguage()),
                    user.getBirthDate(),
                    user.getGender(),
                    normalizeText(user.getProfilePhotoPath()),
                    normalizeText(user.getAddressLine()),
                    normalizeText(user.getCityName()),
                    normalizeText(user.getCountryName()),
                    user.getPrimaryDocumentType(),
                    normalizeText(user.getPrimaryDocumentNumber()),
                    user.getSecondaryDocumentType(),
                    normalizeText(user.getSecondaryDocumentNumber()),
                    normalizeText(user.getEmergencyContactName()),
                    normalizeText(user.getEmergencyContactPhone())
            );
        }

        private List<String> changedFields(User user) {
            List<String> changed = new java.util.ArrayList<>();
            addIfChanged(changed, "nombre", firstName, normalizeText(user.getFirstName()));
            addIfChanged(changed, "apellido", lastName, normalizeText(user.getLastName()));
            addIfChanged(changed, "correo", email, normalizeText(user.getEmail()));
            addIfChanged(changed, "telefono", phone, normalizeText(user.getPhone()));
            addIfChanged(changed, "nacionalidad", nationality, normalizeText(user.getNationality()));
            addIfChanged(changed, "idioma", preferredLanguage, normalizeText(user.getPreferredLanguage()));
            if (!Objects.equals(birthDate, user.getBirthDate())) {
                changed.add("fecha de nacimiento");
            }
            if (!Objects.equals(gender, user.getGender())) {
                changed.add("genero");
            }
            addIfChanged(changed, "foto de perfil", profilePhotoPath, normalizeText(user.getProfilePhotoPath()));
            addIfChanged(changed, "direccion", addressLine, normalizeText(user.getAddressLine()));
            addIfChanged(changed, "ciudad", cityName, normalizeText(user.getCityName()));
            addIfChanged(changed, "pais", countryName, normalizeText(user.getCountryName()));
            if (!Objects.equals(primaryDocumentType, user.getPrimaryDocumentType())
                    || !Objects.equals(primaryDocumentNumber, normalizeText(user.getPrimaryDocumentNumber()))) {
                changed.add("documento principal");
            }
            if (!Objects.equals(secondaryDocumentType, user.getSecondaryDocumentType())
                    || !Objects.equals(secondaryDocumentNumber, normalizeText(user.getSecondaryDocumentNumber()))) {
                changed.add("documento secundario");
            }
            addIfChanged(changed, "contacto de emergencia", emergencyContactName, normalizeText(user.getEmergencyContactName()));
            addIfChanged(changed, "telefono de emergencia", emergencyContactPhone, normalizeText(user.getEmergencyContactPhone()));
            return changed;
        }

        private static void addIfChanged(List<String> changed, String label, String previous, String current) {
            if (!Objects.equals(previous, current)) {
                changed.add(label);
            }
        }

        private static String normalizeText(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }
}
