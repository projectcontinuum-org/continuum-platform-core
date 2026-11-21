## Purpose

AES-GCM encrypted credential store with REST API and embedded React UI. Manages named credentials (key-value bundles) and credential types. Credentials are encrypted at rest with a master key. Accessed by workers on demand via `CredentialFetcherService` (`continuum-worker-springboot-starter`), called from within a node's `execute()`.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-credentials-server`. Runs on port 8080 (mapped to 38083 in docker-compose).

## Local Contracts

- Credentials are scoped to `ownerId` (from `x-continuum-user-id` header). Workers fetch by name within the owner's scope.
- Encryption: AES-GCM via `AesGcmEncryptionService`. Master key from `CREDENTIALS_ENCRYPTION_MASTER_KEY` env var — **required, no default**. Key is base64-encoded.
- Storage backend: `CREDENTIALS_STORAGE_BACKEND` (default `postgres`). Data is encrypted before writing to `credentials` table.
- Credential types define the schema (set of field keys) for credentials of that type. Built-in types are seeded at startup by `BuiltInCredentialTypeInitializer`.
- API: `GET/POST/PUT/DELETE /api/v1/credentials/{name}` and `GET/POST/DELETE /api/v1/credential-types`.
- Frontend: React + Vite built by `frontendBuild` Gradle task into `frontend/dist`, then copied into Spring Boot static resources. `processResources` depends on `copyFrontend`.
- `UiFallbackController` serves `index.html` for non-API routes (SPA fallback).
- Errors: `GlobalExceptionHandler` (a `ResponseEntityExceptionHandler`) returns `ProblemDetail` (`application/problem+json`) for every error, with the message in `detail` **and** in the deprecated `error` property (the embedded UI and the workbench's credential picker read `.error`). Bean-validation failures also list `fieldErrors`. Status codes: not found 404, already exists / concurrent change 409, validation 400, encryption and anything unexpected 500 with a generic detail.
- Every credential read (list, by type, by name) writes `lastAccessedAt`; reads are not side-effect free.
- OpenAPI: follow `docs/api/CONVENTIONS.md` and `docs/api/ENDPOINT_INVENTORY.md`. springdoc comes from `libs.springdoc.openapi.starter.webmvc.ui`, with `com.fasterxml.jackson.module:jackson-module-kotlin` at runtime so non-null Kotlin fields are `required`.
  - Settings in `application.yml`: `CONTINUUM_API_DOCS_ENABLED` (default `true`, Helm `false`), `CONTINUUM_API_DOCS_SERVER_URL`, `server.forward-headers-strategy: framework`.
  - `config.OpenApiConfig` owns the metadata, the single `public` group (no internal endpoints), the `continuumUserId` security scheme, the `ProblemDetail` schema and the shared 400/500 responses.
  - Tags: "Credentials" (user-scoped), "Credential types" (not user-scoped). Request examples live in `controller.CredentialApiExamples`. `UiFallbackController` is `@Hidden`.

## Work Guidance

- Any change to an endpoint, a request/response model or a status code follows the root `AGENTS.md` rule "Rule for AI agents: every API change updates the OpenAPI spec": inventory, annotations, regenerated `docs/api/continuum-credentials-server.openapi.yaml`, Redocly lint and release notes, in the same change.
- Frontend build is triggered automatically by `./gradlew :continuum-credentials-server:build`. To build only the frontend: `./gradlew :continuum-credentials-server:frontendBuild`.
- `CREDENTIALS_ENCRYPTION_MASTER_KEY` must be set before starting the server. In local dev, docker-compose provides a test key (`dGVzdC1tYXN0ZXIta2V5LWZvci1sb2NhbC1kZXY=`).
- Tests use H2 in-memory database. No encryption key is needed for tests — check the test `application.yml`.

## Verification

```bash
./gradlew :continuum-credentials-server:test
# after an intended API change: regenerate docs/api/continuum-credentials-server.openapi.yaml, review, commit
./gradlew :continuum-credentials-server:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec
```

- `openapi.OpenApiSpecTest` boots the full context on H2 (`H2JdbcConfig`, the main `application.yml` imported because the test one shadows it); `exception.ProblemDetailContractTest` pins the error contract.
