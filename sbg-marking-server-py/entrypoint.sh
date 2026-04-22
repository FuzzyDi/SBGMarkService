#!/bin/sh
set -e

echo "[entrypoint] running alembic upgrade head..."
alembic upgrade head

echo "[entrypoint] starting gunicorn on :${SBG_MARKING_PORT:-8090}..."
exec gunicorn \
    --bind "0.0.0.0:${SBG_MARKING_PORT:-8090}" \
    --workers 2 \
    --access-logfile - \
    --error-logfile - \
    "app:create_app()"
