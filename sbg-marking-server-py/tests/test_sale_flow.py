"""Smoke-тесты сценария продажи: resolve + sold-confirm + идемпотентность."""
from __future__ import annotations

from uuid import uuid4


PRODUCT_TYPE = "CIGARETTES"
GTIN = "04670000000017"
ITEM = "ITM-001"


def _seed_marks(client, mark_codes, fifo_ts_epoch_ms=None):
    items = []
    for i, code in enumerate(mark_codes):
        items.append(
            {
                "markCode": code,
                "item": ITEM,
                "gtin": GTIN,
                "productType": PRODUCT_TYPE,
                "valid": True,
                "blocked": False,
                "status": "AVAILABLE",
                "fifoTsEpochMs": (fifo_ts_epoch_ms or 1_700_000_000_000) + i,
            }
        )
    resp = client.post(
        "/api/v1/km/import/full",
        json={"batchId": "test-batch", "items": items},
    )
    assert resp.status_code == 200, resp.get_data(as_text=True)
    return resp.get_json()


def _sale_request(scanned_mark, op_id=None):
    return {
        "operationId": op_id or str(uuid4()),
        "shopId": "SHOP1",
        "posId": "POS1",
        "cashierId": "C1",
        "product": {"barcode": "BC", "item": ITEM, "productType": PRODUCT_TYPE, "gtin": GTIN},
        "scannedMark": scanned_mark,
        "quantity": 1,
    }


def test_health(app_client):
    resp = app_client.get("/health")
    assert resp.status_code == 200
    assert resp.get_json() == {"status": "UP"}


def test_accept_scanned(app_client):
    _seed_marks(app_client, ["MARK-AAA"])
    resp = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-AAA"),
    )
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["result"] == "ACCEPT_SCANNED"
    assert body["appliedMark"] == "MARK-AAA"
    assert body["source"] == "SCANNED"
    assert body["reservationId"]
    assert body["errorCode"] == "NONE"


def test_accept_auto_selected_fifo(app_client):
    # Сканирована посторонняя КМ — backend тихо подменяет из FIFO.
    _seed_marks(app_client, ["MARK-OLD", "MARK-NEW"])
    resp = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-UNKNOWN-NOT-IN-POOL"),
    )
    body = resp.get_json()
    assert body["result"] == "ACCEPT_AUTO_SELECTED"
    assert body["source"] == "AUTO_SELECTED"
    assert body["appliedMark"] == "MARK-OLD"
    assert body["reservationId"]


def test_reuse_of_external_mark_is_blocked(app_client):
    # Повторная попытка продать ту же "левую" КМ должна блокироваться.
    _seed_marks(app_client, ["MARK-OLD", "MARK-NEW"])
    first = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("EXTERNAL-MARK-X"),
    ).get_json()
    assert first["result"] == "ACCEPT_AUTO_SELECTED"

    # Тот же scannedMark, новый operationId — backend должен отказать.
    second = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("EXTERNAL-MARK-X"),
    ).get_json()
    assert second["result"] == "HARD_REJECT"
    assert second["errorCode"] == "INVALID_STATE"


def test_reject_no_candidate(app_client):
    # пул пуст — сканированной нет, FIFO тоже нет.
    resp = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-ABSENT"),
    )
    body = resp.get_json()
    assert body["result"] == "REJECT_NO_CANDIDATE"
    assert body["errorCode"] == "NO_CANDIDATE"
    assert body["appliedMark"] is None


def test_sold_confirm_moves_mark_to_sold(app_client):
    _seed_marks(app_client, ["MARK-001"])
    op_id = str(uuid4())
    r1 = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-001", op_id=op_id),
    ).get_json()
    assert r1["result"] == "ACCEPT_SCANNED"
    reservation_id = r1["reservationId"]

    r2 = app_client.post(
        "/api/v1/marking/sold-confirm",
        json={
            "operationId": str(uuid4()),
            "reservationId": reservation_id,
            "markCode": "MARK-001",
            "receiptId": "R-42",
        },
    ).get_json()
    assert r2["success"] is True
    assert r2["errorCode"] == "NONE"

    # повторный sold-confirm по тому же markCode возвращает already-msg
    r3 = app_client.post(
        "/api/v1/marking/sold-confirm",
        json={
            "operationId": str(uuid4()),
            "markCode": "MARK-001",
        },
    ).get_json()
    assert r3["success"] is True
    assert "already" in r3["message"].lower()


def test_idempotent_resolve(app_client):
    _seed_marks(app_client, ["MARK-IDEMP"])
    op_id = str(uuid4())
    payload = _sale_request("MARK-IDEMP", op_id=op_id)

    r1 = app_client.post("/api/v1/marking/resolve-and-reserve", json=payload).get_json()
    r2 = app_client.post("/api/v1/marking/resolve-and-reserve", json=payload).get_json()

    # Идемпотентность: второй вызов — тот же самый reservationId.
    assert r1["reservationId"] == r2["reservationId"]
    assert r1["result"] == r2["result"] == "ACCEPT_SCANNED"


def test_sale_release_returns_mark_to_pool(app_client):
    _seed_marks(app_client, ["MARK-R1"])
    r1 = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-R1"),
    ).get_json()

    rel = app_client.post(
        "/api/v1/marking/sale-release",
        json={
            "operationId": str(uuid4()),
            "reservationId": r1["reservationId"],
            "markCode": "MARK-R1",
        },
    ).get_json()
    assert rel["success"] is True

    # После release КМ снова доступна — можно зарезервировать повторно.
    r2 = app_client.post(
        "/api/v1/marking/resolve-and-reserve",
        json=_sale_request("MARK-R1"),
    ).get_json()
    assert r2["result"] == "ACCEPT_SCANNED"


def test_validation_fails_without_operation_id(app_client):
    _seed_marks(app_client, ["MARK-V1"])
    payload = _sale_request("MARK-V1")
    payload.pop("operationId")
    resp = app_client.post("/api/v1/marking/resolve-and-reserve", json=payload)
    body = resp.get_json()
    # Pydantic-валидация НЕ падает, т.к. operation_id Optional; валидация идёт в сервисе.
    assert body["result"] == "HARD_REJECT"
    assert body["errorCode"] == "VALIDATION_FAILED"
