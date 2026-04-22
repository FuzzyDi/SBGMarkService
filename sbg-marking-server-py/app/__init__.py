"""Flask application factory."""
import logging

from flask import Flask

from app.api import register_blueprints
from app.background import start_cleanup_scheduler
from app.config import Settings
from app.db import init_engine


def create_app() -> Flask:
    settings = Settings()
    logging.basicConfig(
        level=settings.log_level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )
    app = Flask(__name__)
    app.config["SETTINGS"] = settings
    init_engine(settings.db_url)
    register_blueprints(app)
    if not settings.disable_scheduler:
        start_cleanup_scheduler(
            reservation_ttl_sec=settings.reservation_ttl_sec,
            interval_sec=settings.cleanup_interval_sec,
        )
    return app
