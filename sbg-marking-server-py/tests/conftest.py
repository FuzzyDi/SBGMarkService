"""Общие фикстуры: Flask app + SQLite in-memory + чистый schema на каждый тест."""
from __future__ import annotations

import os

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.db as db_mod
from app.db import Base


def _install_sqlite_engine() -> None:
    """Подставляет единый SQLite in-memory engine в app.db.

    StaticPool гарантирует один и тот же connection между сессиями — иначе
    `:memory:` даёт новую пустую БД на каждое соединение.
    """
    engine = create_engine(
        "sqlite:///:memory:",
        future=True,
        poolclass=StaticPool,
        connect_args={"check_same_thread": False},
    )
    Base.metadata.create_all(engine)
    db_mod._engine = engine
    db_mod._SessionFactory = sessionmaker(bind=engine, expire_on_commit=False, future=True)


@pytest.fixture
def app_client(monkeypatch):
    """Flask test client с in-memory БД. Перехватываем init_engine, чтобы
    не тянуть настоящий PostgreSQL через create_app()."""
    os.environ.setdefault("SBG_MARKING_LOG_LEVEL", "WARNING")
    # Фоновый cleanup scheduler не нужен в тестах — inline cleanup и так
    # покрывает все сценарии, а лишний daemon-поток усложняет teardown БД.
    monkeypatch.setenv("SBG_MARKING_DISABLE_SCHEDULER", "1")

    def _noop_init_engine(_url: str) -> None:
        _install_sqlite_engine()

    monkeypatch.setattr("app.db.init_engine", _noop_init_engine)
    monkeypatch.setattr("app.init_engine", _noop_init_engine)

    from app import create_app

    flask_app = create_app()
    flask_app.config.update(TESTING=True)

    with flask_app.test_client() as client:
        yield client

    if db_mod._engine is not None:
        Base.metadata.drop_all(db_mod._engine)
        db_mod._engine.dispose()
    db_mod._engine = None
    db_mod._SessionFactory = None
