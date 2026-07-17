CREATE TABLE IF NOT EXISTS node_tree_entries (
    id                      BIGSERIAL PRIMARY KEY,
    parent_id               BIGINT NULL REFERENCES node_tree_entries(id) ON DELETE RESTRICT,
    type                    VARCHAR(20) NOT NULL CHECK (type IN ('CATEGORY', 'CONTINUUM_NODE')),
    name                    VARCHAR(500) NOT NULL,

    node_id                 VARCHAR(500) NULL,
    task_queue              VARCHAR(255) NULL,
    worker_id               VARCHAR(255) NULL,
    feature_id              VARCHAR(500) NULL,
    node_manifest           JSONB NULL,
    documentation_markdown  TEXT NULL,
    extensions              JSONB NULL DEFAULT '{}',
    registered_at           TIMESTAMPTZ NULL,
    last_seen_at            TIMESTAMPTZ NULL,

    CONSTRAINT chk_node_tree_shape CHECK (
        (type = 'CATEGORY' AND node_id IS NULL AND node_manifest IS NULL)
        OR
        (type = 'CONTINUUM_NODE' AND node_id IS NOT NULL AND node_manifest IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_node_tree_category_sibling
    ON node_tree_entries (COALESCE(parent_id, -1), name) WHERE type = 'CATEGORY';

CREATE UNIQUE INDEX IF NOT EXISTS ux_node_tree_node_placement
    ON node_tree_entries (node_id, COALESCE(parent_id, -1)) WHERE type = 'CONTINUUM_NODE';

CREATE INDEX IF NOT EXISTS ix_node_tree_parent_id ON node_tree_entries (parent_id);

CREATE INDEX IF NOT EXISTS ix_node_tree_node_id ON node_tree_entries (node_id) WHERE type = 'CONTINUUM_NODE';
