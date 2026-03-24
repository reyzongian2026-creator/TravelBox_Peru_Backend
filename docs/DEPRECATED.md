# Deprecated Files - TravelBox Peru

**Last Updated:** March 24, 2026

---

## Why These Files Are Deprecated

### Firebase Authentication
- **Reason:** Firebase Auth is being replaced
- **Status:** Disabled by default (`firebase.enabled=false`)
- **Impact:** Social login (Google/Facebook) not available
- **Replacement:** Pending - Azure AD B2C not available for new projects

### Google Maps / Google Translate
- **Reason:** Migrated to Azure services
- **Status:** Replaced with Azure Maps and Azure Translator
- **Impact:** None - migration complete

---

## Backend - Deprecated Files

**Location:** `src/main/java/com/tuempresa/storage/deprecated/`

### Firebase Package
| File | Purpose | Deprecated Date |
|------|---------|----------------|
| `FirebaseAdminService.java` | Firebase Admin SDK integration | 2026-03-24 |
| `FirebaseAdminClient.java` | Firebase Admin client | 2026-03-24 |
| `FirebaseClientIdentity.java` | Firebase identity DTO | 2026-03-24 |
| `FirebaseUserMigrationRunner.java` | User migration to Firebase | 2026-03-24 |

### Firebase References in Other Files
| File | Reference | Action |
|------|-----------|--------|
| `pom.xml` | `firebase-admin` dependency | Remove |
| `application.yml` | `app.firebase.*` config | Keep (disabled) |
| `User.java` | `firebaseUid` field | Keep (data migration needed) |
| `AuthService.java` | Firebase social login | Replace |
| `ProfileService.java` | Photo upload to Firebase Storage | Replace with Azure Blob |
| `AdminUserService.java` | User account deletion | Update |

---

## Frontend - Deprecated Files

**Location:** `lib/deprecated/`

### Firebase Core
| File | Purpose | Deprecated Date |
|------|---------|----------------|
| `lib/core/firebase/travelbox_firebase.dart` | Firebase initialization | 2026-03-24 |

### Firebase Auth
| File | Purpose | Deprecated Date |
|------|---------|----------------|
| `lib/features/auth/data/firebase_client_auth_service.dart` | Firebase social auth | 2026-03-24 |

### References to Update
| File | Reference | Action |
|------|-----------|--------|
| `pubspec.yaml` | `firebase_auth`, `firebase_core`, `flutter_facebook_auth`, `google_sign_in` | Remove |
| `auth_repository_impl.dart` | Firebase import | Remove/Replace |
| `generated_plugin_registrant.cc` | Firebase plugins | Regenerate |

---

## Google Services - Deprecated

### Backend
| File | Service | Replacement |
|------|---------|-------------|
| `GeoRoutingService.java` | Google Routes API | OSRM/Azure Maps |
| `OpsMessageTranslationService.java` | Google Translate | Azure Translator |

### Frontend
| File | Service | Replacement |
|------|---------|-------------|
| All `*_page.dart` with `google_maps_flutter` | Google Maps | Azure Maps (flutter_map) |

---

## Migration Status

| Service | Deprecated | Removed | Replacement Ready |
|---------|-----------|---------|------------------|
| Firebase Auth | ✅ | ❌ | ❌ |
| Firebase Storage | ❌ | ❌ | ✅ (Azure Blob) |
| Google Maps | ✅ | ❌ | ✅ |
| Google Translate | ✅ | ❌ | ✅ |

---

## How to Clean Up Deprecated Files

### 1. Remove Firebase from Backend
```bash
# Delete the deprecated folder
rm -rf src/main/java/com/tuempresa/storage/deprecated/

# Remove from pom.xml
# Delete firebase-admin dependency

# Update User.java (keep firebaseUid for data migration)
```

### 2. Remove Firebase from Frontend
```bash
# Move deprecated files first
mv lib/core/firebase lib/deprecated/
mv lib/features/auth/data/firebase_client_auth_service.dart lib/deprecated/

# Update pubspec.yaml - remove:
# - firebase_auth
# - firebase_core
# - flutter_facebook_auth
# - google_sign_in

# Run flutter pub get
# Fix any compilation errors
```

---

## Notes

- **Do NOT delete Firebase files yet** - Auth replacement not ready
- **User data migration** needed before removing Firebase completely
- **Keep FirebaseUid field** in User entity for backward compatibility
- **Consider data export** before Firebase removal
