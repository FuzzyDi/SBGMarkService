# sbg-marking-auto-km
[![SBG Marking CI](https://github.com/FuzzyDi/SBGMarkService/actions/workflows/ci.yml/badge.svg)](https://github.com/FuzzyDi/SBGMarkService/actions/workflows/ci.yml)
[![SBG Set10 Plugin Build](https://github.com/FuzzyDi/SBGMarkService/actions/workflows/plugin-build.yml/badge.svg)](https://github.com/FuzzyDi/SBGMarkService/actions/workflows/plugin-build.yml)

Multi-module Maven project for automatic KM substitution in Set Retail 10:

- `sbg-marking-contracts` - shared DTO/contracts.
- `sbg-marking-server` - backend for FIFO selection, reservations, sale/return confirmation, import, history, reports.
- `sbg-set10-marking-plugin` - Set10 `ExciseValidationPluginExtended` integration.

## Build

```powershell
$env:MAVEN_OPTS='-Dmaven.repo.local=E:\Projects\SbgPosAgent\marking-auto-km\.m2'
mvn -DskipTests package
```

## Tests

```powershell
$env:MAVEN_OPTS='-Dmaven.repo.local=E:\Projects\SbgPosAgent\marking-auto-km\.m2'
mvn -pl sbg-marking-server -am test
```

Integration tests use in-memory H2 for:

- FIFO auto selection
- sale -> sold confirm -> return -> return confirm
- idempotency by `operationId`

CI (GitHub Actions):

- `.github/workflows/ci.yml`
- Automatically runs on `push` and `pull_request` to `main`
- Verifies `sbg-marking-contracts` and `sbg-marking-server`
- Runs staging smoke via Docker Compose (`actuator/health`, KM import, reports endpoints)
- `.github/workflows/plugin-build.yml`
- Builds `sbg-set10-marking-plugin` (manual or PR path-trigger), requires GitHub secret `SET10_API_JAR_BASE64`
- If secret is missing, workflow is skipped with a notice (no failure)
- Secret setup guide: `docs/github-set10-secret.md`

## Run server

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km\sbg-marking-server
mvn spring-boot:run
```

Default server URL: `http://localhost:8080`

Server Web UI:

- Open `http://localhost:8080/`
- UI supports Excel/JSON import, resolve/confirm/release calls, FIFO debug, validation check/policy, admin mark management, and reports/history CSV.

## Run PostgreSQL (docker)

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km
Copy-Item .env.example .env
docker compose up -d
```

## Run Staging Stack (server + postgres in Docker)

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km
Copy-Item .env.staging.example .env
docker compose -f docker-compose.staging.yml up -d --build
```

Or use helper scripts:

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km
.\scripts\staging-up.ps1
.\scripts\staging-smoke.ps1
.\scripts\staging-down.ps1
```

Use `.\scripts\staging-down.ps1 -WithVolumes` to remove volumes too.

Default DB connection (can be overridden by env vars):

- `SBG_MARKING_DB_URL=jdbc:postgresql://localhost:5432/sbg_marking`
- `SBG_MARKING_DB_USER=postgres`
- `SBG_MARKING_DB_PASSWORD=postgres`
- `sbg.marking.idempotency.retention.days=30` (cleanup threshold for `idempotency_entries`)

Staging auth defaults in `.env.staging.example`:

- `SBG_MARKING_AUTH_ENABLED=true`
- `SBG_MARKING_AUTH_ADMIN_TOKEN=admin-secret-change-me`
- `SBG_MARKING_AUTH_OPERATOR_TOKEN=operator-secret-change-me`

`scripts/staging-smoke.ps1` auto-reads `.env` and sends role/token headers when auth is enabled.

Flyway migration is applied on startup from:

- `src/main/resources/db/migration`

## Core API

- `POST /api/v1/marking/resolve-and-reserve`
- `POST /api/v1/marking/return-resolve-and-reserve`
- `POST /api/v1/marking/sold-confirm`
- `POST /api/v1/marking/sale-release`
- `POST /api/v1/marking/return-confirm`
- `POST /api/v1/marking/return-release`
- `POST /api/v1/km/import/full`
- `POST /api/v1/km/import/delta`
- `POST /api/v1/km/import/full/excel` (`multipart/form-data`)
- `POST /api/v1/km/import/delta/excel` (`multipart/form-data`)
- `POST /api/v1/validation/check`
- `GET /api/v1/validation/policy`
- `PUT /api/v1/validation/policy`
- `GET /api/v1/admin/marks`
- `POST /api/v1/admin/marks`
- `PUT /api/v1/admin/marks/{markCode}`
- `DELETE /api/v1/admin/marks/{markCode}`
- `GET /api/v1/admin/audit`
- `GET /api/v1/reports/summary`
- `GET /api/v1/reports/history` (supports filters: `eventType`, `shopId`, `posId`, `cashierId`, `success`, `markCode`, `from`, `to`, `limit`)
- `GET /api/v1/reports/history.csv` (same filters, CSV export)
- `GET /api/v1/km/debug/marks`
- `GET /api/v1/km/debug/fifo-by-product?productType=...&item=...&gtin=...&limit=...`

## API role access (optional)

Authentication/authorization can be enabled by env vars:

- `SBG_MARKING_AUTH_ENABLED=true`
- `SBG_MARKING_AUTH_ADMIN_TOKEN=<admin-secret>`
- `SBG_MARKING_AUTH_OPERATOR_TOKEN=<operator-secret>`

Headers:

- `X-SBG-Role: ADMIN|OPERATOR`
- `X-SBG-Token: <role token>`
- `X-SBG-User: <optional actor id for audit>`
- `X-Request-Id: <optional request id for audit>`

Role scope:

- `ADMIN`: `/api/v1/admin/**`, `/api/v1/validation/policy`, `/api/v1/km/import/**`
- `OPERATOR` (or `ADMIN`): `/api/v1/marking/**`, `/api/v1/validation/check`, `/api/v1/reports/**`, `/api/v1/km/debug/**`

## E2E validation

- Full cashier flow checklist: `docs/e2e-staging-cashier-checklist.md`
- Postman collection and environment: `docs/postman/`
- Excel import guide: `docs/km-import-excel.md`
- Management + validation API guide: `docs/marking-management-api.md`

## FIFO and lifecycle

- Sale flow: `AVAILABLE -> RESERVED -> SOLD`
- Return flow: `SOLD -> RETURN_RESERVED -> AVAILABLE`
- FIFO selection uses `fifoTs` for eligible marks.
- After successful return confirm, mark is returned to `AVAILABLE` and `fifoTs` is refreshed.
- Idempotency for all operation routes is persisted in DB (`idempotency_entries`) by `route + operationId`.
- Debug FIFO endpoint returns queue position and suitability reason (`OK`, `BLOCKED_FLAG_TRUE`, `STATUS_*`, etc.).
