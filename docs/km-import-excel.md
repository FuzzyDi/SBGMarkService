# KM Import From Excel

Server supports Excel import via multipart endpoints:

- `POST /api/v1/km/import/full/excel`
- `POST /api/v1/km/import/delta/excel`

Request params:

- `file` (required): `.xlsx` file
- `batchId` (optional): import batch identifier

## Recommended columns

Header names are case-insensitive. Common aliases are supported.

Required for valid mark rows:

- `markCode` (or `km`)
- `productType`
- at least one of: `item` or `gtin`

Optional:

- `valid` (`true/false`, `1/0`, `yes/no`)
- `blocked` (`true/false`, `1/0`, `yes/no`)
- `status` (`AVAILABLE`, `RESERVED`, `SOLD`, `RETURN_RESERVED`)
- `fifoTsEpochMs` (epoch ms; epoch seconds are auto-converted)

## Example PowerShell (full import)

```powershell
$file = Get-Item "C:\Temp\km-import.xlsx"

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/km/import/full/excel?batchId=excel-batch-1" `
  -Form @{ file = $file }
```

## Example PowerShell (delta import)

```powershell
$file = Get-Item "C:\Temp\km-import-delta.xlsx"

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/km/import/delta/excel?batchId=excel-delta-1" `
  -Form @{ file = $file }
```
