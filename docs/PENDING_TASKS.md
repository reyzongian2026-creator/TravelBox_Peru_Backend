# Pending Tasks - TravelBox Peru Migration

**Last Updated:** April 7, 2026

---

## 🔴 High Priority

### 1. Authentication Replacement
- **Status:** ✅ Done — Custom JWT auth with access/refresh tokens, single-session enforcement, OAuth Google/Facebook/Entra

### 2. Email - Graph API (Microsoft 365)
- **Status:** ✅ Done — Graph API provider active in prod (`admin@inkavoy.pe`), Brevo as fallback
- Circuit breaker + retry con Resilience4j
- Yape email reconciliation via Graph webhook (subscription auto-renew)

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
| 2026-04-04 | Custom JWT auth + OAuth social | Done |
| 2026-04-04 | Graph API email in prod | Done |
| 2026-04-04 | Izipay payment integration | Done |
| 2026-04-05 | Manual transfer + cash + refund flows | Done |
| 2026-04-05 | QR handoff + checkout flow | Done |
| 2026-04-05 | Admin payment settings + history | Done |
| 2026-04-06 | Rate limiting (auth 5/min, payment 15/min) | Done |
| 2026-04-06 | Reservation auto-cancel (5 min) | Done |
| 2026-04-06 | Refresh token cleanup scheduler | Done |
| 2026-04-06 | Single-session enforcement (V38) | Done |
| 2026-04-07 | QR code Caffeine cache (24h) | Done |
| 2026-04-07 | Correlation ID filter (X-Correlation-Id) | Done |
| 2026-04-07 | Resilience4j circuit breaker + retry | Done |
| 2026-04-07 | DB performance indexes (V40) | Done |
| 2026-04-07 | JDBC batch optimization (batch_size=25) | Done |
| 2026-04-07 | Swagger restricted to ADMIN | Done |
| 2026-04-07 | Frontend: shimmer loading, brand tokens, page transitions | Done |
| 2026-04-07 | Frontend: status/payment colors centralized | Done |
| 2026-04-07 | Frontend: image cache + fadeIn, state views i18n + a11y | Done |

---

## 📝 Notes

- Azure AD B2C is NOT available for new projects as of 2024
- Consider using Entra ID (Azure AD) for enterprise auth
- For consumer auth, consider alternatives like Auth0 or building custom JWT auth
