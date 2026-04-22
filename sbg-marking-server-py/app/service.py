"""MarkingService — core бизнес-логика. Портирован с Java MarkingService.java.

Покрывает:
- resolve-and-reserve (продажа: ACCEPT_SCANNED / REJECT_NO_CANDIDATE / HARD_REJECT)
  Автоподмены (ACCEPT_AUTO_SELECTED) больше нет: если сканированной КМ нет в пуле,
  backend возвращает REJECT_NO_CANDIDATE + availableMarks (FIFO), касса просит
  кассира отсканировать предложенную марку.
- return-resolve-and-reserve (возврат: только ACCEPT_SCANNED или HARD_REJECT)
- sold-confirm / sale-release / return-confirm / return-release
- Idempotency by (route, operationId)
- Минимальный import для засева тестовых КМ
"""
from __future__ import annotations

import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional, TypeVar

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.enums import ErrorCode, MarkSource, MarkStatus, ReservationType, ResolveResult
from app.models import (
    ExternalScanHistory,
    IdempotencyEntry,
    Mark,
    Reservation,
    SubstitutionMapping,
)
from app.schemas import (
    CamelModel,
    ImportRequest,
    ImportResponse,
    MarkOperationRequest,
    OperationResponse,
    PreSubstituteRequest,
    PreSubstituteResponse,
    ProductRef,
    ResolveAndReserveRequest,
    ResolveAndReserveResponse,
    ReturnResolveAndReserveRequest,
    ReturnResolveAndReserveResponse,
)

log = logging.getLogger(__name__)

TResponse = TypeVar("TResponse", bound=CamelModel)

# Сколько КМ перечислить в availableMarks при REJECT_NO_CANDIDATE
MAX_AVAILABLE_MARKS = 10


