# TravelBox Peru - Business Workflows Documentation

## 1. Reservation Lifecycle

### 1.1 Status Flow
```
DRAFT → PENDING_PAYMENT → CONFIRMED → CHECKIN_PENDING → STORED → READY_FOR_PICKUP → OUT_FOR_DELIVERY → COMPLETED
                                              ↓
                                          INCIDENT → (resolved to STORED, READY_FOR_PICKUP, COMPLETED, or CANCELLED)
                                           ↓
                                        CANCELLED (from DRAFT, PENDING_PAYMENT, CONFIRMED, CHECKIN_PENDING)
                                           ↓
                                        EXPIRED (from PENDING_PAYMENT only)
```

### 1.2 QR/PIN Handoff Stages (Warehouse Operations)
```
DRAFT → QR_VALIDATED → BAG_TAGGED → STORED_AT_WAREHOUSE → READY_FOR_PICKUP
                                                         ↓
                                         PICKUP_PIN_VALIDATED (for customer pickup)
                                         
                                         DELIVERY_IDENTITY_VALIDATED → DELIVERY_LUGGAGE_VALIDATED → DELIVERY_APPROVAL_PENDING → DELIVERY_APPROVAL_GRANTED → DELIVERY_COMPLETED (for courier delivery)
```

---

## 2. Role Permissions Matrix

| Feature | CLIENT | COURIER | OPERATOR | CITY_SUPERVISOR | ADMIN |
|---------|--------|---------|----------|-----------------|-------|
| Create Reservation | Yes | No | Yes (assisted) | Yes (assisted) | Yes |
| Cancel Own Reservation | Yes | No | No | No | Yes |
| Scan QR | No | Yes | Yes | Yes | Yes |
| Tag Luggage | No | No | Yes | Yes | Yes |
| Store with Photos | No | No | Yes | Yes | Yes |
| Mark Ready for Pickup | No | No | Yes | Yes | Yes |
| Validate Pickup PIN | No | No | Yes | Yes | Yes |
| Claim Delivery | No | Yes | Yes | Yes | Yes |
| Update Delivery Progress | No | Yes | Yes | Yes | Yes |
| Complete Delivery | No | Yes | Yes | Yes | Yes |
| Approve Delivery Issues | No | No | Yes | Yes | Yes |
| View All Deliveries | No | Own only | Own warehouse | Own warehouses | All |
| Manage Users | No | No | No | No | Yes |
| Manage Warehouses | No | No | No | No | Yes |
| View Reports | No | No | Limited | Limited | All |
| Bulk Operations | No | No | No | No | Yes |
| Export Data | No | No | No | No | Yes |

---

## 3. Step-by-Step Workflows

### 3.1 CLIENT: Create Reservation
```
Step 1: Validate Prerequisites
    → Email must be verified
    → Profile must be completed
    ↓
Step 2: Select warehouse and date range
Step 3: Select bag size (S/M/L/XL)
Step 4: Optionally request pickup/dropoff
Step 5: Checkout and payment
Step 6: Receive confirmation with QR code
```

### 3.2 OPERATOR/CITY_SUPERVISOR: Warehouse Storage Flow (Pickup)
```
Step 1: Scan QR Code
    → Creates/loads QrHandoffCase
    → Stage: DRAFT → QR_VALIDATED
    ↓
Step 2: Tag Luggage (REQUIRED)
    → Generates bag tag ID
    → Sets bag units count
    → Stage: QR_VALIDATED → BAG_TAGGED
    ↓
Step 3: Store with Photos (REQUIRED)
    → Requires bag tag already assigned
    → Requires exactly N photos (one per bag unit)
    → Stage: BAG_TAGGED → STORED_AT_WAREHOUSE
    → Reservation: CONFIRMED/CHECKIN_PENDING → STORED
    ↓
Step 4: Mark Ready for Pickup
    → Generates PIN for pickup
    → Stage: STORED_AT_WAREHOUSE → READY_FOR_PICKUP
    → Reservation: STORED → READY_FOR_PICKUP
    ↓
Step 5: Confirm Presential Pickup (if customer picks up)
    → Validates PIN
    → Stage: READY_FOR_PICKUP → PICKUP_PIN_VALIDATED
    → Reservation: READY_FOR_PICKUP → COMPLETED
```

