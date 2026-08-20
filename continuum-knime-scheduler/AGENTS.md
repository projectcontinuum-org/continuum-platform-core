## Purpose

Object-store CRUD proxy for user-authored KNIME workflow (`.knwf`) files. Wraps MinIO/AWS S3 behind a REST API and keeps a PostgreSQL bookkeeping table of what's stored where.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-knime-scheduler`. Runs on port 8085.

## Local Contracts

- `KnimeWorkflowController` — `/api/v1/knime-workflows` CRUD (`POST`, `GET` list/get/content, `PUT`, `DELETE`). Every endpoint reads `x-continuum-user-id` (defaults to `anonymous`) and scopes storage + DB access to that caller.
- Objects live at `knime-workflows/users/{user-id}/{workflowId}.knwf` in the dedicated bucket configured under `continuum.core.knime-scheduler.storage.*` — built exclusively via `KnimeWorkflowKeyBuilder`, never inlined elsewhere.
- `knime_workflows` Postgres table is bookkeeping-only: `workflow_id` (PK, same UUID as the object key), `owned_by`, `object_key`, `bucket_name`, `size_bytes`, `content_type`, timestamps. Every read/write goes through `KnimeWorkflowRepository`'s owner-scoped finders so a workflow belonging to another user is never reachable.
- `ObjectStorageService` (backed by `S3Client`, selected between AWS S3 / MinIO via `continuum.core.knime-scheduler.storage.type`) is the only component allowed to talk to the object store.
- Despite the module name, this phase is CRUD + storage + bookkeeping only — no scheduling/execution integration with `continuum-orchestration-service` or `continuum-knime-base` yet.

## Work Guidance

- Keep S3 backend selection on a single property prefix (`continuum.core.knime-scheduler.storage.type`) for both the MinIO and AWS-S3 conditional beans in `S3ClientConfig`. The equivalent config in `continuum-worker-springboot-starter`'s `S3Config.kt` checks two different prefixes for its two beans — a latent misconfiguration trap. Don't repeat that here.

## Verification

- `./gradlew :continuum-knime-scheduler:test`
- Manual: `docker compose up -d` (from `docker/`), `./gradlew :continuum-knime-scheduler:bootRun`, then exercise upload/get/download/replace/delete via `curl` with different `x-continuum-user-id` values to confirm cross-user access returns 404.
