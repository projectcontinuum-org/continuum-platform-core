CREATE TABLE IF NOT EXISTS workflow_runs (
    workflow_id     VARCHAR(255) PRIMARY KEY,
    workflow_type   VARCHAR(255) NOT NULL,
    owned_by        VARCHAR(255) NOT NULL,
    workflow_uri    VARCHAR(2048) NOT NULL,
    progress_percentage INT       NOT NULL DEFAULT 0,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    data            JSONB        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE workflow_runs ALTER COLUMN workflow_id TYPE VARCHAR(255) USING workflow_id::VARCHAR(255);

CREATE TABLE IF NOT EXISTS workflow_schedules (
    schedule_id     UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    owned_by        VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone       VARCHAR(64),
    workflow        JSONB        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS schedule_id UUID REFERENCES workflow_schedules(schedule_id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS ix_workflow_runs_schedule_id ON workflow_runs (schedule_id) WHERE schedule_id IS NOT NULL;
