# sbg-marking-auto-km

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

Default DB connection (can be overridden by env vars):

- `SBG_MARKING_DB_URL=jdbc:postgresql://localhost:5432/sbg_marking`
- `SBG_MARKING_DB_USER=postgres`
- `SBG_MARKING_DB_PASSWORD=postgres`

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
- `GET /api/v1/reports/summary`
- `GET /api/v1/reports/history`
- `GET /api/v1/km/debug/marks`

## FIFO and lifecycle

- Sale flow: `AVAILABLE -> RESERVED -> SOLD`
- Return flow: `SOLD -> RETURN_RESERVED -> AVAILABLE`
- FIFO selection uses `fifoTs` for eligible marks.
- After successful return confirm, mark is returned to `AVAILABLE` and `fifoTs` is refreshed.
- Idempotency for all operation routes is persisted in DB (`idempotency_entries`) by `route + operationId`.
