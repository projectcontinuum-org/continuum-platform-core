CREATE TABLE IF NOT EXISTS registered_nodes (
    id                      BIGSERIAL PRIMARY KEY,
    node_id                 VARCHAR(500) NOT NULL,
    task_queue              VARCHAR(255) NOT NULL,
    worker_id               VARCHAR(255) NOT NULL,
    feature_id              VARCHAR(500) NOT NULL,
    node_manifest           JSONB NOT NULL,
    documentation_markdown  TEXT NOT NULL,
    categories              JSONB NOT NULL DEFAULT '[]',
    extensions              JSONB NOT NULL DEFAULT '{}',
    registered_at           TIMESTAMP NOT NULL,
    last_seen_at            TIMESTAMP NOT NULL,
    UNIQUE (node_id, task_queue)
);
