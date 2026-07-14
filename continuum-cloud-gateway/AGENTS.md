## Purpose

HTTP and WebSocket reverse proxy for per-user workbench pods. Resolves the target pod service from PostgreSQL, strips the `/workbench/{instanceName}/open` path prefix, and forwards all traffic including WebSocket upgrades.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-cloud-gateway`. Runs on port 8080 (mapped to 38084 in docker-compose).

## Local Contracts

- `WorkbenchProxyController` — handles `GET/POST /workbench/{instanceName}/open/**`. Validates instance belongs to `x-continuum-user-id` owner and status is `RUNNING`.
- `WorkbenchWebSocketProxyHandler` — handles WebSocket upgrades at `/workbench/{instanceName}/open/**`.
- Target URL: `http://wb-{instanceId}-svc.{namespace}.svc.cluster.local:8080{remaining-path}`.
- Hop-by-hop headers (`connection`, `transfer-encoding`, `host`, `content-length`, etc.) are stripped before forwarding.
- `WorkbenchInstanceRepository` is read-only here — the gateway does not write workbench state.
- Requires the same PostgreSQL instance as `continuum-cluster-manager`.
- `WorkbenchProxyService` uses `java.net.http.HttpClient` (HTTP/1.1, follow redirects disabled, 5 min timeout).

## Work Guidance

- Debug logging for the proxy service is on by default (`application.yml`). Disable in production.
- The gateway shares the `WorkbenchInstanceEntity` table with `continuum-cluster-manager` — do not add DDL migrations here; manage schema from `continuum-cluster-manager`.
