package com.tuempresa.storage.users.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    public static final int MAX_RECOGNITION_FIELD_CHANGES = 3;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 40)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "firebase_uid", unique = true, length = 160)
    private String firebaseUid;

    @Column(length = 30)
    private String phone;

    @Column(length = 80)
    private String nationality;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verification_code", length = 20)
    private String emailVerificationCode;

    @Column(name = "email_verification_code_hash", length = 128)
    private String emailVerificationCodeHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    @Column(name = "pending_real_email", length = 160)
    private String pendingRealEmail;

    @Column(name = "password_reset_code", length = 20)
    private String passwordResetCode;

    @Column(name = "password_reset_code_hash", length = 128)
    private String passwordResetCodeHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "profile_photo_path", length = 260)
    private String profilePhotoPath;

    @Column(name = "document_photo_path", length = 260)
    private String documentPhotoPath;

    @Column(name = "address_line", length = 220)
    private String addressLine;

    @Column(name = "city_name", length = 120)
    private String cityName;

    @Column(name = "country_name", length = 120)
    private String countryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_document_type", length = 40)
    private DocumentType primaryDocumentType;

    @Column(name = "primary_document_number", length = 60)
    private String primaryDocumentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_document_type", length = 40)
    private DocumentType secondaryDocumentType;

    @Column(name = "secondary_document_number", length = 60)
    private String secondaryDocumentNumber;

    @Column(name = "emergency_contact_name", length = 120)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Column(name = "phone_encrypted", length = 500)
    private String phoneEncrypted;

    @Column(name = "address_line_encrypted", length = 500)
    private String addressLineEncrypted;

    @Column(name = "primary_document_number_encrypted", length = 500)
    private String primaryDocumentNumberEncrypted;

    @Column(name = "secondary_document_number_encrypted", length = 500)
    private String secondaryDocumentNumberEncrypted;

    @Column(name = "emergency_contact_name_encrypted", length = 500)
    private String emergencyContactNameEncrypted;

    @Column(name = "emergency_contact_phone_encrypted", length = 500)
    private String emergencyContactPhoneEncrypted;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "managed_by_admin", nullable = false)
    private boolean managedByAdmin;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "requires_real_email_completion", nullable = false)
    private boolean requiresRealEmailCompletion;

    @Column(name = "vehicle_plate", length = 30)
    private String vehiclePlate;

    @Column(name = "email_change_count", nullable = false)
    private int emailChangeCount;

    @Column(name = "phone_change_count", nullable = false)
    private int phoneChangeCount;

    @Column(name = "document_change_count", nullable = false)
    private int documentChangeCount;

    @Column(name = "wallet_balance", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal walletBalance = java.math.BigDecimal.ZERO;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_warehouse_access", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    private Set<Warehouse> warehouseAssignments = new HashSet<>();

    public static User of(String fullName, String email, String passwordHash, String phone, Set<Role> roles) {
        User user = new User();
        String normalizedName = clean(fullName, 160);
        user.fullName = normalizedName;
        String[] splitName = splitName(normalizedName);
        user.firstName = splitName[0];
        user.lastName = splitName[1];
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.phone = clean(phone, 30);
        user.roles = new HashSet<>(roles);
        user.active = true;
        user.preferredLanguage = "es";
        user.emailVerified = false;
        user.authProvider = AuthProvider.LOCAL;
        user.onboardingCompleted = false;
        return user;
    }

    public String getFullName() {
        return fullName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void setPendingRealEmail(String pendingRealEmail) {
        this.pendingRealEmail = normalizeEmail(pendingRealEmail);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getNationality() {
        return nationality;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getEmailVerificationCode() {
        return emailVerificationCode;
    }

    public String getEmailVerificationCodeHash() {
        return emailVerificationCodeHash;
    }

    public Instant getEmailVerificationExpiresAt() {
        return emailVerificationExpiresAt;
    }

    public String getPendingRealEmail() {
        return pendingRealEmail;
    }

    public String getPasswordResetCode() {
        return passwordResetCode;
    }

    public String getPasswordResetCodeHash() {
        return passwordResetCodeHash;
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public String getProfilePhotoPath() {
        return profilePhotoPath;
    }

    public String getDocumentPhotoPath() {
        return documentPhotoPath;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCountryName() {
        return countryName;
    }

    public DocumentType getPrimaryDocumentType() {
        return primaryDocumentType;
    }

    public String getPrimaryDocumentNumber() {
        return primaryDocumentNumber;
    }

    public DocumentType getSecondaryDocumentType() {
        return secondaryDocumentType;
    }

    public String getSecondaryDocumentNumber() {
        return secondaryDocumentNumber;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public String getAddressLineEncrypted() {
        return addressLineEncrypted;
    }

    public void setAddressLineEncrypted(String addressLineEncrypted) {
        this.addressLineEncrypted = addressLineEncrypted;
    }

    public String getPrimaryDocumentNumberEncrypted() {
        return primaryDocumentNumberEncrypted;
    }

    public void setPrimaryDocumentNumberEncrypted(String primaryDocumentNumberEncrypted) {
        this.primaryDocumentNumberEncrypted = primaryDocumentNumberEncrypted;
    }

    public String getSecondaryDocumentNumberEncrypted() {
        return secondaryDocumentNumberEncrypted;
    }

    public void setSecondaryDocumentNumberEncrypted(String secondaryDocumentNumberEncrypted) {
        this.secondaryDocumentNumberEncrypted = secondaryDocumentNumberEncrypted;
    }

    public String getEmergencyContactNameEncrypted() {
        return emergencyContactNameEncrypted;
    }

    public void setEmergencyContactNameEncrypted(String emergencyContactNameEncrypted) {
        this.emergencyContactNameEncrypted = emergencyContactNameEncrypted;
    }

    public String getEmergencyContactPhoneEncrypted() {
        return emergencyContactPhoneEncrypted;
    }

    public void setEmergencyContactPhoneEncrypted(String emergencyContactPhoneEncrypted) {
        this.emergencyContactPhoneEncrypted = emergencyContactPhoneEncrypted;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public boolean isActive() {
        return active;
    }

    public java.math.BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void addWalletCredit(java.math.BigDecimal amount) {
        this.walletBalance = this.walletBalance.add(amount);
    }

    public void deductWalletBalance(java.math.BigDecimal amount) {
        if (amount.compareTo(this.walletBalance) > 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }
        this.walletBalance = this.walletBalance.subtract(amount);
    }

    public boolean isManagedByAdmin() {
        return managedByAdmin;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public boolean requiresRealEmailCompletion() {
        return requiresRealEmailCompletion;
    }

    public int getEmailChangeCount() {
        return emailChangeCount;
    }

    public int getPhoneChangeCount() {
        return phoneChangeCount;
    }

    public int getDocumentChangeCount() {
        return documentChangeCount;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<Warehouse> getWarehouseAssignments() {
        return warehouseAssignments;
    }

    public void applyRegistrationDetails(
            String firstName,
            String lastName,
            String nationality,
            String preferredLanguage,
            String phone,
            boolean termsAccepted,
            String profilePhotoPath) {
        this.firstName = clean(firstName, 80);
        this.lastName = clean(lastName, 80);
        this.fullName = resolveFullName(this.firstName, this.lastName, this.fullName);
        this.nationality = clean(nationality, 80);
        this.preferredLanguage = defaultLanguage(preferredLanguage);
        this.phone = clean(phone, 30);
        this.profilePhotoPath = clean(profilePhotoPath, 260);
        if (termsAccepted && this.termsAcceptedAt == null) {
            this.termsAcceptedAt = Instant.now();
        }
    }

    public void updateProfile(
            String firstName,
            String lastName,
            String email,
            String phone,
            String nationality,
            String preferredLanguage,
            LocalDate birthDate,
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
            String emergencyContactPhone) {
        if (firstName != null) {
            this.firstName = clean(firstName, 80);
        }
        if (lastName != null) {
            this.lastName = clean(lastName, 80);
        }
        if (email != null) {
            this.email = normalizeEmail(email);
        }
        if (phone != null) {
            this.phone = clean(phone, 30);
        }
        if (nationality != null) {
            this.nationality = clean(nationality, 80);
        }
        if (preferredLanguage != null) {
            this.preferredLanguage = defaultLanguage(preferredLanguage);
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (profilePhotoPath != null) {
            this.profilePhotoPath = clean(profilePhotoPath, 260);
        }
        if (addressLine != null) {
            this.addressLine = clean(addressLine, 220);
        }
        if (cityName != null) {
            this.cityName = clean(cityName, 120);
        }
        if (countryName != null) {
            this.countryName = clean(countryName, 120);
        }
        if (primaryDocumentType != null || primaryDocumentNumber != null) {
            this.primaryDocumentType = primaryDocumentType;
            this.primaryDocumentNumber = clean(primaryDocumentNumber, 60);
        }
        if (secondaryDocumentType != null || secondaryDocumentNumber != null) {
            this.secondaryDocumentType = secondaryDocumentType;
            this.secondaryDocumentNumber = clean(secondaryDocumentNumber, 60);
        }
        if (emergencyContactName != null) {
            this.emergencyContactName = clean(emergencyContactName, 120);
        }
        if (emergencyContactPhone != null) {
            this.emergencyContactPhone = clean(emergencyContactPhone, 30);
        }
        this.fullName = resolveFullName(this.firstName, this.lastName, this.fullName);
    }

    public void updateDocumentPhotoPath(String documentPhotoPath) {
        if (documentPhotoPath != null) {
            this.documentPhotoPath = clean(documentPhotoPath, 260);
        }
    }

    public void prepareEmailVerification(String verificationCode, Instant expiresAt) {
        String normalizedCode = clean(verificationCode, 20);
        this.emailVerified = false;
        this.emailVerificationCode = null;
        this.emailVerificationCodeHash = normalizedCode == null ? null : hashOneTimeCode(normalizedCode);
        this.emailVerificationExpiresAt = expiresAt;
    }

    public void markEmailPendingVerification() {
        this.emailVerified = false;
        this.emailVerificationCode = null;
        this.emailVerificationCodeHash = null;
        this.emailVerificationExpiresAt = null;
    }

    public boolean verifyEmailCode(String verificationCode, Instant now) {
        String normalized = clean(verificationCode, 20);
        if (normalized == null) {
            return false;
        }
        boolean codeMatches;
        if (hasText(emailVerificationCodeHash)) {
            codeMatches = Objects.equals(hashOneTimeCode(normalized), emailVerificationCodeHash);
        } else {
            codeMatches = Objects.equals(normalized, emailVerificationCode);
        }
        if (!codeMatches) {
            return false;
        }
        if (emailVerificationExpiresAt == null || emailVerificationExpiresAt.isBefore(now)) {
            return false;
        }
        markEmailVerified();
        return true;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerificationCode = null;
        this.emailVerificationCodeHash = null;
        this.emailVerificationExpiresAt = null;
    }

    public void preparePasswordReset(String resetCode, Instant expiresAt) {
        String normalizedCode = clean(resetCode, 20);
        this.passwordResetCode = null;
        this.passwordResetCodeHash = normalizedCode == null ? null : hashOneTimeCode(normalizedCode);
        this.passwordResetExpiresAt = expiresAt;
    }

    public boolean verifyPasswordResetCode(String resetCode, Instant now) {
        String normalized = clean(resetCode, 20);
        if (normalized == null) {
            return false;
        }
        boolean codeMatches;
        if (hasText(passwordResetCodeHash)) {
            codeMatches = Objects.equals(hashOneTimeCode(normalized), passwordResetCodeHash);
        } else {
            codeMatches = Objects.equals(normalized, passwordResetCode);
        }
        if (!codeMatches) {
            return false;
        }
        if (passwordResetExpiresAt == null || passwordResetExpiresAt.isBefore(now)) {
            return false;
        }
        return true;
    }

    public void clearPasswordReset() {
        this.passwordResetCode = null;
        this.passwordResetCodeHash = null;
        this.passwordResetExpiresAt = null;
    }

    public boolean isProfileCompleted() {
        return hasText(firstName)
                && hasText(lastName)
                && hasText(phone)
                && hasText(nationality)
                && hasText(preferredLanguage);
    }

    public void updateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Roles cannot be empty.");
        }
        this.roles = new HashSet<>(roles);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void markManagedByAdmin(boolean managedByAdmin) {
        this.managedByAdmin = managedByAdmin;
    }

    public void markOnboardingCompleted() {
        this.onboardingCompleted = true;
    }

    public void requireRealEmailCompletion() {
        this.requiresRealEmailCompletion = true;
    }

    public void clearRealEmailCompletionRequirement() {
        this.requiresRealEmailCompletion = false;
    }

    public void clearPendingRealEmail() {
        this.pendingRealEmail = null;
    }

    public void updateWarehouseAssignments(Set<Warehouse> warehouses) {
        this.warehouseAssignments = warehouses == null ? new HashSet<>() : new HashSet<>(warehouses);
    }

    public void updateAdminProfile(
            String firstName,
            String lastName,
            String email,
            String phone,
            String nationality,
            String preferredLanguage) {
        this.firstName = clean(firstName, 80);
        this.lastName = clean(lastName, 80);
        this.fullName = resolveFullName(this.firstName, this.lastName, this.fullName);
        this.email = normalizeEmail(email);
        this.phone = clean(phone, 30);
        this.nationality = clean(nationality, 80);
        this.preferredLanguage = defaultLanguage(preferredLanguage);
    }

    public void updateVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = clean(vehiclePlate, 30);
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        clearPasswordReset();
    }

    public void linkSocialIdentity(AuthProvider authProvider) {
        this.authProvider = authProvider == null ? AuthProvider.LOCAL : authProvider;
    }

    public void linkFirebaseIdentity(AuthProvider authProvider, String firebaseUid) {
        linkSocialIdentity(authProvider);
        this.firebaseUid = clean(firebaseUid, 160);
    }

    public boolean isClientSelfManaged() {
        return roles.contains(Role.CLIENT) && !managedByAdmin;
    }

    public boolean canSelfEditProfile() {
        if (roles.contains(Role.ADMIN)) {
            return true;
        }
        return isClientSelfManaged();
    }

    public boolean isSocialClientAuth() {
        return roles.contains(Role.CLIENT) && authProvider != null && authProvider != AuthProvider.LOCAL;
    }

    public void incrementEmailChangeCount() {
        emailChangeCount += 1;
    }

    public void incrementPhoneChangeCount() {
        phoneChangeCount += 1;
    }

    public void incrementDocumentChangeCount() {
        documentChangeCount += 1;
    }

    public int remainingEmailChanges() {
        return Math.max(0, MAX_RECOGNITION_FIELD_CHANGES - emailChangeCount);
    }

    public int remainingPhoneChanges() {
        return Math.max(0, MAX_RECOGNITION_FIELD_CHANGES - phoneChangeCount);
    }

    public int remainingDocumentChanges() {
        return Math.max(0, MAX_RECOGNITION_FIELD_CHANGES - documentChangeCount);
    }

    private static String resolveFullName(String firstName, String lastName, String fallback) {
        String fullName = (safe(firstName) + " " + safe(lastName)).trim();
        if (hasText(fullName)) {
            return clean(fullName, 160);
        }
        return clean(fallback, 160);
    }

    private static String defaultLanguage(String preferredLanguage) {
        String normalized = clean(preferredLanguage, 10);
        return hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : "es";
    }

    private static String normalizeEmail(String email) {
        String normalized = clean(email, 160);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String[] splitName(String fullName) {
        String normalized = clean(fullName, 160);
        if (!hasText(normalized)) {
            return new String[] { null, null };
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return new String[] { parts[0], null };
        }
        int middle = Math.max(1, parts.length / 2);
        StringBuilder first = new StringBuilder();
        StringBuilder last = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i < middle) {
                if (first.length() > 0) {
                    first.append(' ');
                }
                first.append(parts[i]);
            } else {
                if (last.length() > 0) {
                    last.append(' ');
                }
                last.append(parts[i]);
            }
        }
        return new String[] { first.toString(), last.toString() };
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String hashOneTimeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM.", ex);
        }
    }
}
