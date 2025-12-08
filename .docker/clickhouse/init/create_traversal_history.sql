CREATE TABLE system_traversal_history ON CLUSTER waygrid_cluster
(
    traversal_id           String,
    event_id               UUID,
    node_id                String,
    node_address           String,
    status                 Enum8(
    'started' = 1,
    'completed' = 2,
    'failed' = 3,
    'retry' = 4
),
    failure_reason         String,
    start_ts               DateTime64(6, 'UTC'),
    end_ts                 DateTime64(6, 'UTC'),
    duration_ms            UInt32,
    remaining_nodes        Array(String),
    completed_nodes        Array(String),
    failed_nodes           Array(String),
    retry_attempts         UInt8,
    vector_clock           String,
    metadata               JSON,
    received_at            DateTime DEFAULT now(),
    date                   Date DEFAULT toDate(start_ts)
    )
    ENGINE = MergeTree
    PARTITION BY date
    ORDER BY (traversal_id, start_ts, node_id)
    SETTINGS index_granularity = 8192;
