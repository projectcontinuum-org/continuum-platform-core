## Purpose

AES-GCM encrypted credential store with REST API and embedded React UI. Manages named credentials (key-value bundles) and credential types. Credentials are encrypted at rest with a master key. Accessed by workers via `CredentialResolver` before node execution.

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

## Work Guidance

- Frontend build is triggered automatically by `./gradlew :continuum-credentials-server:build`. To build only the frontend: `./gradlew :continuum-credentials-server:frontendBuild`.
- `CREDENTIALS_ENCRYPTION_MASTER_KEY` must be set before starting the server. In local dev, docker-compose provides a test key (`dGVzdC1tYXN0ZXIta2V5LWZvci1sb2NhbC1kZXY=`).
- Tests use H2 in-memory database. No encryption key is needed for tests — check the test `application.yml`.

## Verification

```bash
./gradlew :continuum-credentials-server:test
```
