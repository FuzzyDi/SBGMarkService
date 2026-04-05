CREATE TABLE IF NOT EXISTS idempotency_entries (
    id BIGSERIAL PRIMARY KEY,
    route VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    response_type VARCHAR(128) NOT NULL,
    response_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_route_operation UNIQUE (route, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_route_op
    ON idempotency_entries (route, operation_id);
