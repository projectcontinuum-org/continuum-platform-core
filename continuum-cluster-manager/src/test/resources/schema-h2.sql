-- H2-compatible schema for tests (mirrors main schema.sql without PostgreSQL-specific features)
CREATE TABLE IF NOT EXISTS workbench_instances (
    instance_id         UUID PRIMARY KEY,
    instance_name       VARCHAR(255) NOT NULL,
    namespace           VARCHAR(255) NOT NULL DEFAULT 'default',
    user_id             VARCHAR(255) NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    image               VARCHAR(512) NOT NULL,
    cpu_request         VARCHAR(50)  NOT NULL DEFAULT '500m',
    cpu_limit           VARCHAR(50)  NOT NULL DEFAULT '2',
    memory_request      VARCHAR(50)  NOT NULL DEFAULT '512Mi',
    memory_limit        VARCHAR(50)  NOT NULL DEFAULT '1Gi',
    storage_size        VARCHAR(50)  NOT NULL DEFAULT '5Gi',
    storage_class_name  VARCHAR(255),
    k8s_resources       TEXT         NOT NULL DEFAULT '[]',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_version      BIGINT       NOT NULL DEFAULT 0
);

-- Note: Partial unique index (WHERE clause) not supported in H2
-- The duplicate prevention is handled in application code (WorkbenchService)

-- Index for fast lookups by user_id and instance_name
CREATE INDEX IF NOT EXISTS idx_workbench_user_instance
    ON workbench_instances (user_id, instance_name);

-- Index for listing workbenches by user (excludes deleted)
CREATE INDEX IF NOT EXISTS idx_workbench_user_status
    ON workbench_instances (user_id, status);