class MarkingService:
    def __init__(self, session: Session, reservation_ttl_sec: int = 900) -> None:
        self.session = session
        self.reservation_ttl = timedelta(seconds=max(60, reservation_ttl_sec))

    # ========================================================================
    # sale resolve
    # ========================================================================
    def resolve_and_reserve(self, req: ResolveAndReserveRequest) -> ResolveAndReserveResponse:
        self._cleanup_expired_reservations()
        route = "resolve"
        op_id = req.operation_id

        cached = self._find_idempotent(route, op_id, ResolveAndReserveResponse)
        if cached is not None:
            return cached

        err = self._validate_resolve_request(req)
        if err is not None:
            self._remember_idempotent(route, op_id, err)
            return err

        # 1. КМ сканированная и существует в пуле?
        scanned: Optional[Mark] = self.session.get(Mark, req.scanned_mark)
        if scanned is not None:
            if not self._matches_product(scanned, req.product):
                resp = self._hard_reject(
                    ErrorCode.INVALID_MARK_FOR_PRODUCT,
                    "Scanned mark belongs to another product.",
                )
                self._remember_idempotent(route, op_id, resp)
                return resp
            if not self._eligible_for_sale(scanned):
                resp = self._hard_reject(
                    ErrorCode.INVALID_STATE,
                    "Scanned mark is not available for sale.",
                )
                self._remember_idempotent(route, op_id, resp)
                return resp
            resp = self._reserve_for_sale(
                scanned,
                op_id,
                MarkSource.SCANNED,
                ResolveResult.ACCEPT_SCANNED,
                "Scanned mark accepted.",
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        # 2. Сканированной КМ нет в пуле. В архитектуре "BarcodeTransformer +
        # ExciseValidation" подмена делается ДО этого шага на уровне трансформера
        # штрихкодов, поэтому сюда мы попадаем только если подмены не было или
        # она не удалась. Возвращаем REJECT_NO_CANDIDATE + аудит.
        self._record_external_scan(req, None, 0)
        resp = ResolveAndReserveResponse(
            result=ResolveResult.REJECT_NO_CANDIDATE,
            error_code=ErrorCode.NO_CANDIDATE,
            message="Scanned mark was not found in pool.",
            available_marks=None,
        )
        self._remember_idempotent(route, op_id, resp)
        return resp

    # ========================================================================
    # return resolve
    # ========================================================================
    def return_resolve_and_reserve(
        self, req: ReturnResolveAndReserveRequest
    ) -> ReturnResolveAndReserveResponse:
        self._cleanup_expired_reservations()
        route = "return-resolve"
        op_id = req.operation_id

        cached = self._find_idempotent(route, op_id, ReturnResolveAndReserveResponse)
        if cached is not None:
            return cached

        err = self._validate_return_resolve_request(req)
        if err is not None:
            self._remember_idempotent(route, op_id, err)
            return err

        mark: Optional[Mark] = self.session.get(Mark, req.scanned_mark)
        if mark is None:
            resp = self._fail_return(
                ErrorCode.INVALID_STATE, "Mark was not found in local mark pool."
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        if not self._matches_product(mark, req.product):
            resp = self._fail_return(
                ErrorCode.INVALID_MARK_FOR_PRODUCT,
                "Scanned mark belongs to another product.",
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        if mark.status != MarkStatus.SOLD.value:
            err_code = (
                ErrorCode.ALREADY_RETURNED
                if mark.status == MarkStatus.AVAILABLE.value and mark.last_return_receipt_id
                else ErrorCode.INVALID_STATE
            )
            resp = self._fail_return(
                err_code, "Mark is not in SOLD state and cannot be returned."
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        reservation = self._create_reservation(mark.mark_code, op_id, ReservationType.RETURN)
        mark.status = MarkStatus.RETURN_RESERVED.value
        mark.active_reservation_id = reservation.id
        self.session.add(mark)

        resp = ReturnResolveAndReserveResponse(
            success=True,
            applied_mark=mark.mark_code,
            reservation_id=reservation.id,
            message="Return reservation created.",
        )
        self._remember_idempotent(route, op_id, resp)
        return resp

    # ========================================================================
    # pre-substitute (BarcodeTransformerPlugin)
    # ========================================================================
    def pre_substitute(self, req: PreSubstituteRequest) -> PreSubstituteResponse:
        """Lookup или подмена КМ до этапа валидации.

        Без резервации — только назначение mapping'а. Резервация появится позже
        в resolve_and_reserve(substituted_mark).
        """
        self._cleanup_expired_mappings()

        if not req.scanned_mark:
            return PreSubstituteResponse(
                source="UNAVAILABLE",
                scanned_mark=req.scanned_mark,
                message="scannedMark is required.",
            )

        # 1. Проверяем не ставили ли уже mapping для этой КМ (идемпотентность).
        existing = self._find_active_mapping(req.scanned_mark)
        if existing is not None:
            return PreSubstituteResponse(
                source="SUBSTITUTED",
                scanned_mark=req.scanned_mark,
                substituted_mark=existing.substituted_mark,
                expires_at_epoch_ms=_to_epoch_ms(existing.expires_at),
                message="Reused existing substitution mapping.",
            )

        # 2. scanned_mark уже есть в пуле → подмена не нужна.
        original: Optional[Mark] = self.session.get(Mark, req.scanned_mark)
        if original is not None and self._eligible_for_sale(original):
            if self._scanned_matches_hints(original, req):
                return PreSubstituteResponse(
                    source="ORIGINAL",
                    scanned_mark=req.scanned_mark,
                    substituted_mark=req.scanned_mark,
                    message="Scanned mark is available in pool; no substitution needed.",
                )

        # 3. Подбираем FIFO кандидата по gtin/product_type.
        product = ProductRef(
            gtin=req.gtin,
            product_type=req.product_type,
        )
        candidate = self._find_fifo_candidate_excluding_mapped(product)
        if candidate is None:
            return PreSubstituteResponse(
                source="UNAVAILABLE",
                scanned_mark=req.scanned_mark,
                substituted_mark=None,
                message="No suitable mark available in pool for substitution.",
            )

        # 4. Создаём mapping.
        expires_at = _now() + self.reservation_ttl
        mapping = SubstitutionMapping(
            scanned_mark=req.scanned_mark,
            substituted_mark=candidate.mark_code,
            receipt_id=req.receipt_id,
            shop_id=req.shop_id,
            pos_id=req.pos_id,
            cashier_id=req.cashier_id,
            created_at=_now(),
            expires_at=expires_at,
            consumed=False,
        )
        self.session.add(mapping)

        return PreSubstituteResponse(
            source="SUBSTITUTED",
            scanned_mark=req.scanned_mark,
            substituted_mark=candidate.mark_code,
            expires_at_epoch_ms=_to_epoch_ms(expires_at),
            message="Substitution mapping created.",
        )

    # ========================================================================
    # sold-confirm / sale-release / return-confirm / return-release
    # ========================================================================
    def sold_confirm(self, req: MarkOperationRequest) -> OperationResponse:
        return self._run_mark_op(
            req,
            route="sold-confirm",
            expected_type=ReservationType.SALE,
            on_active=lambda mark, r: self._commit_sale(mark, r),
            already_check=lambda m: m.status == MarkStatus.SOLD.value,
            success_msg="Sale confirmed.",
            already_msg="Sale was already confirmed.",
            no_active_msg="No active sale reservation found for mark.",
        )

    def sale_release(self, req: MarkOperationRequest) -> OperationResponse:
        return self._run_mark_op(
            req,
            route="sale-release",
            expected_type=ReservationType.SALE,
            on_active=lambda mark, r: self._release_sale(mark),
            already_check=lambda m: m.status == MarkStatus.AVAILABLE.value,
            success_msg="Sale reservation released.",
            already_msg="Sale reservation was already released.",
            no_active_msg="No active sale reservation found for release.",
        )

    def return_confirm(self, req: MarkOperationRequest) -> OperationResponse:
        return self._run_mark_op(
            req,
            route="return-confirm",
            expected_type=ReservationType.RETURN,
            on_active=lambda mark, r: self._commit_return(mark, r),
            already_check=lambda m: m.status == MarkStatus.AVAILABLE.value,
            success_msg="Return confirmed. Mark is available for sale again.",
            already_msg="Return was already confirmed.",
            no_active_msg="No active return reservation found for mark.",
        )

    def return_release(self, req: MarkOperationRequest) -> OperationResponse:
        return self._run_mark_op(
            req,
            route="return-release",
            expected_type=ReservationType.RETURN,
            on_active=lambda mark, r: self._release_return(mark),
            already_check=lambda m: m.status == MarkStatus.SOLD.value,
            success_msg="Return reservation released.",
            already_msg="Return reservation was already released.",
            no_active_msg="No active return reservation found for release.",
        )

    # ========================================================================
    # import (минимум)
    # ========================================================================
    def import_full(self, req: ImportRequest) -> ImportResponse:
        return self._import(req, full_mode=True)

    def import_delta(self, req: ImportRequest) -> ImportResponse:
        return self._import(req, full_mode=False)

    # ========================================================================
    # internals
    # ========================================================================
    def _run_mark_op(
        self,
        req: MarkOperationRequest,
        *,
        route: str,
        expected_type: ReservationType,
        on_active,
        already_check,
        success_msg: str,
        already_msg: str,
        no_active_msg: str,
    ) -> OperationResponse:
        self._cleanup_expired_reservations()
        op_id = req.operation_id

        cached = self._find_idempotent(route, op_id, OperationResponse)
        if cached is not None:
            return cached

        err = self._validate_mark_op_request(req)
        if err is not None:
            self._remember_idempotent(route, op_id, err)
            return err

        reservation = self._find_reservation(req, expected_type)
        if reservation is not None:
            mark: Optional[Mark] = self.session.get(Mark, reservation.mark_code)
            if mark is None:
                resp = OperationResponse(
                    success=False,
                    error_code=ErrorCode.INVALID_STATE,
                    message="Reservation mark was not found.",
                )
                self._remember_idempotent(route, op_id, resp)
                return resp
            on_active(mark, req)
            self._remove_reservation(reservation.id)
            log.info(
                "[%s] ok | mark=%s | rid=%s | op=%s | receipt=%s | reason=%s",
                route,
                reservation.mark_code,
                reservation.id,
                op_id,
                req.receipt_id or req.receipt_number,
                req.reason or "-",
            )
            resp = OperationResponse(
                success=True, error_code=ErrorCode.NONE, message=success_msg,
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        mark: Optional[Mark] = (
            self.session.get(Mark, req.mark_code) if req.mark_code else None
        )
        if mark is not None and already_check(mark):
            log.info(
                "[%s] already | mark=%s | op=%s | reason=%s",
                route,
                req.mark_code,
                op_id,
                req.reason or "-",
            )
            resp = OperationResponse(
                success=True, error_code=ErrorCode.NONE, message=already_msg
            )
            self._remember_idempotent(route, op_id, resp)
            return resp

        log.info(
            "[%s] no-active | mark=%s | rid=%s | op=%s | reason=%s",
            route,
            req.mark_code,
            req.reservation_id,
            op_id,
            req.reason or "-",
        )
        resp = OperationResponse(
            success=False,
            error_code=ErrorCode.INVALID_STATE,
            message=no_active_msg,
        )
        self._remember_idempotent(route, op_id, resp)
        return resp

    # --- state transitions ---
    def _commit_sale(self, mark: Mark, req: MarkOperationRequest) -> None:
        mark.status = MarkStatus.SOLD.value
        mark.active_reservation_id = None
        mark.last_sale_receipt_id = req.receipt_id or (
            str(req.receipt_number) if req.receipt_number is not None else None
        )
        self.session.add(mark)
        # Отмечаем substitution_mapping как consumed — чтобы он не мешал
        # последующей FIFO-выборке и одновременно сохранился в истории.
        self._mark_mappings_consumed(mark.mark_code)

    def _mark_mappings_consumed(self, substituted_mark: str) -> None:
        stmt = select(SubstitutionMapping).where(
            SubstitutionMapping.substituted_mark == substituted_mark,
            SubstitutionMapping.consumed.is_(False),
        )
        for m in self.session.scalars(stmt):
            m.consumed = True
            self.session.add(m)

    def _release_sale(self, mark: Mark) -> None:
        mark.status = MarkStatus.AVAILABLE.value
        mark.active_reservation_id = None
        self.session.add(mark)

    def _commit_return(self, mark: Mark, req: MarkOperationRequest) -> None:
        mark.status = MarkStatus.AVAILABLE.value
        mark.active_reservation_id = None
        mark.last_return_receipt_id = req.receipt_id or (
            str(req.receipt_number) if req.receipt_number is not None else None
        )
        mark.fifo_ts = _now()
        self.session.add(mark)

    def _release_return(self, mark: Mark) -> None:
        mark.status = MarkStatus.SOLD.value
        mark.active_reservation_id = None
        self.session.add(mark)

    # --- reservation helpers ---
    def _create_reservation(
        self, mark_code: str, op_id: Optional[str], res_type: ReservationType
    ) -> Reservation:
        reservation = Reservation(
            id=str(uuid.uuid4()),
            operation_id=op_id or "",
            mark_code=mark_code,
            type=res_type.value,
            expires_at=_now() + self.reservation_ttl,
        )
        self.session.add(reservation)
        self.session.flush()
        return reservation

    def _find_reservation(
        self, req: MarkOperationRequest, expected_type: ReservationType
    ) -> Optional[Reservation]:
        if req.reservation_id:
            r: Optional[Reservation] = self.session.get(Reservation, req.reservation_id)
            if r is not None:
                return r if r.type == expected_type.value else None

        if not req.mark_code:
            return None

        stmt = (
            select(Reservation)
            .where(Reservation.mark_code == req.mark_code)
            .where(Reservation.type == expected_type.value)
        )
        return self.session.scalars(stmt).first()

    def _remove_reservation(self, reservation_id: str) -> None:
        r = self.session.get(Reservation, reservation_id)
        if r is not None:
            self.session.delete(r)

    def _cleanup_expired_reservations(self) -> None:
        now = _now()
        expired: list[Reservation] = list(
            self.session.scalars(select(Reservation).where(Reservation.expires_at <= now))
        )
        for r in expired:
            mark: Optional[Mark] = self.session.get(Mark, r.mark_code)
            if mark is not None and mark.active_reservation_id == r.id:
                mark.status = (
                    MarkStatus.AVAILABLE.value
                    if r.type == ReservationType.SALE.value
                    else MarkStatus.SOLD.value
                )
                mark.active_reservation_id = None
                self.session.add(mark)
            self.session.delete(r)

    # --- substitution mappings ---
    def _find_active_mapping(
        self, scanned_mark: str
    ) -> Optional[SubstitutionMapping]:
        now = _now()
        stmt = (
            select(SubstitutionMapping)
            .where(SubstitutionMapping.scanned_mark == scanned_mark)
            .where(SubstitutionMapping.consumed.is_(False))
            .where(SubstitutionMapping.expires_at > now)
            .limit(1)
        )
        return self.session.scalars(stmt).first()

    def _cleanup_expired_mappings(self) -> None:
        now = _now()
        stmt = select(SubstitutionMapping).where(
            SubstitutionMapping.expires_at <= now,
            SubstitutionMapping.consumed.is_(False),
        )
        for m in list(self.session.scalars(stmt)):
            self.session.delete(m)

    def _active_mapped_marks(self) -> set[str]:
        now = _now()
        stmt = select(SubstitutionMapping.substituted_mark).where(
            SubstitutionMapping.consumed.is_(False),
            SubstitutionMapping.expires_at > now,
        )
        return set(self.session.scalars(stmt))

    @staticmethod
    def _scanned_matches_hints(mark: Mark, req: PreSubstituteRequest) -> bool:
        if req.gtin and mark.gtin and req.gtin != mark.gtin:
            return False
        if (
            req.product_type
            and mark.product_type
            and req.product_type.lower() != mark.product_type.lower()
        ):
            return False
        return True

    def _find_fifo_candidate_excluding_mapped(
        self, product: Optional[ProductRef]
    ) -> Optional[Mark]:
        """FIFO кандидат, но исключая те что уже "зарезервированы" в
        substitution_mapping (иначе два разных X могли бы подменяться на одну Y).
        """
        if product is None or (not product.gtin and not product.product_type):
            return None
        excluded = self._active_mapped_marks()

        stmt = select(Mark).where(
            Mark.status == MarkStatus.AVAILABLE.value,
            Mark.valid.is_(True),
            Mark.blocked.is_(False),
            Mark.active_reservation_id.is_(None),
        )
        if product.product_type:
            stmt = stmt.where(Mark.product_type.ilike(product.product_type))
        stmt = stmt.order_by(Mark.fifo_ts.asc(), Mark.mark_code.asc())

        for mark in self.session.scalars(stmt):
            if mark.mark_code in excluded:
                continue
            if product.gtin and mark.gtin and product.gtin != mark.gtin:
                continue
            return mark
        return None

    # --- FIFO / matching ---
    def _find_fifo_candidate(self, product: Optional[ProductRef]) -> Optional[Mark]:
        if product is None or not product.product_type:
            return None

        stmt = (
            select(Mark)
            .where(Mark.product_type.ilike(product.product_type))
            .where(Mark.status == MarkStatus.AVAILABLE.value)
            .where(Mark.valid.is_(True))
            .where(Mark.blocked.is_(False))
            .where(Mark.active_reservation_id.is_(None))
            .order_by(Mark.fifo_ts.asc(), Mark.mark_code.asc())
        )
        for mark in self.session.scalars(stmt):
            if self._matches_product(mark, product):
                return mark
        return None

    def _list_available_marks_for_product(
        self, product: Optional[ProductRef], limit: int = MAX_AVAILABLE_MARKS
    ) -> list[str]:
        if product is None or not product.product_type:
            return []
        stmt = (
            select(Mark)
            .where(Mark.product_type.ilike(product.product_type))
            .where(Mark.status == MarkStatus.AVAILABLE.value)
            .where(Mark.valid.is_(True))
            .where(Mark.blocked.is_(False))
            .where(Mark.active_reservation_id.is_(None))
            .order_by(Mark.fifo_ts.asc(), Mark.mark_code.asc())
        )
        result: list[str] = []
        for mark in self.session.scalars(stmt):
            if not self._matches_product(mark, product):
                continue
            result.append(mark.mark_code)
            if len(result) >= limit:
                break
        return result

    @staticmethod
    def _matches_product(mark: Mark, product: Optional[ProductRef]) -> bool:
        if product is None or not product.product_type:
            return False
        if (mark.product_type or "").lower() != product.product_type.lower():
            return False
        if product.gtin and mark.gtin and product.gtin != mark.gtin:
            return False
        if product.item and mark.item and product.item != mark.item:
            return False
        return True

    @staticmethod
    def _eligible_for_sale(mark: Mark) -> bool:
        return (
            mark.status == MarkStatus.AVAILABLE.value
            and mark.valid
            and not mark.blocked
            and mark.active_reservation_id is None
        )

    # --- sale reserve ---
    def _reserve_for_sale(
        self,
        mark: Mark,
        op_id: Optional[str],
        source: MarkSource,
        result: ResolveResult,
        message: str,
    ) -> ResolveAndReserveResponse:
        reservation = self._create_reservation(mark.mark_code, op_id, ReservationType.SALE)
        mark.status = MarkStatus.RESERVED.value
        mark.active_reservation_id = reservation.id
        self.session.add(mark)

        return ResolveAndReserveResponse(
            result=result,
            applied_mark=mark.mark_code,
            source=source,
            reservation_id=reservation.id,
            ttl_sec=int(self.reservation_ttl.total_seconds()),
            message=message,
        )

    # --- validation ---
    @staticmethod
    def _validate_resolve_request(
        req: ResolveAndReserveRequest,
    ) -> Optional[ResolveAndReserveResponse]:
        if not req.operation_id:
            return MarkingService._hard_reject(
                ErrorCode.VALIDATION_FAILED, "operationId is required."
            )
        if req.quantity != 1:
            return MarkingService._hard_reject(
                ErrorCode.VALIDATION_FAILED, "Only quantity=1 is supported for serial marks."
            )
        if (
            req.product is None
            or not req.product.product_type
            or (not req.product.item and not req.product.gtin)
        ):
            return MarkingService._hard_reject(
                ErrorCode.VALIDATION_FAILED,
                "productType and one of item/gtin are required.",
            )
        if not req.scanned_mark:
            return MarkingService._hard_reject(
                ErrorCode.VALIDATION_FAILED, "scannedMark is required."
            )
        return None

    @staticmethod
    def _validate_return_resolve_request(
        req: ReturnResolveAndReserveRequest,
    ) -> Optional[ReturnResolveAndReserveResponse]:
        if not req.operation_id:
            return MarkingService._fail_return(
                ErrorCode.VALIDATION_FAILED, "operationId is required."
            )
        if (
            req.product is None
            or not req.product.product_type
            or (not req.product.item and not req.product.gtin)
        ):
            return MarkingService._fail_return(
                ErrorCode.VALIDATION_FAILED,
                "productType and one of item/gtin are required.",
            )
        if not req.scanned_mark:
            return MarkingService._fail_return(
                ErrorCode.VALIDATION_FAILED, "scannedMark is required."
            )
        return None

    @staticmethod
    def _validate_mark_op_request(req: MarkOperationRequest) -> Optional[OperationResponse]:
        if not req.operation_id:
            return OperationResponse(
                success=False,
                error_code=ErrorCode.VALIDATION_FAILED,
                message="operationId is required.",
            )
        if not req.reservation_id and not req.mark_code:
            return OperationResponse(
                success=False,
                error_code=ErrorCode.VALIDATION_FAILED,
                message="reservationId or markCode is required.",
            )
        return None

    @staticmethod
    def _hard_reject(error_code: ErrorCode, message: str) -> ResolveAndReserveResponse:
        return ResolveAndReserveResponse(
            result=ResolveResult.HARD_REJECT,
            error_code=error_code,
            message=message,
        )

    @staticmethod
    def _fail_return(error_code: ErrorCode, message: str) -> ReturnResolveAndReserveResponse:
        return ReturnResolveAndReserveResponse(
            success=False, error_code=error_code, message=message
        )

    # --- external scan audit ---
    def _record_external_scan(
        self,
        req: ResolveAndReserveRequest,
        suggested: Optional[str],
        available_count: int,
    ) -> None:
        try:
            product = req.product
            entry = ExternalScanHistory(
                scanned_mark=req.scanned_mark or "",
                product_type=product.product_type if product else None,
                gtin=product.gtin if product else None,
                item=product.item if product else None,
                operation_id=req.operation_id,
                receipt_id=None,
                shop_id=req.shop_id,
                pos_id=req.pos_id,
                cashier_id=req.cashier_id,
                suggested_mark=suggested,
                available_count=available_count,
                created_at=_now(),
            )
            self.session.add(entry)
        except Exception as ex:  # pragma: no cover - не должно ломать основной поток
            log.warning("external_scan_history insert failed: %s", ex)

    # --- idempotency ---
    def _find_idempotent(
        self, route: str, op_id: Optional[str], response_type: type[TResponse]
    ) -> Optional[TResponse]:
        if not op_id:
            return None
        stmt = (
            select(IdempotencyEntry)
            .where(IdempotencyEntry.route == route)
            .where(IdempotencyEntry.operation_id == op_id)
        )
        entry = self.session.scalars(stmt).first()
        if entry is None:
            return None
        if entry.response_type != response_type.__name__:
            return None
        try:
            return response_type.model_validate_json(entry.response_payload)
        except Exception as ex:  # pragma: no cover - поврежденный JSON
            log.warning("idempotency payload parse failed route=%s op=%s: %s", route, op_id, ex)
            return None

    def _remember_idempotent(
        self, route: str, op_id: Optional[str], value: CamelModel
    ) -> None:
        if not op_id:
            return
        payload = value.model_dump_json(by_alias=True)
        now = _now()
        stmt = (
            select(IdempotencyEntry)
            .where(IdempotencyEntry.route == route)
            .where(IdempotencyEntry.operation_id == op_id)
        )
        entry = self.session.scalars(stmt).first()
        if entry is None:
            entry = IdempotencyEntry(
                route=route,
                operation_id=op_id,
                response_type=type(value).__name__,
                response_payload=payload,
                created_at=now,
                updated_at=now,
            )
            self.session.add(entry)
        else:
            entry.response_type = type(value).__name__
            entry.response_payload = payload
            entry.updated_at = now

    # --- import ---
    def _import(self, req: ImportRequest, *, full_mode: bool) -> ImportResponse:
        self._cleanup_expired_reservations()
        response = ImportResponse(batch_id=req.batch_id)

        if not req.items:
            return response

        incoming: dict[str, object] = {}
        for item in req.items:
            if (
                not item.mark_code
                or not item.product_type
                or (not item.item and not item.gtin)
            ):
                response.quarantined += 1
                continue
            incoming[item.mark_code] = item
            existing: Optional[Mark] = self.session.get(Mark, item.mark_code)
            if existing is None:
                self.session.add(
                    Mark(
                        mark_code=item.mark_code,
                        item=item.item,
                        gtin=item.gtin,
                        product_type=item.product_type,
                        valid=item.valid,
                        blocked=item.blocked,
                        status=(item.status or MarkStatus.AVAILABLE.value),
                        fifo_ts=_from_epoch_ms(item.fifo_ts_epoch_ms) or _now(),
                        active_reservation_id=None,
                        last_sale_receipt_id=None,
                        last_return_receipt_id=None,
                    )
                )
                response.added += 1
            else:
                existing.item = item.item
                existing.gtin = item.gtin
                existing.product_type = item.product_type
                existing.valid = item.valid
                existing.blocked = item.blocked
                if existing.status == MarkStatus.AVAILABLE.value and item.status:
                    existing.status = item.status
                if (
                    item.fifo_ts_epoch_ms is not None
                    and existing.status == MarkStatus.AVAILABLE.value
                ):
                    existing.fifo_ts = _from_epoch_ms(item.fifo_ts_epoch_ms) or existing.fifo_ts
                self.session.add(existing)
                response.updated += 1

        if full_mode:
            stmt = select(Mark)
            for mark in list(self.session.scalars(stmt)):
                if mark.mark_code in incoming:
                    continue
                if (
                    mark.status == MarkStatus.AVAILABLE.value
                    and mark.active_reservation_id is None
                ):
                    self.session.delete(mark)
                    response.skipped += 1

        return response


# ========================================================================
# helpers
# ========================================================================
def _now() -> datetime:
    return datetime.now(timezone.utc)


def _from_epoch_ms(epoch_ms: Optional[int]) -> Optional[datetime]:
    if epoch_ms is None:
        return None
    return datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc)


def _to_epoch_ms(dt: Optional[datetime]) -> Optional[int]:
    if dt is None:
        return None
    return int(dt.timestamp() * 1000)
