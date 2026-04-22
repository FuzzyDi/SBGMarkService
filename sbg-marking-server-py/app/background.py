"""Фоновые джобы сервера.

Сейчас здесь один периодический sweep: отпустить истёкшие резервы и удалить
просроченные substitution-mapping'и. Нужен как подстраховка на случай, когда
кассы простаивают и inline-cleanup в запросах не срабатывает.

Запускается в {@code create_app()} через {@link start_cleanup_scheduler}.
Отключается установкой env {@code SBG_MARKING_DISABLE_SCHEDULER=1} (тесты,
миграции, ad-hoc скрипты).

APScheduler {@code BackgroundScheduler} крутит джоб в отдельном daemon-потоке,
не блокирует Flask. Каждый tick — короткая транзакция в новой сессии.
"""
from __future__ import annotations

import atexit
import logging
from typing import Optional

from apscheduler.schedulers.background import BackgroundScheduler

from app.db import session_scope
from app.service import MarkingService

log = logging.getLogger(__name__)

_scheduler: Optional[BackgroundScheduler] = None


def _cleanup_tick(reservation_ttl_sec: int) -> None:
    """Один цикл очистки. Свои commit/rollback — через session_scope."""
    try:
        with session_scope() as session:
            svc = MarkingService(session, reservation_ttl_sec)
            # Метод приватный по конвенции; дёргаем через вежливый доступ.
            # Оба cleanup'а идемпотентны и безопасно повторяемы.
            svc._cleanup_expired_reservations()
            svc._cleanup_expired_mappings()
    except Exception:  # pragma: no cover - защитная, логируем и продолжаем
        log.exception("cleanup tick failed")


def start_cleanup_scheduler(reservation_ttl_sec: int, interval_sec: int) -> None:
    """Запускает фоновый sweeper. Повторные вызовы игнорируются."""
    global _scheduler
    if _scheduler is not None:
        return

    sched = BackgroundScheduler(daemon=True, timezone="UTC")
    sched.add_job(
        _cleanup_tick,
        trigger="interval",
        seconds=interval_sec,
        kwargs={"reservation_ttl_sec": reservation_ttl_sec},
        id="cleanup_expired",
        max_instances=1,
        coalesce=True,
        next_run_time=None,  # первый запуск — через interval_sec
    )
    sched.start()
    _scheduler = sched
    log.info(
        "background cleanup scheduler started (interval=%ds, reservation_ttl=%ds)",
        interval_sec,
        reservation_ttl_sec,
    )

    # Аккуратный shutdown при остановке процесса.
    atexit.register(stop_cleanup_scheduler)


def stop_cleanup_scheduler() -> None:
    """Останавливает scheduler, если он запущен."""
    global _scheduler
    if _scheduler is None:
        return
    try:
        _scheduler.shutdown(wait=False)
    except Exception:  # pragma: no cover
        log.exception("scheduler shutdown failed")
    finally:
        _scheduler = None
        log.info("background cleanup scheduler stopped")
