CREATE TABLE IF NOT EXISTS validation_policy (
    id INTEGER PRIMARY KEY,
    reject_unknown_mark BOOLEAN NOT NULL DEFAULT TRUE,
    require_product_match BOOLEAN NOT NULL DEFAULT TRUE,
    reject_invalid_flag BOOLEAN NOT NULL DEFAULT TRUE,
    reject_blocked BOOLEAN NOT NULL DEFAULT TRUE,
    sale_allowed_statuses VARCHAR(256) NOT NULL DEFAULT 'AVAILABLE',
    return_allowed_statuses VARCHAR(256) NOT NULL DEFAULT 'SOLD'
);

INSERT INTO validation_policy (
    id,
    reject_unknown_mark,
    require_product_match,
    reject_invalid_flag,
    reject_blocked,
    sale_allowed_statuses,
    return_allowed_statuses
)
SELECT
    1,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    'AVAILABLE',
    'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM validation_policy WHERE id = 1);