### 3.3 COURIER: Delivery Flow
```
Step 1-4: Same as Warehouse Storage Flow (Steps 1-4 above)
Step 5: Mark Ready for Pickup (generates PIN for courier)
Step 6: System transitions reservation to OUT_FOR_DELIVERY
Step 7: Courier claims delivery order
Step 8: Courier validates:
    a) Identity validated (REQUIRED FIRST)
    b) Luggage matched (REQUIRED AFTER IDENTITY)
    c) If issues, request approval
Step 9: If approval requested:
    → Operator approves → PIN generated
    → Operator rejects → Courier notified
Step 10: Courier completes delivery with PIN
    → Stage: DELIVERY_APPROVAL_GRANTED → DELIVERY_COMPLETED
    → Reservation: OUT_FOR_DELIVERY → COMPLETED
```

---

## 4. Validation Rules

### 4.1 Cannot Skip Steps
| Step | Cannot Proceed Without |
|-------|----------------------|
| Tag Luggage | QR scan completed |
| Store | Bag tag generated |
| Store Photos | Bag tag generated + correct photo count |
| Ready for Pickup | Stored in warehouse |
| Validate Pickup PIN | Ready for pickup status |
| Validate Luggage | Identity validated FIRST |
| Request Approval | Identity + Luggage validated |
| Complete Delivery | Identity + Luggage + Approval (if needed) |

### 4.2 Cancellation Rules
- **Can cancel from:** DRAFT, PENDING_PAYMENT, CONFIRMED, CHECKIN_PENDING
- **Cannot cancel if:** Reservation has confirmed digital payment (refund required)

### 4.3 Assisted Reservations
- Operator must verify customer email is verified
- Operator must verify customer profile is completed

---

## 5. Error Codes

| Code | Description |
|------|-------------|
| ACCOUNT_EMAIL_NOT_VERIFIED | Email verification required |
| PROFILE_COMPLETION_REQUIRED | Profile must be completed |
| ASSISTED_CUSTOMER_EMAIL_NOT_VERIFIED | Assisted customer email not verified |
| ASSISTED_CUSTOMER_PROFILE_INCOMPLETE | Assisted customer profile incomplete |
| DUPLICATE_RESERVATION_DETECTED | Rapid duplicate reservation prevention |
| INVALID_DATE_RANGE | End date must be after start date |
| WAREHOUSE_CAPACITY_FULL | No availability for time slot |
| BAG_TAG_REQUIRED | Generate bag tag before storing |
| LUGGAGE_PHOTOS_COUNT_MISMATCH | Photo count must match bag units |
| STORE_NOT_ALLOWED | Cannot store in current status |
| DELIVERY_STEP_ORDER_INVALID | Must validate identity before luggage |
| PIN_INVALID | Incorrect PIN entered |
| PIN_EXPIRED | PIN has expired |
| PIN_LOCKED | Too many attempts, temporarily locked |
| ADMIN_USER_CANNOT_BE_DISABLED | Admin cannot be deactivated |
| WAREHOUSE_SCOPE_FORBIDDEN | No access to warehouse |

---

## 6. API Rate Limits

| Endpoint | Limit |
|----------|-------|
| General API | 100 requests/min, 1000 requests/hour |
| Admin API | 60 requests/min, 500 requests/hour |
| Auth endpoints | 10 requests/min |

---

## 7. WebSocket Events

| Event | Trigger |
|-------|---------|
| reservation_created | New reservation created |
| reservation_updated | Reservation status changed |
| reservation_cancelled | Reservation cancelled |
| notification | General notification |
| delivery_status | Delivery status changed |
| dashboard_stats | Dashboard data updated |
| approval_required | Delivery approval needed |

---

## 8. Health Check Endpoints

| Endpoint | Auth | Description |
|----------|------|-------------|
| `/health` | None | Simple liveness check |
| `/` | None | Root endpoint returning service info |
| `/actuator/health` | None | Spring Boot Actuator health (includes DB, Redis, etc.) |
| `/actuator/health/liveness` | None | Kubernetes liveness probe |
| `/actuator/health/readiness` | None | Kubernetes readiness probe |

### Required Azure Key Vault Secrets

| Secret Name | Description |
|-------------|-------------|
| `tbx-back-db-url` | PostgreSQL connection URL |
| `tbx-back-db-username` | Database username |
| `tbx-back-db-password` | Database password |
| `tbx-back-jwt-secret` | JWT signing secret (min 256 bits) |
| `tbx-back-smtp-host` | SMTP server host |
| `tbx-back-smtp-username` | SMTP username |
| `tbx-back-smtp-password` | SMTP password |
| `tbx-back-firebase-sa-json` | Firebase service account JSON |

---

*Last updated: March 2026*

