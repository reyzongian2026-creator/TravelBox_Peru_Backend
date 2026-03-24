# Pending Tasks - TravelBox Peru Migration

**Last Updated:** March 24, 2026

---

## 🔴 High Priority

### 1. Authentication Replacement
- **Issue:** Firebase Auth is disabled, no replacement configured
- **Impact:** Users cannot login/register
- **Options:**
  - Azure AD B2C (not available for new projects)
  - Build custom auth service with JWT
  - Use third-party auth (Auth0, Okta, etc.)
- **Owner:** Backend Team

### 2. Email - Graph API (Microsoft 365)
- **Issue:** Graph API requires M365 license with Exchange Online mailbox
- **Current:** Using Brevo SMTP relay as workaround
- **Impact:** Cannot send emails from corporate domain
- **Steps to fix:**
  1. Purchase M365 license (E3 or Business Basic)
  2. Assign license to user `admin@Weesystem.onmicrosoft.com`
  3. Set `tbx-back-email-provider=graph` in Key Vault
  4. Restart backend
- **Owner:** DevOps/Admin

---

## 🟡 Medium Priority

### 3. Azure AD Configuration (Backend)
- **Issue:** Azure AD App Registration exists but auth flow not fully configured
- **Files:**
  - `lib/core/auth/entra_auth_config.dart` (placeholder)
  - `lib/features/auth/data/entra_client_auth_service.dart` (placeholder)
- **Status:** Code added but not integrated with auth flow

### 4. Remove Firebase Dependencies
- **Backend:** Move `firebase/` package to `deprecated/`
- **Frontend:** 
  - Move `lib/core/firebase/` to `lib/deprecated/`
  - Move `lib/features/auth/data/firebase_client_auth_service.dart` to `lib/deprecated/`
  - Remove from `pubspec.yaml`
  - Update imports in `auth_repository_impl.dart`
- **Status:** ✅ Done (commit `8423f78` backend, `53d7881` frontend)

---

## 🟢 Low Priority / Nice to Have

### 5. Azure Cost Monitoring
- Add cost tracking endpoint to backend
- Display estimated monthly costs in frontend Settings tab
- **Status:** ✅ Done (endpoint `/admin/system/azure-resources`, tab in System Admin page)

### 6. Testing
- Test the Azure Resources endpoint
- Test the Azure Resources tab in frontend
- Verify deployment workflows work correctly

### 6. Resource Expiry Alerts
- Set up alerts for resource expirations
- Add to monitoring dashboard

### 7. Performance Metrics
- Add APM (Application Performance Monitoring)
- Consider Azure Application Insights

---

## ✅ Completed Tasks (Historical)

| Date | Task | Status |
|------|------|--------|
| 2026-03-24 | Backend → Azure App Service | Done |
| 2026-03-24 | Frontend → Azure App Service | Done |
| 2026-03-24 | PostgreSQL → Azure DB | Done |
| 2026-03-24 | Google Maps → Azure Maps | Done |
| 2026-03-24 | Google Translate → Azure Translator | Done |
| 2026-03-24 | Firebase Auth disabled | Done |
| 2026-03-24 | Secrets → Azure Key Vault | Done |
| 2026-03-24 | Graph API email code ready | Done |
| 2026-03-24 | Azure Resources monitoring endpoint | Done |
| 2026-03-24 | Azure Resources tab in frontend | Done |
| 2026-03-24 | Firebase files moved to deprecated | Done |

---

## 📝 Notes

- Azure AD B2C is NOT available for new projects as of 2024
- Consider using Entra ID (Azure AD) for enterprise auth
- For consumer auth, consider alternatives like Auth0 or building custom JWT auth
