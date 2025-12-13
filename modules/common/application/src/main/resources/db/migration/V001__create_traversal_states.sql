-- Traversal State Storage
-- Stores DAG traversal state as JSONB with optimistic locking support

CREATE TABLE IF NOT EXISTS traversal_states (
    traversal_id VARCHAR(26) PRIMARY KEY,  -- ULID format
    state JSONB NOT NULL,                   -- Full state as JSON
    version BIGINT NOT NULL DEFAULT 1,      -- Optimistic locking version
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for listing active traversals by creation time
CREATE INDEX IF NOT EXISTS idx_traversal_states_created_at
    ON traversal_states(created_at DESC);

-- GIN index for querying state contents (optional, for advanced queries)
CREATE INDEX IF NOT EXISTS idx_traversal_states_state_gin
    ON traversal_states USING gin(state);

-- Distributed Lock Table
-- Provides mutex-like locking for cluster coordination with automatic expiration

CREATE TABLE IF NOT EXISTS traversal_locks (
    traversal_id VARCHAR(26) PRIMARY KEY,
    locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Index for finding and cleaning expired locks
CREATE INDEX IF NOT EXISTS idx_traversal_locks_expires_at
    ON traversal_locks(expires_at);

-- Cleanup function for expired locks
CREATE OR REPLACE FUNCTION cleanup_expired_traversal_locks()
RETURNS void AS $$
BEGIN
    DELETE FROM traversal_locks WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

-- Optional: Event History Table for Audit/Replay
-- Stores all state events for complete history and replay capability

CREATE TABLE IF NOT EXISTS traversal_events (
    id BIGSERIAL PRIMARY KEY,
    traversal_id VARCHAR(26) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    vector_clock JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for querying events by traversal
CREATE INDEX IF NOT EXISTS idx_traversal_events_traversal_id
    ON traversal_events(traversal_id);

-- Index for time-based queries
CREATE INDEX IF NOT EXISTS idx_traversal_events_created_at
    ON traversal_events(created_at);

-- Composite index for efficient traversal event ordering
CREATE INDEX IF NOT EXISTS idx_traversal_events_traversal_created
    ON traversal_events(traversal_id, created_at);
