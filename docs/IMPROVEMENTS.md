# Device Management Service — Industry-Readiness Roadmap

**Date:** 2026-07-09 · **Scope:** gap analysis vs. industry device-management platforms
(ThingsBoard, AWS IoT Core, Azure IoT Hub) + fixes/features implemented in this pass.

Legend: ✅ done in this pass · 🔒 needs a coordinated rollout (breaking risk) · 📋 backlog

---

## 1. Bugs found (and their status)

| # | Issue | Impact | Status |
|---|---|---|---|
| B1 | `GET /{id}/owner` throws NPE when device has no customer — `Map.of()` rejects null values → **500 to TSS/TNS** on every unassigned device | Internal lookups fail for unassigned devices | ✅ fixed (returns `{"ownerId": null}` as the contract documents) |
| B2 | `DELETE /{id}` fails with an FK constraint violation when the device has an identity mapping (`device_identities.device_id`), and even where it succeeds it **orphans the hardware-ID mapping**, leaving stale Redis routing entries | Deletes broken for provisioned devices; deleted devices keep receiving telemetry | ✅ fixed (identity mapping + Redis cache evicted before device delete) |
| B3 | `PUT /{id}` silently ignores `location` in the payload | Device location can never be updated | ✅ fixed |
| B4 | `GET /api/v1/customers` returns **random fake UUIDs** — assigning one as owner creates an FK violation or a dangling reference | Owner assignment from UI is broken | ✅ fixed (backed by `CustomerRepository`, same response shape) |
| B5 | `validateDeviceTypeForCategory` is an empty TODO — mismatched category/type pairs are persisted | Dirty data | ✅ implemented as **warn-only** (logs mismatch; does not reject, so no existing caller breaks). Tighten to hard-fail once clients are audited. |
| B6 | `GET /api/v1/devices/labels` returns hardcoded fake labels with fresh random UUIDs on every call | Label filters can never match real data | 📋 left untouched (a `Label` entity exists but no repository; UI may depend on the mock shape — needs a small UI-coordinated change) |

## 2. Security findings (documented, NOT changed — coordinate before enabling)

| # | Finding | Why not fixed here |
|---|---|---|
| S1 | 🔒 **All `@PreAuthorize` rules are inert.** No `@EnableMethodSecurity` exists anywhere, and `SecurityFilterChain` permits `/**`. Every endpoint is effectively open (modulo gateway). | Turning it on instantly enforces role/tenant checks on every consumer (UI, TSS, TNS, ingestion). Must be enabled in a coordinated release with verified JWT claims end-to-end. |
| S2 | 🔒 JWT secret and internal API key are **hardcoded in application.yml** (and identical across services). | Rotation requires updating every consumer simultaneously; move to env/secret store in a platform-wide change. |
| S3 | Dev DB credentials in `application-dev.yml` committed to repo. | Dev-only; move to `.env`/profile overrides at next convenience. |
| S4 | `POST /api/v1/internal/provision` is a no-op stub that accepts unauthenticated input. | Dead code — delete or implement behind `ROLE_SYSTEM` when Cassandra provisioning returns. |

## 3. Features added in this pass ✅

Industry-standard device-fleet operations, all **additive** (no existing endpoint signature,
path, or response changed):

