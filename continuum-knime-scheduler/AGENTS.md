## Purpose

Object-store CRUD proxy for user-authored KNIME workflow (`.knwf`) files. Wraps MinIO/AWS S3 behind a REST API and keeps a PostgreSQL bookkeeping table of what's stored where.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-knime-scheduler`. Runs on port 8085.

## Local Contracts

- `KnimeWorkflowController` — `/api/v1/knime-workflows` CRUD (`POST`, `GET` list/get/content, `PUT`, `DELETE`). Every endpoint reads `x-continuum-user-id` (defaults to `anonymous`) and scopes storage + DB access to that caller.
- Objects live at `knime-workflows/users/{user-id}/{workflowId}.knwf` in the dedicated bucket configured under `continuum.core.knime-scheduler.storage.*` — built exclusively via `KnimeWorkflowKeyBuilder`, never inlined elsewhere.
- `knime_workflows` Postgres table is bookkeeping-only: `workflow_id` (PK, same UUID as the object key), `owned_by`, `object_key`, `bucket_name`, `size_bytes`, `content_type`, timestamps. Every read/write goes through `KnimeWorkflowRepository`'s owner-scoped finders so a workflow belonging to another user is never reachable.
- `ObjectStorageService` (backed by `S3Client`, selected between AWS S3 / MinIO via `continuum.core.knime-scheduler.storage.type`) is the only component allowed to talk to the object store.
- `KnimeWorkflowScheduleController` — `/api/v1/knime-workflow-schedules` (`POST`, `GET` list/get, `POST .../pause`/`.../unpause`/`.../trigger`, `DELETE`). All KNIME scheduling logic lives here, not in `continuum-api-server`: `KnimeWorkflowScheduleService` resolves `.knwf` ownership locally via `KnimeWorkflowRepository` (no HTTP round trip), builds a single-node `WorkflowModel` DAG via `KnimeScheduleWorkflowMapper`, and calls `continuum-api-server`'s generic `/api/v1/workflow/schedule` API as a plain REST client (`WorkflowScheduleApiClient`, config key `continuum.core.knime-scheduler.api-server-base-url`). The DAG's single node points `workflowLocation` back at this module's own `GET /api/v1/knime-workflows/{id}/content` endpoint via the `continuum.core.knime-scheduler.public-base-url` config key — keep `content`'s auth behavior (owner-scoped via `x-continuum-user-id`) stable, the executor node depends on it.
- `WorkflowScheduleApiClient`'s DTOs (`WorkflowModel`/`WorkflowNode`/`WorkflowNodeData`/`Position`) are a deliberate minimal local duplicate of `continuum-commons`'s `ContinuumWorkflowModel` shape — kept in sync manually rather than via a dependency, to avoid pulling Temporal/Avro/Parquet/Hadoop into this module. `KnimeScheduleWorkflowMapper` also duplicates `KNIMEWorkflowExecutorNodeModel`'s node-model class name and `propertiesSchema` from the sibling `continuum-feature-knime` repo — keep both in sync if that node's contract changes.

## Work Guidance

- Keep S3 backend selection on a single property prefix (`continuum.core.knime-scheduler.storage.type`) for both the MinIO and AWS-S3 conditional beans in `S3ClientConfig`. The equivalent config in `continuum-worker-springboot-starter`'s `S3Config.kt` checks two different prefixes for its two beans — a latent misconfiguration trap. Don't repeat that here.

## Verification

- `./gradlew :continuum-knime-scheduler:test`
- Manual: `docker compose up -d` (from `docker/`), `./gradlew :continuum-knime-scheduler:bootRun`, then exercise upload/get/download/replace/delete via `curl` with different `x-continuum-user-id` values to confirm cross-user access returns 404.
