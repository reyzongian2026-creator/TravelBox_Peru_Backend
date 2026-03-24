# Architecture - TravelBox Peru on Azure

**Last Updated:** March 24, 2026

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│                                                                             │
│   ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐       │
│   │   Web Browser   │     │   Android App   │     │    iOS App      │       │
│   └────────┬────────┘     └────────┬────────┘     └────────┬────────┘       │
└────────────┼───────────────────────┼───────────────────────┼─────────────────┘
             │                       │                       │
             └───────────────────────┴───────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AZURE FRONTEND                                     │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │              Azure App Service - Frontend                         │     │
│   │                   (travelbox-frontend-prod)                        │     │
│   │                         P1V2 Linux                               │     │
│   └─────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTPS
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AZURE BACKEND                                      │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────┐     │
│   │              Azure App Service - Backend                          │     │
│   │                    (travelbox-backend-prod)                       │     │
│   │                          P1V2 Linux                               │     │
│   └─────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│   │  Auth    │  │  Users   │  │Reserva-  │  │Delivery  │              │
│   │  Service  │  │ Service  │  │  tions   │  │ Service  │              │
│   └──────────┘  └──────────┘  └──────────┘  └──────────┘              │
│                                                                             │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│   │ Payments  │  │  Geo/    │  │  Ops    │  │  Email   │              │
│   │  Culqi    │  │ Routing  │  │  QR     │  │ Service  │              │
│   └──────────┘  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
             │                       │                       │
             ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Azure DB      │     │  Azure Blob    │     │    Azure AI     │
│   PostgreSQL    │     │    Storage      │     │   Translator    │
│   Flexible     │     │                 │     │                 │
│  ┌───────────┐ │     │  ┌───────────┐  │     │                 │
│  │ travelbox │ │     │  │ travelbox │  │     │                 │
│  │  (db)    │ │     │  │ -images  │  │     │                 │
│  └───────────┘ │     │  │ -profiles│  │     │                 │
│                │     │  │ -docs    │  │     │                 │
└─────────────────┘     │  └───────────┘  │     └─────────────────┘
                        └─────────────────┘
                                │
                                ▼
                    ┌─────────────────────┐
                    │    Azure Maps        │
                    │    (Gen2 SKU)       │
                    └─────────────────────┘
```

---

## Data Flow

### 1. User Authentication
- User → Frontend → Backend → JWT Token
- Firebase Auth disabled, awaiting replacement

### 2. Reservation Flow
```
User → Frontend → Backend → PostgreSQL (reservations table)
                   ↓
              Notification Queue → Email Service
```

### 3. Delivery Tracking
```
Backend → OSRM/Azure Maps → Route calculation
       → Frontend → Map Display (Azure Maps tiles)
```

### 4. Translation
```
Backend → Azure Translator API → Translated message
```

---

## Security

### Authentication Flow
- JWT tokens with 30-min access / 7-day refresh
- Rate limiting: 12 requests/minute per IP
- CORS configured for allowed origins

### Data Encryption
- TLS 1.2+ for transit
- Azure Disk Encryption at rest
- Custom encryption for sensitive fields

### Secrets Management
- All secrets in Azure Key Vault
- No secrets in code or Git

---

## Network Architecture

| Component | Public Access | VNet | Private Endpoints |
|-----------|--------------|------|------------------|
| Frontend App Service | Yes | No | No |
| Backend App Service | No (only via frontend) | No | No |
| PostgreSQL | No | No | Yes |
| Blob Storage | No | No | Yes |
| Key Vault | No | No | Yes |

---

## Disaster Recovery

| Resource | Backup | RTO | RPO |
|----------|--------|-----|-----|
| PostgreSQL | 7-day retention | 1 hour | 1 hour |
| Blob Storage | Geo-redundant | N/A | N/A |
| App Services | Auto-deploy from Git | 15 min | N/A |
| Key Vault | Azure-managed | N/A | N/A |

---

## Scalability

| Resource | Auto-scale | Min | Max |
|----------|------------|-----|-----|
| Backend App Service | CPU-based | 1 | 3 |
| Frontend App Service | CPU-based | 1 | 3 |
| PostgreSQL | Manual | - | - |

---

## Monitoring

- Azure Monitor for metrics
- Application Insights (if enabled)
- Log Analytics for centralized logging
- Azure Advisor for recommendations
