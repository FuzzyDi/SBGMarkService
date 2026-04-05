# Project Status (2026-04-06)

This document captures the current baseline for `sbg-marking-auto-km`: what is done, what decisions are fixed, and what is still pending.

## 1) Fixed business decisions

- No manual KM selection list on POS.
- Sale flow:
- if scanned KM is suitable, use scanned KM
- if scanned KM is absent in pool, auto-select first suitable FIFO KM
- if no suitable KM exists, reject with `NO_CANDIDATE`
- Return flow is supported end-to-end:
- after successful `return-confirm`, mark goes to `AVAILABLE`
- mark can be sold again
- Server updates mark lifecycle so already sold marks are not selected incorrectly.
- Production may run with `SBG_MARKING_AUTH_ENABLED=false` when security is provided by external perimeter.
- Plugin keeps both current and future mark types enabled:
- `SPIRITS`, `TOBACCO`, `FOOTWEAR`, `JEWELRY`, `PERFUMES`, `LIGHT_INDUSTRY`, `TYRES`, `PHOTO`, `MILK`, `BEER`, `DRAFT_BEER`, `NONALCOHOLIC_BEER`, `WATER_AND_BEVERAGES`, `DRUGS`, `MEDICAL_DEVICES`

## 2) Implemented scope

### 2.1 Server (`sbg-marking-server`)

- Full KM lifecycle endpoints:
- `resolve-and-reserve`
- `sold-confirm`
- `sale-release`
- `return-resolve-and-reserve`
- `return-confirm`
- `return-release`
- FIFO auto-selection and FIFO debug reasons:
- `/api/v1/km/debug/fifo-by-product`
- KM import:
- JSON full/delta
- Excel full/delta
- Validation API:
- `/api/v1/validation/check`
- `/api/v1/validation/policy` (GET/PUT)
- Admin KM management:
- `/api/v1/admin/marks` (GET/POST/PUT/DELETE)
- `/api/v1/admin/audit`
- History and reports:
- `/api/v1/reports/summary`
- `/api/v1/reports/history`
- `/api/v1/reports/history.csv`
- Idempotency persistence by `route + operationId`.
- Flyway migrations + PostgreSQL persistence.
- Built-in web UI at `/` for import, operations, FIFO debug, validation, admin, history, CSV.

### 2.2 Plugin (`sbg-set10-marking-plugin`)

- Set10 integration via `ExciseValidationPluginExtended`.
- Calls backend resolve APIs during sale/refund validation.
- Sends finalize/release operations on fiscalized event:
- `sold-confirm`, `sale-release`, `return-confirm`, `return-release`
- Supports retry through `Feedback` (`onRepeatSend`).
- Plugin naming and IDs use `sbg` prefix.

### 2.3 Contracts (`sbg-marking-contracts`)

- Shared DTO set for resolve/return/operations/import/validation/history/reports/admin/FIFO debug.

### 2.4 Delivery and deployment

- GitHub Actions for server/contracts and separate plugin workflow.
- Docker Compose and staging stack scripts.
- Windows Server deployment guide and scripts for host `192.168.80.31`.

## 3) Verified on this snapshot

- Server tests passed:
- Command: `mvn -pl sbg-marking-server -am test`
- Result: `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`
- Plugin build passed:
- Command: `mvn -pl sbg-set10-marking-plugin -am package -DskipTests`
- Artifact: `sbg-set10-marking-plugin-1.0.0.jar`
- Manifest check for `sbg-set10-marking-plugin-1.0.0.jar`:
- `Implementation-Version: 1.0.0` is present
- vendor/build metadata is present

## 4) Important findings and risks

- `sbg-set10-marking-plugin/src/main/resources/strings_ru.xml` currently has broken text encoding (mojibake).
- `target/` may still contain old `sbg-set10-marking-plugin-1.0.0-SNAPSHOT.jar`.
- Old SNAPSHOT jar does not contain `Implementation-Version` in manifest, which explains cashier warning logs.
- `D:\APISet10\utils\MetainfValidator.jar` fails on current JDK due ClassLoader compatibility; run it with compatible Java runtime (usually Java 8).

## 5) Remaining work

- Fix `strings_ru.xml` and verify Russian texts on cashier UI.
- Remove stale SNAPSHOT plugin jars on cashier hosts and keep only `sbg-set10-marking-plugin-1.0.0.jar`.
- Run `MetainfValidator.jar` with compatible Java and record result.
- Run final real POS E2E against `http://192.168.80.31:8080`:
- sale success
- sale cancel
- refund success
- refund cancel
- resale after refund
- Finalize operational process for KM database updates:
- full/delta ownership
- import schedule
- rollback procedure
- Finalize operations rules:
- history/audit retention
- DB backup/restore
- health monitoring for `/actuator/health`

## 6) Definition of done for production

- Real cashier E2E passes for all critical flows.
- Only release plugin jar is deployed on cashier hosts.
- Russian localization is corrected and verified.
- Import/update operational process is approved.
- Recovery checklist is approved for network/backend outage and retry flow.
