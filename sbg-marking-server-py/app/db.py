"""SQLAlchemy engine + session helpers."""
from contextlib import contextmanager
from typing import Iterator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker


class Base(DeclarativeBase):
    pass


_engine = None
_SessionFactory: sessionmaker[Session] | None = None


def init_engine(db_url: str) -> None:
    global _engine, _SessionFactory
    _engine = create_engine(db_url, pool_pre_ping=True, future=True)
    _SessionFactory = sessionmaker(bind=_engine, expire_on_commit=False, future=True)


def get_engine():
    if _engine is None:
        raise RuntimeError("Engine not initialized — call init_engine() first")
    return _engine


@contextmanager
def session_scope() -> Iterator[Session]:
    """Короткоживущая сессия с commit/rollback."""
    if _SessionFactory is None:
        raise RuntimeError("SessionFactory not initialized — call init_engine() first")
    session = _SessionFactory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
