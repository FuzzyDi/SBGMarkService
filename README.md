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
- `GET /api/v1/reports/summary`
- `GET /api/v1/reports/history` (supports filters: `eventType`, `shopId`, `posId`, `cashierId`, `success`, `markCode`, `from`, `to`, `limit`)
- `GET /api/v1/reports/history.csv` (same filters, CSV export)
- `GET /api/v1/km/debug/marks`
- `GET /api/v1/km/debug/fifo-by-product?productType=...&item=...&gtin=...&limit=...`

## E2E validation

- Full cashier flow checklist: `docs/e2e-staging-cashier-checklist.md`
- Postman collection and environment: `docs/postman/`
- Excel import guide: `docs/km-import-excel.md`

## FIFO and lifecycle

- Sale flow: `AVAILABLE -> RESERVED -> SOLD`
- Return flow: `SOLD -> RETURN_RESERVED -> AVAILABLE`
- FIFO selection uses `fifoTs` for eligible marks.
- After successful return confirm, mark is returned to `AVAILABLE` and `fifoTs` is refreshed.
- Idempotency for all operation routes is persisted in DB (`idempotency_entries`) by `route + operationId`.
- Debug FIFO endpoint returns queue position and suitability reason (`OK`, `BLOCKED_FLAG_TRUE`, `STATUS_*`, etc.).
