# device-management-service

Device inventory and lifecycle management for the IoTMining/IIoTEdge
platform: CRUD, bulk import/export, hardware identity mapping, and
tenant/customer-scoped queries for individual devices.

## Features

- **Device CRUD** (`/api/v1/devices`) with per-device access control via
  `DeviceSecurityEvaluator` (`@deviceSecurity.canAccessDevice`) in addition
  to role-based checks (`ROLE_SUPER_ADMIN`/`ROLE_TENANT_ADMIN`).
- **Bulk operations** (`DeviceBulkController`) — bulk create with
  per-item success/failure reporting (`BulkOperationResponse`), bulk
  credential/access-token rotation, all tenant-scoped.
- **Hardware identity mapping** (`/api/v1/devices/{deviceId}/identity`) —
  a device can accumulate multiple hardware identifiers (IMEI/MAC/serial/
  token) over its lifetime (hardware swaps); only one is active at a
  time, the rest are kept as history.
- **Query & export** (`DeviceQueryController`/`DeviceQueryService`) —
  filtered listing and CSV export, tenant-scoped.
- **Device profile resolution** — calls device-profile-service via
  OpenFeign (`DeviceProfileClient`) to validate/resolve the profile a
  device is assigned to; a profile-service outage degrades gracefully
  (devices are still returned, just without resolved-profile enrichment)
  rather than failing the whole request.
- **Stateless JWT auth** — `LiteJwtAuthenticationFilter` validates the
  bearer token and builds the `Authentication` from its claims; tokens
  are issued by auth-service and shared across services via a common
  signing secret. `@EnableMethodSecurity` backs every `@PreAuthorize` on
  the controllers above.

## Running locally

Requires local Postgres (`iotmining` database), and optionally Eureka
(`localhost:8761`), Config Server (`localhost:8888`), and
device-profile-service (`localhost:8087`, or override via
`app.services.device-profile.url`) — all are `optional`/graceful, so the
service still boots and serves device CRUD without them.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Default port: `8055` (overridable via `SERVER_PORT`). JWT verification
requires `JWT_SECRET_KEY_BASE64` to match the key auth-service signs
with; the `dev` profile falls back to the same shared local-dev secret
used across this platform's other services if unset.

## Build

```bash
./mvnw clean verify
```

Runs the full test suite (35 tests) plus SpotBugs/FindSecBugs (see
`spotbugs-exclude.xml` for narrowly-scoped, justified exclusions) and a
JaCoCo coverage report. The JaCoCo *check* gate is currently disabled
(actual coverage is ~23%/29%, well under the platform's usual 85%/65%
default) - the report still runs so real numbers stay visible; see
`pom.xml`'s comment on the `coverage-check` execution.

Depends on `com.iotmining.common:data`/`:base`/`:utils` from the
separate [`common`](https://github.com/iiotedge/common) repo - build/
install those modules first if they aren't already in your local
`~/.m2`.

## Parent / dependency management

Resolves `iiotedge-parent`/`iiotedge-bom` from GitHub Packages
([`platform-build`](https://github.com/iiotedge/platform-build)) rather
than a local relative-path parent - this repo can be built standalone
with just this checkout (plus `common`, above), no monorepo checkout
required.

## Docker

```bash
./mvnw clean package -DskipTests
docker build -t device-management-service .
docker run -p 8055:8055 device-management-service
```

The image is runtime-only (see `Dockerfile`): the jar is built beforehand
by CI, not inside the image, so quality gates in CI (`mvn verify`) are a
real gate that can't be bypassed by skipping straight to the Docker
build.
