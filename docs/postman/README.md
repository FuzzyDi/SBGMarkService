# Postman API Collection

This folder contains ready-to-run Postman artifacts for manual E2E checks.

## Files

- `SBG-Marking-Auto-KM.postman_collection.json`
- `SBG-Marking-Local.postman_environment.json`

## Quick start

1. Import both files into Postman.
2. Select environment `SBG Marking Local`.
3. Start server (`mvn spring-boot:run`) or staging stack (`scripts/staging-up.ps1`).
4. Run requests in this order:
   - `Health / Actuator`
   - `Import / Full Import Seed`
   - `Sale Success Flow / Resolve and Reserve (Auto FIFO)`
   - `Sale Success Flow / Sold Confirm`
   - `Return Success Flow / Return Resolve and Reserve`
   - `Return Success Flow / Return Confirm`
   - `Reports / Summary`
   - `Reports / History`
   - `Reports / GET /api/v1/km/debug/fifo-by-product`

## Notes

- Collection scripts automatically store `reservationId` and `appliedMark` into collection variables.
- `Sale Cancel Flow` and `Return Cancel Flow` are optional and demonstrate `sale-release` and `return-release`.
- To test idempotency, repeat the same request without changing `operationId` variables.
- FIFO debug request shows why each mark is selected or skipped (`reason`) for the current product filter.
