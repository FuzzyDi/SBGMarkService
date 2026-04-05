# Prompt: sbg-marking-server

You are implementing backend logic for `sbg-marking-server` (Java 17, Spring Boot).

## Goal

Implement robust KM lifecycle for Set Retail 10 integration:

- resolve scanned KM for sale;
- if scanned KM absent in DB, auto-select first suitable FIFO KM;
- reserve selected KM with TTL;
- confirm/release on fiscalization;
- support refund lifecycle;
- keep KM history and reporting endpoints.

## Business rules

- Candidate suitability by product match: `productType` (required) and one of `item/gtin`.
- Eligible for auto-sale: `status=AVAILABLE`, `valid=true`, `blocked=false`, no active reservation.
- FIFO by `fifoTs` ascending.
- Sale states: `AVAILABLE -> RESERVED -> SOLD`.
- Return states: `SOLD -> RETURN_RESERVED -> AVAILABLE`.
- On refund confirm, returned KM becomes available for next sale.
- Idempotency by `operationId` per route.

## Endpoints

- `/api/v1/marking/resolve-and-reserve`
- `/api/v1/marking/return-resolve-and-reserve`
- `/api/v1/marking/sold-confirm`
- `/api/v1/marking/sale-release`
- `/api/v1/marking/return-confirm`
- `/api/v1/marking/return-release`
- `/api/v1/km/import/full`, `/api/v1/km/import/delta`
- `/api/v1/reports/summary`, `/api/v1/reports/history`

## Non-functional

- Thread-safe operations.
- Structured errors with error codes.
- Keep audit history with configurable retention size.
- Keep all plugin/artifact naming with `sbg` prefix.
- Preserve Maven manifest entries in POM files.
