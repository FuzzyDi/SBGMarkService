"""Flask endpoints. Пути и JSON-контракт 1:1 с Java MarkingController."""
from __future__ import annotations

import logging
from typing import Type, TypeVar

from flask import Blueprint, Flask, jsonify, request
from pydantic import ValidationError

from app.config import Settings
from app.db import session_scope
from app.enums import ErrorCode
from app.schemas import (
    CamelModel,
    ImportRequest,
    ImportResponse,
    MarkOperationRequest,
    OperationResponse,
    PreSubstituteRequest,
    PreSubstituteResponse,
    ResolveAndReserveRequest,
    ResolveAndReserveResponse,
    ReturnResolveAndReserveRequest,
    ReturnResolveAndReserveResponse,
)
from app.service import MarkingService

log = logging.getLogger(__name__)

T = TypeVar("T", bound=CamelModel)

health_bp = Blueprint("health", __name__)
marking_bp = Blueprint("marking", __name__, url_prefix="/api/v1/marking")
import_bp = Blueprint("km_import", __name__, url_prefix="/api/v1/km/import")


# ============================================================================
# health
# ============================================================================
@health_bp.get("/health")
def health():
    return jsonify({"status": "UP"})


# ============================================================================
# marking
# ============================================================================
@marking_bp.post("/pre-substitute")
def pre_substitute():
    req = _parse(PreSubstituteRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp = svc.pre_substitute(req)
    return _json(resp)


@marking_bp.post("/resolve-and-reserve")
def resolve_and_reserve():
    req = _parse(ResolveAndReserveRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp = svc.resolve_and_reserve(req)
    return _json(resp)


@marking_bp.post("/return-resolve-and-reserve")
def return_resolve_and_reserve():
    req = _parse(ReturnResolveAndReserveRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp = svc.return_resolve_and_reserve(req)
    return _json(resp)


@marking_bp.post("/sold-confirm")
def sold_confirm():
    return _mark_op("sold_confirm")


@marking_bp.post("/sale-release")
def sale_release():
    return _mark_op("sale_release")


@marking_bp.post("/return-confirm")
def return_confirm():
    return _mark_op("return_confirm")


@marking_bp.post("/return-release")
def return_release():
    return _mark_op("return_release")


# ============================================================================
# import
# ============================================================================
@import_bp.post("/full")
def import_full():
    req = _parse(ImportRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp = svc.import_full(req)
    return _json(resp)


@import_bp.post("/delta")
def import_delta():
    req = _parse(ImportRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp = svc.import_delta(req)
    return _json(resp)


# ============================================================================
# helpers
# ============================================================================
def _mark_op(method_name: str):
    req = _parse(MarkOperationRequest)
    if isinstance(req, tuple):
        return req
    with session_scope() as session:
        svc = MarkingService(session, _ttl_sec())
        resp: OperationResponse = getattr(svc, method_name)(req)
    return _json(resp)


def _parse(schema: Type[T]):
    """Распарсить JSON в Pydantic-модель. Возвращает либо объект, либо (json, code)."""
    payload = request.get_json(silent=True)
    if payload is None:
        return (
            jsonify(
                {
                    "success": False,
                    "errorCode": ErrorCode.VALIDATION_FAILED.value,
                    "message": "Request body must be a valid JSON object.",
                }
            ),
            400,
        )
    try:
        return schema.model_validate(payload)
    except ValidationError as ex:
        log.info("validation failed for %s: %s", schema.__name__, ex.errors())
        return (
            jsonify(
                {
                    "success": False,
                    "errorCode": ErrorCode.VALIDATION_FAILED.value,
                    "message": "Request body validation failed.",
                    "details": ex.errors(include_url=False),
                }
            ),
            400,
        )


def _json(resp: CamelModel):
    return jsonify(resp.model_dump(by_alias=True))


def _ttl_sec() -> int:
    from flask import current_app

    settings: Settings = current_app.config["SETTINGS"]
    return settings.reservation_ttl_sec


# ============================================================================
# registration
# ============================================================================
def register_blueprints(app: Flask) -> None:
    app.register_blueprint(health_bp)
    app.register_blueprint(marking_bp)
    app.register_blueprint(import_bp)

    @app.errorhandler(500)
    def _on_500(err):  # pragma: no cover
        log.exception("internal error: %s", err)
        return (
            jsonify(
                {
                    "success": False,
                    "errorCode": ErrorCode.INTERNAL_ERROR.value,
                    "message": "Internal server error.",
                }
            ),
            500,
        )
