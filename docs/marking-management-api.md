# Marking Management + Validation API

Additional management endpoints:

## Validation

- `POST /api/v1/validation/check`
- `GET /api/v1/validation/policy`
- `PUT /api/v1/validation/policy`

`validation/check` request:

```json
{
  "operationType": "SALE",
  "scannedMark": "KM-1001",
  "product": {
    "item": "ITEM-1",
    "gtin": "GTIN-1",
    "productType": "TOBACCO"
  }
}
```

## Mark Admin

- `GET /api/v1/admin/marks`
  - filters: `productType`, `item`, `gtin`, `status`, `valid`, `blocked`, `limit`, `markCodeLike`
- `POST /api/v1/admin/marks`
- `PUT /api/v1/admin/marks/{markCode}`
- `DELETE /api/v1/admin/marks/{markCode}`
- `GET /api/v1/admin/audit`
  - filters: `limit`, `action`, `success`, `targetMarkCode`, `actorUser`

Upsert payload:

```json
{
  "markCode": "KM-2001",
  "item": "ITEM-2",
  "gtin": "GTIN-2",
  "productType": "TOBACCO",
  "valid": true,
  "blocked": false,
  "status": "AVAILABLE",
  "fifoTsEpochMs": 1712300000000
}
```

## Built-in UI

Server UI at `http://localhost:8080/` now includes:

- import (JSON + Excel)
- sale/return flow controls
- validation check + policy editor
- mark admin upsert/delete/list
- admin audit viewer
- reports/history and CSV download

## Optional role-based access

Enable:

- `SBG_MARKING_AUTH_ENABLED=true`
- `SBG_MARKING_AUTH_ADMIN_TOKEN=<admin-secret>`
- `SBG_MARKING_AUTH_OPERATOR_TOKEN=<operator-secret>`

Headers:

- `X-SBG-Role: ADMIN|OPERATOR`
- `X-SBG-Token: <role token>`
- `X-SBG-User: <optional actor id for audit>`
- `X-Request-Id: <optional request id for audit>`
