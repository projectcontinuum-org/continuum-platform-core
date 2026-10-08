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
    overlay_variant     VARCHAR(255),
    ingress_url         VARCHAR(1024),
    k8s_resources       TEXT         NOT NULL DEFAULT '[]',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_version      BIGINT       NOT NULL DEFAULT 0
);

-- Partial unique index to prevent duplicate active workbenches per user (PostgreSQL-specific)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_workbench
    ON workbench_instances (user_id, instance_name)
    WHERE status NOT IN ('DELETED', 'TERMINATING');

-- Index for fast lookups by user_id and instance_name
CREATE INDEX IF NOT EXISTS idx_workbench_user_instance
    ON workbench_instances (user_id, instance_name);

-- Index for listing workbenches by user (excludes deleted)
CREATE INDEX IF NOT EXISTS idx_workbench_user_status
    ON workbench_instances (user_id, status);

CREATE TABLE IF NOT EXISTS feature_worker_instances (
    worker_id                     UUID PRIMARY KEY,
    worker_name                   VARCHAR(255) NOT NULL,
    namespace                     VARCHAR(255) NOT NULL DEFAULT 'default',
    created_by                    VARCHAR(255) NOT NULL,
    status                        VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    image                         VARCHAR(512) NOT NULL,
    task_queue                    VARCHAR(255) NOT NULL,
    replicas                      INT          NOT NULL DEFAULT 1,
    autoscaling_enabled           BOOLEAN      NOT NULL DEFAULT false,
    autoscaling_min_replicas      INT,
    autoscaling_max_replicas      INT,
    autoscaling_target_cpu_percent INT,
    cpu_request                   VARCHAR(50)  NOT NULL DEFAULT '500m',
    cpu_limit                     VARCHAR(50)  NOT NULL DEFAULT '2',
    memory_request                VARCHAR(50)  NOT NULL DEFAULT '512Mi',
    memory_limit                  VARCHAR(50)  NOT NULL DEFAULT '1Gi',
    pvc_enabled                   BOOLEAN      NOT NULL DEFAULT false,
    storage_size                  VARCHAR(50),
    storage_class_name            VARCHAR(255),
    env_vars                      TEXT         NOT NULL DEFAULT '{}',
    overlay_variant                VARCHAR(255),
    k8s_resources                 TEXT         NOT NULL DEFAULT '[]',
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_version                BIGINT       NOT NULL DEFAULT 0
);

-- Partial unique index to prevent duplicate active feature workers by name within a namespace (PostgreSQL-specific)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_feature_worker_name
    ON feature_worker_instances (namespace, worker_name)
    WHERE status NOT IN ('DELETED', 'TERMINATING');

-- Partial unique index: a Temporal task queue can only be served by one logical worker pool
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_feature_worker_task_queue
    ON feature_worker_instances (task_queue)
    WHERE status NOT IN ('DELETED', 'TERMINATING');

-- Index for listing feature workers by namespace and status
CREATE INDEX IF NOT EXISTS idx_feature_worker_namespace_status
    ON feature_worker_instances (namespace, status);

