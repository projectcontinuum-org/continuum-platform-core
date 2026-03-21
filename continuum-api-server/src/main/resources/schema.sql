CREATE TABLE IF NOT EXISTS workflow_runs (
    workflow_id     UUID PRIMARY KEY,
    workflow_type   VARCHAR(255) NOT NULL,
    owned_by        VARCHAR(255) NOT NULL,
    workflow_uri    VARCHAR(2048) NOT NULL,
    progress_percentage INT       NOT NULL DEFAULT 0,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    data            JSONB        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
