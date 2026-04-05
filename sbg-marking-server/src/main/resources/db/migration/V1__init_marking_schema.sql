CREATE TABLE IF NOT EXISTS marks (
    mark_code VARCHAR(256) PRIMARY KEY,
    item VARCHAR(128),
    gtin VARCHAR(64),
    product_type VARCHAR(64) NOT NULL,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    fifo_ts TIMESTAMPTZ NOT NULL,
    active_reservation_id VARCHAR(64),
    last_sale_receipt_id VARCHAR(128),
    last_return_receipt_id VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_marks_selection
    ON marks (product_type, item, gtin, status, valid, blocked, fifo_ts);

CREATE TABLE IF NOT EXISTS reservations (
    id VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(128) NOT NULL,
    mark_code VARCHAR(256) NOT NULL,
    type VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_reservation_mark FOREIGN KEY (mark_code) REFERENCES marks (mark_code)
);

CREATE INDEX IF NOT EXISTS idx_reservations_mark_code ON reservations (mark_code);
CREATE INDEX IF NOT EXISTS idx_reservations_expires_at ON reservations (expires_at);

CREATE TABLE IF NOT EXISTS history_events (
    id BIGSERIAL PRIMARY KEY,
    event_ts TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128),
    mark_code VARCHAR(256),
    reservation_id VARCHAR(64),
    product_type VARCHAR(64),
    item VARCHAR(128),
    gtin VARCHAR(64),
    shop_id VARCHAR(32),
    pos_id VARCHAR(32),
    cashier_id VARCHAR(128),
    receipt_id VARCHAR(128),
    success BOOLEAN NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    message TEXT
);

CREATE INDEX IF NOT EXISTS idx_history_mark_code_ts ON history_events (mark_code, event_ts);
CREATE INDEX IF NOT EXISTS idx_history_event_ts ON history_events (event_ts);
