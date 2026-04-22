"""SQLAlchemy ORM — схема 1:1 с Java Flyway V1+V2."""
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class Mark(Base):
    __tablename__ = "marks"

    mark_code: Mapped[str] = mapped_column(String(256), primary_key=True)
    item: Mapped[str | None] = mapped_column(String(128), nullable=True)
    gtin: Mapped[str | None] = mapped_column(String(64), nullable=True)
    product_type: Mapped[str] = mapped_column(String(64), nullable=False)
    valid: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    blocked: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    fifo_ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    active_reservation_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    last_sale_receipt_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    last_return_receipt_id: Mapped[str | None] = mapped_column(String(128), nullable=True)

    __table_args__ = (
        Index(
            "idx_marks_selection",
            "product_type", "item", "gtin", "status", "valid", "blocked", "fifo_ts",
        ),
    )


class Reservation(Base):
    __tablename__ = "reservations"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    operation_id: Mapped[str] = mapped_column(String(128), nullable=False)
    mark_code: Mapped[str] = mapped_column(
        String(256),
        ForeignKey("marks.mark_code", name="fk_reservation_mark"),
        nullable=False,
    )
    type: Mapped[str] = mapped_column(String(32), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        Index("idx_reservations_mark_code", "mark_code"),
        Index("idx_reservations_expires_at", "expires_at"),
    )


class SubstitutionMapping(Base):
    """Связь "левая КМ отсканированная кассиром" → "КМ из пула, которую мы подменили".

    Создаётся BarcodeTransformer-плагином ДО валидации. Резервации ещё нет —
    она возникнет при последующем resolve-and-reserve(substituted_mark).

    Идемпотентность: повторный скан той же scanned_mark в пределах TTL
    возвращает уже назначенную substituted_mark (не берём ещё одну из пула).
    """

    __tablename__ = "substitution_mapping"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    scanned_mark: Mapped[str] = mapped_column(String(256), nullable=False)
    substituted_mark: Mapped[str] = mapped_column(
        String(256),
        ForeignKey("marks.mark_code", name="fk_subst_mapping_mark"),
        nullable=False,
    )
    receipt_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    shop_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    pos_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    cashier_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    consumed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    __table_args__ = (
        UniqueConstraint("scanned_mark", name="uq_subst_scanned_mark"),
        Index("idx_subst_substituted_mark", "substituted_mark"),
        Index("idx_subst_expires_at", "expires_at"),
    )


class ExternalScanHistory(Base):
    """Аудит сканирований "левых" КМ (которые не нашлись в пуле).

    Пишется на каждый REJECT_NO_CANDIDATE в resolve-and-reserve. Используется
    для диагностики и — в перспективе — защиты от массовых попыток подбора.
    """

    __tablename__ = "external_scan_history"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    scanned_mark: Mapped[str] = mapped_column(String(256), nullable=False)
    product_type: Mapped[str | None] = mapped_column(String(64), nullable=True)
    gtin: Mapped[str | None] = mapped_column(String(64), nullable=True)
    item: Mapped[str | None] = mapped_column(String(128), nullable=True)
    operation_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    receipt_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    shop_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    pos_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    cashier_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    suggested_mark: Mapped[str | None] = mapped_column(String(256), nullable=True)
    available_count: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        Index("idx_ext_scan_mark", "scanned_mark"),
        Index("idx_ext_scan_created_at", "created_at"),
    )


class IdempotencyEntry(Base):
    __tablename__ = "idempotency_entries"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    route: Mapped[str] = mapped_column(String(64), nullable=False)
    operation_id: Mapped[str] = mapped_column(String(128), nullable=False)
    response_type: Mapped[str] = mapped_column(String(128), nullable=False)
    response_payload: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        UniqueConstraint("route", "operation_id", name="uq_idempotency_route_operation"),
        Index("idx_idempotency_route_op", "route", "operation_id"),
    )
