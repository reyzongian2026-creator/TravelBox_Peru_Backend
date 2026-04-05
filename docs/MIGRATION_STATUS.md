# Migration Status - TravelBox Peru to Azure

**Last Updated:** March 24, 2026  
**Status:** ✅ Complete (Infrastructure fully on Azure, GCP artifacts removed)

---

## 🔨 Git Commits

### Backend
- `8423f78` - "feat: add documentation, Azure resources endpoint, and move Firebase to deprecated"

### Frontend
- `53d7881` - "feat: move Firebase to deprecated and add Azure Resources monitoring tab"

---

## ✅ Completed Migrations

### Infrastructure
| Service | Destination (Azure) | Status |
|---------|---------------------|--------|
| Backend API | Azure App Service | ✅ Done |
| Frontend Web | Azure Static Web Apps | ✅ Done |
| Database | Azure Database for PostgreSQL Flexible | ✅ Done |
| Storage | Azure Blob Storage | ✅ Done |
| CI/CD | GitHub Actions → Azure | ✅ Done |

### Features
| Feature | Source | Destination | Status |
|---------|--------|-------------|--------|
| Maps | Google Maps | Azure Maps | ✅ Done |
| Translation | Google Translate | Azure Translator | ✅ Done |
| Authentication | Firebase Auth | ⚠️ Disabled (Pending) | ⚠️ Pending |
| Email | - | SMTP (Brevo) / Graph API | ⚠️ Partial |

---

## ⚠️ Partial / Pending

### Email Corporate (Graph API)
- **Backend:** Code supports Graph API ✅
- **Config:** `tbx-back-email-provider=smtp` (Brevo)
- **Graph API:** Configured but requires M365 license with Exchange mailbox
- **Current workaround:** Using Brevo SMTP relay

### Authentication
- **Firebase Auth:** Disabled by default (`firebase.enabled=false`)
- **Status:** No replacement configured yet
- **Options:** 
  - Azure AD B2C (not available for new projects)
  - Custom auth service
  - Third-party provider

---

## 📊 Current Configuration

### Azure Resources
| Resource | Name | SKU | Status |
|----------|------|-----|--------|
| App Service (Backend) | travelbox-backend-prod | P1V2 | Running |
| App Service (Frontend) | travelbox-frontend-prod | P1V2 | Running |
| PostgreSQL | travelbox-peru-db | Standard_D2s_v3 | Running |
| AI Services | travelbox-ai | S0 | Running |
| Azure Maps | travelbox-maps | Gen2 | Running |
| Key Vault | kvtravelboxpe | Standard | Running |

### Environment Variables (Key Vault)
All secrets stored in Azure Key Vault `kvtravelboxpe`:
- `tbx-back-db-url`, `tbx-back-db-username`, `tbx-back-db-password`
- `tbx-back-jwt-secret`, `tbx-back-encryption-key`
- `tbx-back-email-provider` = `smtp`
- `tbx-back-translation-azure-api-key`
- `tbx-back-azure-maps-api-key`
- `tbx-back-firebase-enabled` = `false`

---

## 🔧 How to Enable Graph API Email

1. Assign M365 license with Exchange Online to a user
2. Get user email (e.g., `admin@Weesystem.onmicrosoft.com`)
3. Update Key Vault secret:
   ```
   az keyvault secret set --vault-name "kvtravelboxpe" --name "tbx-back-email-provider" --value "graph"
   ```
4. Restart backend app

---

## 📁 Related Documents

- [PENDING_TASKS.md](./PENDING_TASKS.md) - Detailed pending items
- [AZURE_RESOURCES.md](./AZURE_RESOURCES.md) - Azure resources inventory
- [COSTS_ESTIMATE.md](./COSTS_ESTIMATE.md) - Monthly cost estimation
- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture
- [DEPRECATED.md](./DEPRECATED.md) - Deprecated files and reasons
