# E2E Staging Checklist (Set10 Cashier Flow)

This checklist validates the full KM lifecycle with staging server.

## 1) Start staging

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km
Copy-Item .env.staging.example .env
docker compose -f docker-compose.staging.yml up -d --build
```

Verify service:

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

Expected: `"status":"UP"`.

## 2) Seed mark pool

Import sample KM records:

```powershell
$body = @{
  batchId = "e2e-batch-1"
  items = @(
    @{ markCode="KM-1001"; item="ITEM-1"; gtin="GTIN-1"; productType="TOBACCO"; valid=$true; blocked=$false; status="AVAILABLE"; fifoTsEpochMs=1000 },
    @{ markCode="KM-1002"; item="ITEM-1"; gtin="GTIN-1"; productType="TOBACCO"; valid=$true; blocked=$false; status="AVAILABLE"; fifoTsEpochMs=2000 }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/km/import/full -ContentType "application/json" -Body $body
```

Expected: `added=2`.

Optional FIFO diagnostics:

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/km/debug/fifo-by-product?productType=TOBACCO&item=ITEM-1&gtin=GTIN-1&limit=10"
```

Expected:
- `total` shows all marks of this product in FIFO order
- `firstSelectableMark` shows mark that will be auto-selected
- `candidates[*].reason` explains why mark is selectable or skipped

Optional Excel import:

```powershell
$file = Get-Item "C:\Temp\km-import.xlsx"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/km/import/full/excel?batchId=e2e-excel-1" -Form @{ file = $file }
```

## 3) Sale flow (cashier)

1. Cashier scans product barcode of marked item.
2. Cashier scans KM that does not exist in pool (or invalid for product).
3. Plugin asks server `resolve-and-reserve`.
4. Server auto-selects first suitable FIFO KM.
5. Sale is fiscalized -> plugin sends `sold-confirm`.

Verify summary:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/reports/summary
```

Expected:
- `soldMarks` increases
- `activeReservations` goes down after confirm

## 4) Sale cancel flow

1. Perform reserve as sale.
2. Cancel receipt on POS.
3. Plugin sends `sale-release`.

Expected:
- KM returns to `AVAILABLE`
- reservation is removed

## 5) Refund flow

1. Cashier starts refund for previously sold KM.
2. Plugin calls `return-resolve-and-reserve`.
3. Refund fiscalized -> plugin sends `return-confirm`.

Expected:
- KM status returns to `AVAILABLE`
- `fifoTs` refreshed
- mark can be sold again

## 6) Refund cancel flow

1. Start refund and reserve mark.
2. Cancel refund receipt.
3. Plugin sends `return-release`.

Expected:
- KM remains `SOLD`

## 7) Idempotency checks

Repeat the same request with same `operationId` for:
- `resolve-and-reserve`
- `sold-confirm`
- `return-confirm`

Expected:
- same response payload
- no duplicate state transitions

## 8) Offline/retry checks

1. Temporarily stop backend network from POS/plugin side.
2. Fiscalized callback fails and `Feedback` is saved.
3. Restore network.
4. Retry send path executes (`onRepeatSend`).

Expected:
- operation eventually reaches backend
- local reservation map is cleaned after successful retry

## 9) History and reports

Check:

```powershell
Invoke-RestMethod "http://localhost:8080/api/v1/reports/history?limit=100"
Invoke-RestMethod "http://localhost:8080/api/v1/reports/history?eventType=SALE_RESOLVE&cashierId=cashier-api&success=true&limit=50"
Invoke-WebRequest "http://localhost:8080/api/v1/reports/history.csv?eventType=SALE_RESOLVE&success=true&limit=50" | Select-Object -ExpandProperty Content
Invoke-RestMethod http://localhost:8080/api/v1/reports/summary
```

Expected:
- history includes `SALE_RESOLVE`, `SALE_CONFIRM`, `RETURN_RESOLVE`, `RETURN_CONFIRM`
- filtered history returns only requested subset
- CSV export includes header and filtered events
- counters in summary match performed operations
