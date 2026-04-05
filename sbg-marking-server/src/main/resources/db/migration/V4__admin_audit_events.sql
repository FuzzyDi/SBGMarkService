CREATE TABLE IF NOT EXISTS admin_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_ts TIMESTAMPTZ NOT NULL,
    actor_user VARCHAR(128),
    actor_role VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    endpoint VARCHAR(256),
    request_id VARCHAR(128),
    remote_addr VARCHAR(128),
    target_mark_code VARCHAR(512),
    success BOOLEAN NOT NULL,
    message VARCHAR(1024),
    payload_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_event_ts ON admin_audit_events (event_ts DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_action ON admin_audit_events (action);
CREATE INDEX IF NOT EXISTS idx_admin_audit_mark_code ON admin_audit_events (target_mark_code);
