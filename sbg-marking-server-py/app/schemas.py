"""Pydantic-модели. JSON — camelCase, как у Java-плагина."""
from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.enums import ErrorCode, MarkSource, ResolveResult


class CamelModel(BaseModel):
    """Base: JSON camelCase, tolerant к неизвестным полям на вход."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
        use_enum_values=True,
    )


# ------- shared -------

class ProductRef(CamelModel):
    barcode: Optional[str] = None
    item: Optional[str] = None
    product_type: Optional[str] = None
    gtin: Optional[str] = None


# ------- sale resolve -------

class ResolveAndReserveRequest(CamelModel):
    operation_id: Optional[str] = None
    shop_id: Optional[str] = None
    pos_id: Optional[str] = None
    cashier_id: Optional[str] = None
    product: Optional[ProductRef] = None
    scanned_mark: Optional[str] = None
    quantity: int = 1


class ResolveAndReserveResponse(CamelModel):
    result: Optional[ResolveResult] = None
    applied_mark: Optional[str] = None
    source: Optional[MarkSource] = None
    reservation_id: Optional[str] = None
    ttl_sec: int = 0
    error_code: ErrorCode = ErrorCode.NONE
    message: Optional[str] = None
    available_marks: Optional[list[str]] = None


# ------- pre-substitute (BarcodeTransformerPlugin) -------

class PreSubstituteRequest(CamelModel):
    scanned_mark: Optional[str] = None
    gtin: Optional[str] = None
    product_type: Optional[str] = None
    receipt_id: Optional[str] = None
    shop_id: Optional[str] = None
    pos_id: Optional[str] = None
    cashier_id: Optional[str] = None


class PreSubstituteResponse(CamelModel):
    # ORIGINAL  — scanned_mark есть в пуле, подмена не нужна (возвращаем его же)
    # SUBSTITUTED — scanned_mark левый, найден FIFO, возвращаем подменённый
    # UNAVAILABLE — нет ни оригинала, ни кандидата (возвращаем scanned_mark как есть,
    #               валидация на следующем шаге отдаст HARD_REJECT)
    source: str = "ORIGINAL"
    scanned_mark: Optional[str] = None
    substituted_mark: Optional[str] = None
    expires_at_epoch_ms: Optional[int] = None
    message: Optional[str] = None


# ------- return resolve -------

class ReturnResolveAndReserveRequest(CamelModel):
    operation_id: Optional[str] = None
    shop_id: Optional[str] = None
    pos_id: Optional[str] = None
    cashier_id: Optional[str] = None
    product: Optional[ProductRef] = None
    scanned_mark: Optional[str] = None
    sale_receipt_id: Optional[str] = None


class ReturnResolveAndReserveResponse(CamelModel):
    success: bool = False
    reservation_id: Optional[str] = None
    applied_mark: Optional[str] = None
    error_code: ErrorCode = ErrorCode.NONE
    message: Optional[str] = None


# ------- mark operations (sold-confirm / sale-release / return-confirm / return-release) -------

class MarkOperationRequest(CamelModel):
    operation_id: Optional[str] = None
    reservation_id: Optional[str] = None
    mark_code: Optional[str] = None
    receipt_id: Optional[str] = None
    receipt_number: Optional[int] = None
    shift_number: Optional[int] = None
    fiscal_doc_id: Optional[int] = None
    fiscal_sign: Optional[int] = None
    reason: Optional[str] = None


class OperationResponse(CamelModel):
    success: bool = False
    error_code: ErrorCode = ErrorCode.NONE
    message: Optional[str] = None


# ------- import (минимум для засева тестовых КМ) -------

class ImportMarkItem(CamelModel):
    mark_code: Optional[str] = None
    item: Optional[str] = None
    gtin: Optional[str] = None
    product_type: Optional[str] = None
    valid: bool = True
    blocked: bool = False
    status: Optional[str] = None
    fifo_ts_epoch_ms: Optional[int] = None


class ImportRequest(CamelModel):
    batch_id: Optional[str] = None
    items: list[ImportMarkItem] = []


class ImportResponse(CamelModel):
    batch_id: Optional[str] = None
    added: int = 0
    updated: int = 0
    quarantined: int = 0
    skipped: int = 0
