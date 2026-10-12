CREATE TABLE IF NOT EXISTS knime_workflows (
    workflow_id   UUID PRIMARY KEY,
    owned_by      VARCHAR(255) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    object_key    VARCHAR(2048) NOT NULL,
    bucket_name   VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    content_type  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_knime_workflows_owned_by ON knime_workflows (owned_by);

CREATE TABLE IF NOT EXISTS knime_workflow_executions (
    execution_id  UUID PRIMARY KEY,
    workflow_id   UUID NOT NULL REFERENCES knime_workflows(workflow_id),
    owned_by      VARCHAR(255) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    object_key    VARCHAR(2048) NOT NULL,
    bucket_name   VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    content_type  VARCHAR(255),
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_knime_workflow_executions_workflow_id ON knime_workflow_executions (workflow_id);

