# sbg-marking-server-py

Python-реализация backend SBG Marking — **для локального тестирования Set10-плагина**.

JSON-контракт и схема БД идентичны Java-версии (`sbg-marking-server/`), поэтому плагин не требует изменений: достаточно переключить `marking.service.url` на `http://<host>:8090`.

> Статус: временный тестовый stub. После стабилизации плагина вернёмся к Java-серверу.

## Стек

- **Flask 3.0** — application factory pattern (`app/__init__.py::create_app`)
- **SQLAlchemy 2.0** — типизированная ORM
- **Alembic** — миграции (схема 1:1 с Flyway V1+V2 из Java)
- **Pydantic v2** — DTO с `alias_generator=to_camel` для camelCase JSON
- **psycopg 3** — драйвер PostgreSQL
- **Gunicorn** — prod WSGI
- **PostgreSQL 15** — БД

## Изоляция от Java-стэка

| Компонент | Java | Python |
|-----------|------|--------|
| HTTP-порт | 8080 | **8090** |
| Postgres-порт | 5432 | **5433** |
| DB name | `sbg_marking` | `sbg_marking_py` |
| Volume | — | `pgdata-py` |

Два стэка могут работать параллельно без конфликтов.

## Быстрый старт

```bash
cd sbg-marking-server-py
docker compose up --build -d

# Проверить здоровье
curl http://localhost:8090/health
# -> {"status":"UP"}
```

Alembic поднимается автоматически при старте контейнера (`entrypoint.sh`).

## Настройка плагина

В `metainf.xml` или `IntegrationProperties` плагина:

```
marking.service.url=http://localhost:8090
marking.service.connect.timeout.ms=3000
marking.service.read.timeout.ms=5000
```

## Endpoints

| Метод | Путь | DTO |
|-------|------|-----|
| GET | `/health` | — |
| POST | `/api/v1/marking/resolve-and-reserve` | ResolveAndReserveRequest → ResolveAndReserveResponse |
| POST | `/api/v1/marking/return-resolve-and-reserve` | ReturnResolveAndReserveRequest → ReturnResolveAndReserveResponse |
| POST | `/api/v1/marking/sold-confirm` | MarkOperationRequest → OperationResponse |
| POST | `/api/v1/marking/sale-release` | MarkOperationRequest → OperationResponse |
| POST | `/api/v1/marking/return-confirm` | MarkOperationRequest → OperationResponse |
| POST | `/api/v1/marking/return-release` | MarkOperationRequest → OperationResponse |
| POST | `/api/v1/km/import/full` | ImportRequest → ImportResponse |
| POST | `/api/v1/km/import/delta` | ImportRequest → ImportResponse |

### Контракт (camelCase, точь-в-точь как у Java-плагина)

`ResolveResult`: `ACCEPT_SCANNED | ACCEPT_AUTO_SELECTED | REJECT_NO_CANDIDATE | HARD_REJECT`
`MarkSource`: `SCANNED | AUTO_SELECTED`
`ErrorCode`: `NONE | NO_CANDIDATE | INVALID_MARK_FOR_PRODUCT | SERVICE_UNAVAILABLE | RESERVE_CONFLICT | ALREADY_RETURNED | INVALID_STATE | VALIDATION_FAILED | INTERNAL_ERROR`

## Засеять тестовые КМ

```bash
curl -X POST http://localhost:8090/api/v1/km/import/full \
  -H 'Content-Type: application/json' \
  -d '{
    "batchId": "seed-001",
    "items": [
      {
        "markCode": "MARK-DEMO-001",
        "item": "ITM-001",
        "gtin": "04670000000017",
        "productType": "CIGARETTES",
        "valid": true,
        "blocked": false,
        "status": "AVAILABLE",
        "fifoTsEpochMs": 1700000000000
      },
      {
        "markCode": "MARK-DEMO-002",
        "item": "ITM-001",
        "gtin": "04670000000017",
        "productType": "CIGARETTES",
        "valid": true,
        "blocked": false,
        "status": "AVAILABLE",
        "fifoTsEpochMs": 1700000000001
      }
    ]
  }'
```

## Сценарий продажи вручную

```bash
# 1. Сканирование — плагин запрашивает резерв
curl -X POST http://localhost:8090/api/v1/marking/resolve-and-reserve \
  -H 'Content-Type: application/json' \
  -d '{
    "operationId": "op-sale-001",
    "shopId": "SHOP1", "posId": "POS1", "cashierId": "C1",
    "product": {"item":"ITM-001","gtin":"04670000000017","productType":"CIGARETTES"},
    "scannedMark": "MARK-DEMO-001",
    "quantity": 1
  }'
# -> {"result":"ACCEPT_SCANNED","appliedMark":"MARK-DEMO-001","source":"SCANNED","reservationId":"...","ttlSec":900,"errorCode":"NONE",...}

# 2. Фискализация — подтвердить продажу
curl -X POST http://localhost:8090/api/v1/marking/sold-confirm \
  -H 'Content-Type: application/json' \
  -d '{"operationId":"op-confirm-001","reservationId":"<из шага 1>","markCode":"MARK-DEMO-001","receiptId":"R-42"}'
# -> {"success":true,"errorCode":"NONE","message":"Sale confirmed."}
```

### Автовыбор по FIFO

Если сканированная КМ **отсутствует** в пуле, backend выберет подходящую по FIFO и вернёт `ACCEPT_AUTO_SELECTED` + `appliedMark` — касса должна напечатать именно её.

## Разработка

```bash
cd sbg-marking-server-py

# Установить deps (нужен Python 3.11-3.12)
pip install -r requirements-dev.txt

# Поднять только Postgres
docker compose up -d postgres-py

# Миграции
alembic upgrade head

# Dev-сервер
python run.py
```

### Тесты

```bash
pytest
```

Тесты используют SQLite in-memory через `StaticPool`, PostgreSQL не нужен.

### Новая миграция

```bash
alembic revision -m "add_foo_column"
# правка migrations/versions/*.py
alembic upgrade head
```

## Структура

```
sbg-marking-server-py/
├── app/
│   ├── __init__.py      # create_app() factory
│   ├── api.py           # Flask blueprints (/health + marking + import)
│   ├── config.py        # Settings from env
│   ├── db.py            # SQLAlchemy engine + session_scope()
│   ├── enums.py         # ResolveResult, ErrorCode, ...
│   ├── models.py        # ORM: Mark, Reservation, IdempotencyEntry
│   ├── schemas.py       # Pydantic DTO (CamelModel)
│   └── service.py       # MarkingService — бизнес-логика
├── migrations/
│   ├── env.py
│   └── versions/20260416_0001_initial_schema.py
├── tests/
│   ├── conftest.py
│   ├── test_sale_flow.py
│   └── test_return_flow.py
├── alembic.ini
├── Dockerfile
├── docker-compose.yml
├── entrypoint.sh        # alembic upgrade + gunicorn
├── pyproject.toml
├── requirements.txt
├── requirements-dev.txt
└── run.py               # dev entrypoint
```

## Известные упрощения (vs Java)

- Нет таблицы `history_events` — не нужна для плагина.
- Нет `validation_policy` (V3) — хардкод в `MarkingService._validate_*`.
- Нет фоновой чистки истёкших резерваций отдельным scheduler-ом — просто вызов `_cleanup_expired_reservations()` в начале каждого запроса.
- Импорт минимальный (без GS1-check-digit, без quarantine-таблицы) — только для быстрого засева тестовых КМ.

Для production возвращаемся к `sbg-marking-server/` (Java/Spring).
