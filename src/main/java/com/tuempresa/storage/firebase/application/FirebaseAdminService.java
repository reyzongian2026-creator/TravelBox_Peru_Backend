package com.tuempresa.storage.firebase.application;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.users.domain.AuthProvider;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FirebaseAdminService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final boolean enabled;
    private final String storageBucket;
    private final String clientProfileCollection;
    private final FirebaseApp firebaseApp;
    private final boolean initialized;

    public FirebaseAdminService(
            @Value("${app.firebase.enabled:false}") boolean enabled,
            @Value("${app.firebase.project-id:}") String projectId,
            @Value("${app.firebase.storage-bucket:}") String storageBucket,
            @Value("${app.firebase.service-account-file:}") String serviceAccountFile,
            @Value("${app.firebase.service-account-json:}") String serviceAccountJson,
            @Value("${app.firebase.firestore.client-profile-collection:clientProfiles}") String clientProfileCollection
    ) {
        this.enabled = enabled;
        this.storageBucket = safe(storageBucket);
        this.clientProfileCollection = safe(clientProfileCollection) == null ? "clientProfiles" : safe(clientProfileCollection);
        FirebaseApp app = null;
        boolean initSuccess = false;
        if (enabled) {
            try {
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setCredentials(loadCredentials(serviceAccountFile, serviceAccountJson));
                if (safe(projectId) != null) {
                    builder.setProjectId(projectId.trim());
                }
                if (this.storageBucket != null) {
                    builder.setStorageBucket(this.storageBucket);
                }
                app = FirebaseApp.getApps().stream()
                        .filter(a -> "travelbox-backend".equals(a.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(builder.build(), "travelbox-backend"));
                initSuccess = true;
            } catch (Exception ex) {
                System.err.println("WARNING: Firebase initialization failed - Firebase features will be disabled. Error: " + ex.getMessage());
            }
        }
        this.firebaseApp = app;
        this.initialized = initSuccess;
    }

    public boolean isEnabled() {
        return enabled && initialized && firebaseApp != null;
    }

    public boolean isStorageEnabled() {
        return isEnabled() && storageBucket != null;
    }

    public FirebaseClientIdentity verifyClientIdToken(String idToken) {
        ensureEnabled("FIREBASE_AUTH_DISABLED", "Firebase Auth no esta configurado en el backend.");
        try {
            FirebaseToken token = FirebaseAuth.getInstance(firebaseApp).verifyIdToken(idToken);
            AuthProvider provider = mapProvider(token);
            if (provider == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "FIREBASE_PROVIDER_UNSUPPORTED",
                        "Solo se permite Google o Facebook para el registro del cliente."
                );
            }
            return new FirebaseClientIdentity(
                    token.getUid(),
                    token.getEmail(),
                    token.getName(),
                    token.getPicture(),
                    provider
            );
        } catch (FirebaseAuthException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "FIREBASE_TOKEN_INVALID", "El token de Firebase es invalido.");
        }
    }

    public String syncUserAccount(User user, String rawPassword) {
        if (!isEnabled() || user == null) {
            return user == null ? null : safe(user.getFirebaseUid());
        }
        String email = safe(user.getEmail());
        if (email == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FIREBASE_USER_EMAIL_REQUIRED",
                    "No se puede sincronizar en Firebase un usuario sin correo."
            );
        }
        try {
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(firebaseApp);
            UserRecord existing = findExistingUser(firebaseAuth, safe(user.getFirebaseUid()), email);
            String password = normalizePassword(rawPassword);

            UserRecord persisted;
            if (existing == null) {
                CreateRequest createRequest = new CreateRequest()
                        .setEmail(email)
                        .setDisplayName(safe(user.getFullName()))
                        .setEmailVerified(user.isEmailVerified())
                        .setDisabled(!user.isActive());
                if (safe(user.getProfilePhotoPath()) != null) {
                    createRequest.setPhotoUrl(user.getProfilePhotoPath());
                }
                if (password != null) {
                    createRequest.setPassword(password);
                }
                persisted = firebaseAuth.createUser(createRequest);
            } else {
                UpdateRequest updateRequest = new UpdateRequest(existing.getUid())
                        .setEmail(email)
                        .setDisplayName(safe(user.getFullName()))
                        .setEmailVerified(user.isEmailVerified())
                        .setDisabled(!user.isActive());
                if (safe(user.getProfilePhotoPath()) != null) {
                    updateRequest.setPhotoUrl(user.getProfilePhotoPath());
                }
                if (password != null) {
                    updateRequest.setPassword(password);
                }
                persisted = firebaseAuth.updateUser(updateRequest);
            }

            applyCustomClaims(firebaseAuth, persisted.getUid(), user);
            return persisted.getUid();
        } catch (FirebaseAuthException ex) {
            throw mapFirebaseAuthException(
                    ex,
                    "FIREBASE_USER_SYNC_ERROR",
                    "No se pudo sincronizar el usuario con Firebase Auth."
            );
        }
    }

    public void deleteUserAccount(String firebaseUid) {
        if (!isEnabled()) {
            return;
        }
        String uid = safe(firebaseUid);
        if (uid == null) {
            return;
        }
        try {
            FirebaseAuth.getInstance(firebaseApp).deleteUser(uid);
        } catch (FirebaseAuthException ex) {
            if (!isAuthError(ex, "USER_NOT_FOUND")) {
                throw mapFirebaseAuthException(
                        ex,
                        "FIREBASE_USER_DELETE_ERROR",
                        "No se pudo eliminar el usuario en Firebase Auth."
                );
            }
        }

        Firestore firestore = FirestoreClient.getFirestore(firebaseApp);
        firestore.collection(clientProfileCollection).document(uid).delete();
    }

    public void deleteUserAccountByEmail(String email) {
        if (!isEnabled()) {
            return;
        }
        String normalizedEmail = safe(email);
        if (normalizedEmail == null) {
            return;
        }
        try {
            UserRecord userRecord = FirebaseAuth.getInstance(firebaseApp).getUserByEmail(normalizedEmail);
            deleteUserAccount(userRecord.getUid());
        } catch (FirebaseAuthException ex) {
            if (isAuthError(ex, "USER_NOT_FOUND")) {
                return;
            }
            throw mapFirebaseAuthException(
                    ex,
                    "FIREBASE_USER_DELETE_ERROR",
                    "No se pudo eliminar el usuario en Firebase Auth."
            );
        }
    }

    public String uploadPublicImage(MultipartFile file, String folder, String prefix) {
        if (!isStorageEnabled()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_FAILED,
                    "FIREBASE_STORAGE_DISABLED",
                    "Firebase Storage no esta configurado en el backend."
            );
        }
        validateImage(file);
        try {
            String extension = extensionFromContentType(file.getContentType());
            String objectName = folder + "/" + prefix + UUID.randomUUID() + extension;
            String downloadToken = UUID.randomUUID().toString();
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(storageBucket, objectName))
                    .setContentType(file.getContentType())
                    .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                    .build();
            StorageClient.getInstance(firebaseApp)
                    .bucket(storageBucket)
                    .getStorage()
                    .create(blobInfo, file.getBytes());
            String encodedObjectName = URLEncoder.encode(objectName, StandardCharsets.UTF_8).replace("+", "%20");
            return "https://firebasestorage.googleapis.com/v0/b/"
                    + storageBucket
                    + "/o/"
                    + encodedObjectName
                    + "?alt=media&token="
                    + downloadToken;
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FIREBASE_STORAGE_UPLOAD_ERROR",
                    "No se pudo subir la imagen a Firebase Storage."
            );
        }
    }

    public void mirrorClientProfile(User user) {
        if (!isEnabled() || user == null || !user.getRoles().contains(com.tuempresa.storage.users.domain.Role.CLIENT)) {
            return;
        }
        Firestore firestore = FirestoreClient.getFirestore(firebaseApp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("localUserId", user.getId());
        payload.put("firebaseUid", user.getFirebaseUid());
        payload.put("authProvider", user.getAuthProvider().name());
        payload.put("fullName", user.getFullName());
        payload.put("firstName", user.getFirstName());
        payload.put("lastName", user.getLastName());
        payload.put("email", user.getEmail());
        payload.put("phone", user.getPhone());
        payload.put("nationality", user.getNationality());
        payload.put("preferredLanguage", user.getPreferredLanguage());
        payload.put("profilePhotoPath", user.getProfilePhotoPath());
        payload.put("documentType", user.getPrimaryDocumentType() == null ? null : user.getPrimaryDocumentType().name());
        payload.put("documentNumber", user.getPrimaryDocumentNumber());
        payload.put("emailVerified", user.isEmailVerified());
        payload.put("profileCompleted", user.isProfileCompleted());
        payload.put("emailChangeRemaining", user.remainingEmailChanges());
        payload.put("phoneChangeRemaining", user.remainingPhoneChanges());
        payload.put("documentChangeRemaining", user.remainingDocumentChanges());
        payload.put("updatedAt", Instant.now().toString());
        String documentId = safe(user.getFirebaseUid()) != null ? user.getFirebaseUid() : String.valueOf(user.getId());
        firestore.collection(clientProfileCollection)
                .document(documentId)
                .set(payload, SetOptions.merge());
    }

    private AuthProvider mapProvider(FirebaseToken token) {
        Object firebaseClaim = token.getClaims().get("firebase");
        if (!(firebaseClaim instanceof Map<?, ?> firebaseMap)) {
            return null;
        }
        Object signInProvider = firebaseMap.get("sign_in_provider");
        if (signInProvider == null) {
            return null;
        }
        String provider = signInProvider.toString().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "google.com" -> AuthProvider.FIREBASE_GOOGLE;
            case "facebook.com" -> AuthProvider.FIREBASE_FACEBOOK;
            default -> null;
        };
    }

    private UserRecord findExistingUser(FirebaseAuth firebaseAuth, String firebaseUid, String email) throws FirebaseAuthException {
        if (firebaseUid != null) {
            try {
                return firebaseAuth.getUser(firebaseUid);
            } catch (FirebaseAuthException ex) {
                if (!isAuthError(ex, "USER_NOT_FOUND")) {
                    throw ex;
                }
            }
        }
        try {
            return firebaseAuth.getUserByEmail(email);
        } catch (FirebaseAuthException ex) {
            if (isAuthError(ex, "USER_NOT_FOUND")) {
                return null;
            }
            throw ex;
        }
    }

    private void applyCustomClaims(FirebaseAuth firebaseAuth, String firebaseUid, User user) throws FirebaseAuthException {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(
                "roles",
                user.getRoles().stream()
                        .map(Role::name)
                        .sorted()
                        .toList()
        );
        claims.put("authProvider", user.getAuthProvider() == null ? AuthProvider.LOCAL.name() : user.getAuthProvider().name());
        claims.put("managedByAdmin", user.isManagedByAdmin());
        claims.put("active", user.isActive());
        if (user.getId() != null) {
            claims.put("localUserId", user.getId());
        }
        firebaseAuth.setCustomUserClaims(firebaseUid, claims);
    }

    private String normalizePassword(String rawPassword) {
        String normalized = safe(rawPassword);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() < 8) {
            return null;
        }
        return normalized;
    }

    private ApiException mapFirebaseAuthException(FirebaseAuthException ex, String code, String fallbackMessage) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (isAuthError(ex, "EMAIL_ALREADY_EXISTS")) {
            status = HttpStatus.CONFLICT;
        } else if (isAuthError(ex, "INVALID_EMAIL") || isAuthError(ex, "INVALID_PASSWORD")) {
            status = HttpStatus.BAD_REQUEST;
        }
        String message = safe(ex.getMessage());
        return new ApiException(status, code, message == null ? fallbackMessage : message);
    }

    private boolean isAuthError(FirebaseAuthException ex, String code) {
        if (ex == null || code == null) {
            return false;
        }
        return ex.getAuthErrorCode() != null && code.equals(ex.getAuthErrorCode().name());
    }

    private GoogleCredentials loadCredentials(String serviceAccountFile, String serviceAccountJson) throws IOException {
        if (safe(serviceAccountJson) != null) {
            try (InputStream inputStream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }
        if (safe(serviceAccountFile) != null) {
            try (InputStream inputStream = Files.newInputStream(Path.of(serviceAccountFile.trim()))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Debes enviar un archivo.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "El archivo excede el tamano maximo permitido (5MB).");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", "Solo se permiten imagenes JPG, PNG o WEBP.");
        }
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private void ensureEnabled(String code, String message) {
        if (!isEnabled()) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, code, message);
        }
    }

    private String safe(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