| Feature | Endpoint | Notes |
|---|---|---|
| **Bulk create** | `POST /api/v1/devices/bulk` | JSON array (≤500/request); per-item result: `CREATED` / `SKIPPED` (duplicate name) / `FAILED` (+reason); each item in its own transaction so one bad row never rolls back the batch; optional `hardwareId`/`identityType` auto-creates the identity mapping atomically with the device |
| **CSV import** | `POST /api/v1/devices/import` (multipart `file`) | Same engine as bulk create; RFC-4180-style parsing (quoted fields); header row required; returns the same per-row report |
| **Bulk delete** | `POST /api/v1/devices/bulk-delete` | Body = array of UUIDs (≤500); per-item results; cleans identity mappings + cache first |
| **Bulk state change** | `POST /api/v1/devices/bulk-state?state=` | Set state for a list of device IDs |
| **Single state change** | `PUT /api/v1/devices/{id}/state?state=` | Service method existed but was never exposed |
| **Search & filter** | `GET /api/v1/devices/search` | `tenantId` (required) + `q` (name contains, case-insensitive), `state`, `category`, `deviceType`, `protocol`, `profileId`, `customerId`, `isPublic`; paginated, size capped at 200 |
| **Fleet statistics** | `GET /api/v1/devices/stats?tenantId=` | Total + `byState` / `byCategory` / `byType` breakdowns in 4 grouped queries — powers dashboards without N calls |
| **CSV export** | `GET /api/v1/devices/export?tenantId=` | Download the tenant's fleet as CSV (name, type, state, token, hardware id…) for offline audit / re-import |
| **Credential rotation** | `POST /api/v1/devices/{id}/credentials/rotate` | Regenerates the device access token. ⚠️ ingestion caches `auth:token:*` in Redis — old token stays valid until TTL/eviction; see backlog item F7 |
| **Consistent API errors** | (cross-cutting) | `GlobalExceptionHandler`: `IllegalArgumentException`→400, `SecurityException`→403, `IllegalStateException`→503, validation errors→400 with field details. Previously these surfaced as whitelabel 500s. |

New code lives in new classes (`DeviceBulkService`, `DeviceQueryService`,
`DeviceBulkController`, `DeviceQueryController`, `dto/bulk/*`,
`GlobalExceptionHandler`); the only existing files touched are the
small bug fixes (B1–B5) and one additive `extends JpaSpecificationExecutor`
on `DeviceRepository`. Existing routes untouched.

**Verified end-to-end** against the local dev database: service boots, `/stats`
returns real grouped counts, `/search` paginates, `/export` produces re-importable
CSV, `/bulk` creates + skips duplicates with per-row results, empty batches get a
clean 400 from the new error handler, `/customers` serves real tenant data.

> Build note: if the app fails to start with a
> `ConflictingBeanDefinitionException` for `deviceIdentityService`, run
> `./mvnw clean` once — a stale `target/` class from before the
> `service`/`services` package rename shadows the current one (see F11).

## 4. Backlog (recommended next, in order)

- **F1 — Enable method security (S1)** behind a feature flag; verify each consumer's roles in staging.
- **F2 — Device claiming flow**: factory-preloaded devices claimable by customers with a claim key (AWS IoT "fleet provisioning" pattern).
- **F3 — Real labels CRUD** (`LabelRepository` + tenant-scoped label management, replace B6 mock).
- **F4 — Last-seen / connectivity status**: `last_connected_at` column updated by ingestion (via internal endpoint or Kafka event); expose `ONLINE/OFFLINE` derived state; alarm on silence.
- **F5 — OTA integration**: firmware/software entities exist but have no endpoints — assignment endpoints + rollout batches (device-control-service owns execution).
- **F6 — Audit log**: who created/deleted/rotated what and when (append-only table; required in industrial compliance contexts).
- **F7 — Token rotation cache sync**: on rotate, publish an event (or call ingestion's internal API) to evict `auth:token:{old}` from Redis immediately.
- **F8 — Soft delete / archive** instead of hard delete, so historical telemetry keeps a referent.
- **F9 — OpenAPI spec** (springdoc) — other teams currently integrate by reading source.
- **F10 — Tests + JaCoCo + Sonar** — same setup as auth-service/rulechain-service (service currently has zero tests).
- **F11 — Fix package naming drift**: `services/` vs `service/` (DeviceIdentityService), `configuration/` vs other services' `config/`.
- **F12 — Pagination caps on legacy list endpoints** (currently a client can ask for `size=100000`).
