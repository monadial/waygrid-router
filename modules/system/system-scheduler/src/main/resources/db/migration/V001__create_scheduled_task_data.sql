CREATE TABLE IF NOT EXISTS scheduled_task_data
(
    id         CHAR(26) PRIMARY KEY,      -- ULID as base32 string
    time       TIMESTAMPTZ NOT NULL      -- Schedule execution time
--     partition  SMALLINT    NOT NULL      -- Logical scheduler partition
--     data       BYTEA       NOT NULL,      -- Binary serialized event payload
--     created_at TIMESTAMPTZ DEFAULT now() -- Record creation timestamp
)
