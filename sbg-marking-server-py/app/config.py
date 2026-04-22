"""Конфиг из env. Используется фабрикой приложения."""
import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    db_url: str = os.environ.get(
        "SBG_MARKING_DB_URL",
        "postgresql+psycopg://postgres:postgres@localhost:5433/sbg_marking_py",
    )
    port: int = int(os.environ.get("SBG_MARKING_PORT", "8090"))
    reservation_ttl_sec: int = int(os.environ.get("SBG_MARKING_RESERVATION_TTL_SEC", "900"))
    cleanup_interval_sec: int = int(os.environ.get("SBG_MARKING_CLEANUP_INTERVAL_SEC", "30"))
    disable_scheduler: bool = os.environ.get("SBG_MARKING_DISABLE_SCHEDULER", "0") == "1"
    log_level: str = os.environ.get("SBG_MARKING_LOG_LEVEL", "INFO")
