# API Versioning Strategy

## Current Version: v1

All current endpoints use `/api/v1/` prefix.

Example: `GET /api/v1/admin/dashboard`

## Future v2 Migration

When v2 is needed:

1. **Create new controllers** in separate packages:
   - `com.tuempresa.storage.v2.admin.*`
   - These will have `@RequestMapping("/api/v2/admin")`

2. **Keep v1 for backwards compatibility** until all clients migrate

3. **Key differences for v2**:
   - Use `BigDecimal` for all monetary values (backend already supports)
   - Use `String` for IDs instead of `Long` to avoid overflow
   - Add pagination cursor-based instead of offset
   - Add OpenAPI documentation

## Deprecation Policy

When v2 is released:
- v1 will be marked as `@Deprecated`
- v1 will remain functional for minimum 6 months
- After 12 months, v1 may be removed with notice

## Version Detection

Clients should send `Accept-Version: 1` header or use `/api/v1/` URL.

Future: Support `Accept-Version: 2` for v2 endpoints.
