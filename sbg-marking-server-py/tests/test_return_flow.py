"""Smoke-тесты сценария возврата: только ACCEPT_SCANNED, без FIFO."""
from __future__ import annotations

from uuid import uuid4


PRODUCT_TYPE = "CIGARETTES"
GTIN = "04670000000017"
ITEM = "ITM-001"


def _seed_sold(client, mark_code: str):
    """Сначала засеваем КМ, потом пропускаем через resolve+sold-confirm
    чтобы получить статус SOLD."""
    client.post(
        "/api/v1/km/import/full",
        json={
            "batchId": "seed",
            "items": [
                {
                    "markCode": mark_code,
                    "item": ITEM,
                    "gtin": GTIN,
                    "productType": PRODUCT_TYPE,
                    "valid": True,
                    "blocked": False,
                    "status": "AVAILABLE",
                    "fifoTsEpochMs": 1_700_000_000_000,
                }
            ],
        },
    )
    sale = client.post(
        "/api/v1/marking/resolve-and-reserve",
        json={
            "operationId": str(uuid4()),
            "product": {"item": ITEM, "productType": PRODUCT_TYPE, "gtin": GTIN},
            "scannedMark": mark_code,
            "quantity": 1,
        },
    ).get_json()
    client.post(
        "/api/v1/marking/sold-confirm",
        json={
            "operationId": str(uuid4()),
            "reservationId": sale["reservationId"],
            "markCode": mark_code,
            "receiptId": "R-99",
        },
    )


def _return_request(mark_code: str, op_id=None):
    return {
        "operationId": op_id or str(uuid4()),
        "product": {"item": ITEM, "productType": PRODUCT_TYPE, "gtin": GTIN},
        "scannedMark": mark_code,
        "saleReceiptId": "R-99",
    }


def test_return_accept_scanned(app_client):
    _seed_sold(app_client, "MARK-RET-1")
    resp = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("MARK-RET-1"),
    )
    body = resp.get_json()
    assert body["success"] is True
    assert body["appliedMark"] == "MARK-RET-1"
    assert body["reservationId"]
    assert body["errorCode"] == "NONE"


def test_return_not_sold_rejected(app_client):
    # КМ в пуле, но ещё не продана.
    app_client.post(
        "/api/v1/km/import/full",
        json={
            "batchId": "seed",
            "items": [
                {
                    "markCode": "MARK-RET-2",
                    "item": ITEM,
                    "gtin": GTIN,
                    "productType": PRODUCT_TYPE,
                    "valid": True,
                    "blocked": False,
                    "status": "AVAILABLE",
                    "fifoTsEpochMs": 1_700_000_000_000,
                }
            ],
        },
    )
    body = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("MARK-RET-2"),
    ).get_json()
    assert body["success"] is False
    assert body["errorCode"] == "INVALID_STATE"


def test_return_unknown_mark_rejected(app_client):
    body = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("NOT-IN-POOL"),
    ).get_json()
    assert body["success"] is False
    assert body["errorCode"] == "INVALID_STATE"


def test_return_confirm_cycle(app_client):
    _seed_sold(app_client, "MARK-RET-3")
    r = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("MARK-RET-3"),
    ).get_json()
    reservation_id = r["reservationId"]

    conf = app_client.post(
        "/api/v1/marking/return-confirm",
        json={
            "operationId": str(uuid4()),
            "reservationId": reservation_id,
            "markCode": "MARK-RET-3",
            "receiptId": "RR-1",
        },
    ).get_json()
    assert conf["success"] is True

    # КМ снова доступна для продажи.
    sale2 = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json={
            "operationId": str(uuid4()),
            "product": {"item": ITEM, "productType": PRODUCT_TYPE, "gtin": GTIN},
            "scannedMark": "MARK-RET-3",
            "quantity": 1,
        },
    ).get_json()
    assert sale2["result"] == "ACCEPT_SCANNED"


def test_return_release_reverts_to_sold(app_client):
    _seed_sold(app_client, "MARK-RET-4")
    r = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("MARK-RET-4"),
    ).get_json()

    rel = app_client.post(
        "/api/v1/marking/return-release",
        json={
            "operationId": str(uuid4()),
            "reservationId": r["reservationId"],
            "markCode": "MARK-RET-4",
        },
    ).get_json()
    assert rel["success"] is True

    # Повторный возврат снова возможен — статус SOLD.
    r2 = app_client.post(
        "/api/v1/marking/return-resolve-and-reserve",
        json=_return_request("MARK-RET-4"),
    ).get_json()
    assert r2["success"] is True
