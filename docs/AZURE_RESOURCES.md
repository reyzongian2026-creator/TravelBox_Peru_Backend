# Azure Resources - TravelBox Peru

**Last Updated:** March 24, 2026

---

## Subscription Information

| Field | Value |
|-------|-------|
| Subscription ID | `33815caa-4cfb-4a9e-b60a-8fee5caa2b08` |
| Tenant ID | `a574d6a3-a5ed-4f76-8e5a-232160f91d34` |
| Resource Group | `travelbox-peru-rg` |
| Location | West US 3 |

---

## Compute

### App Services

| Resource | Name | SKU | Status | URL |
|----------|------|-----|--------|-----|
| Backend | travelbox-backend-prod | P1V2 | Running | https://travelbox-backend-prod.azurewebsites.net |
| Frontend | travelbox-frontend-prod | P1V2 | Running | https://travelbox-frontend-prod.azurewebsites.net |
| App Service Plan | travelbox-peru-linux-plan | P1V2 | Running | - |

---

## Data

### Azure Database for PostgreSQL Flexible

| Field | Value |
|-------|-------|
| Server Name | `travelbox-peru-db.postgres.database.azure.com` |
| Version | 16 |
| SKU | Standard_D2s_v3 |
| Storage | 32 GB |
| Backup Retention | 7 days |
| SSL | Required |
| Database | `travelbox` |

---

## AI Services

### Azure AI Services (Translator)

| Field | Value |
|-------|-------|
| Name | `travelbox-ai` |
| SKU | S0 (Standard) |
| Location | East US |
| Endpoint | https://travelbox-ai.cognitiveservices.azure.com/ |

---

## Maps

### Azure Maps

| Field | Value |
|-------|-------|
| Name | `travelbox-maps` |
| SKU | Gen2 |
| Location | Global |

---

## Security

### Azure Key Vault

| Field | Value |
|-------|-------|
| Name | `kvtravelboxpe` |
| SKU | Standard |
| Location | West US 3 |
| Soft Delete | 90 days |
| Purge Protection | Disabled |

**Secrets Stored:**
- Database credentials
- JWT secret
- Encryption key
- Email provider config
- Azure API keys
- Firebase (disabled)

---

## Identity

### Azure AD App Registration

| Field | Value |
|-------|-------|
| Name | `TravelBoxAuth` |
| App ID | `c3bc7d6d-41b7-4dee-aa14-0485275ba2ad` |
| Object ID | `dbffb2a3-73c2-4dbd-8538-67a399e10ea4` |
| Tenant ID | `a574d6a3-a5ed-4f76-8e5a-232160f91d34` |

**Permissions:**
- Mail.Send (Microsoft Graph)

---

## Storage

### Azure Blob Storage

| Container | Purpose |
|-----------|---------|
| `travelbox-images` | Product images |
| `travelbox-profiles` | User profile photos |
| `travelbox-warehouses` | Warehouse images |
| `travelbox-documents` | User documents |
| `travelbox-evidences` | Delivery evidences |
| `travelbox-reports` | Generated reports |
| `travelbox-exports` | Data exports |

---

## Service Principals

| Service | Object ID |
|---------|-----------|
| TravelBoxAuth | `6faed558-8091-4e10-8ede-29f733a06339` |
| Microsoft Graph | `da62dd90-f99b-4ab1-a2c6-ae5e483cbd53` |

---

## How to Check Resources

```bash
# List all resources in subscription
az resource list --subscription "33815caa-4cfb-4a9e-b60a-8fee5caa2b08"

# List App Services
az webapp list --resource-group travelbox-peru-rg

# List PostgreSQL servers
az postgres flexible-server list --resource-group travelbox-peru-rg

# List Key Vaults
az keyvault list --resource-group travelbox-peru-rg

# List Azure AD Apps
az ad app list --filter "displayName eq 'TravelBoxAuth'"
```
