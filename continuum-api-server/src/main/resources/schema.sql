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

-- Every endpoint is scoped by owner (list, count, tree, active, cancel, terminate, status, runs and node output), so
-- every one of them filters on owned_by. The list endpoints sort newest first, hence created_at DESC in the same
-- index: the common query (a caller's most recent runs, paged) is then one index scan with no sort. Without it each of
-- these queries scans the whole table, which grows with every run and cannot be pruned through the API.
CREATE INDEX IF NOT EXISTS ix_workflow_runs_owned_by_created_at ON workflow_runs (owned_by, created_at DESC);

-- Temporal run id of the execution. Node outputs are stored under <run_id>/<node_id>/..., so this is the key that ties
-- a stored output back to its owner. Written by the message-bridge from the workflow update events; it differs from
-- workflow_id (the id the API returns).
ALTER TABLE workflow_runs ADD COLUMN IF NOT EXISTS run_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS ix_workflow_runs_run_id ON workflow_runs (run_id) WHERE run_id IS NOT NULL;

-- Backfill runs that finished before run_id existed: their stored output paths look like
-- {remote}<run id>/<node id>/output.<port>.parquet. Only rows that still have no run_id are touched, so this is a
-- no-op on every start after the first.
UPDATE workflow_runs
SET run_id = (regexp_match(data::text, '\{remote\}([0-9a-fA-F-]{36})/'))[1]
WHERE run_id IS NULL AND data::text LIKE '%{remote}%';
