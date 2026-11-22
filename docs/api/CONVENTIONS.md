# OpenAPI conventions for Continuum platform services

Every Spring Boot service in `continuum-platform-core` that exposes `@RestController` endpoints follows these rules.
The drift test in each service (`OpenApiSpecTest`) enforces most of them.

## Library and versions

- `springdoc-openapi-starter-webmvc-ui` comes from the version catalog `gradle/libs.versions.toml`
  (`implementation(libs.springdoc.openapi.starter.webmvc.ui)`). springdoc 3.0.x matches Spring Boot 4.0.x. Bump both together.
- springdoc 3.0.x / swagger-core 2.2.x still introspect models with **Jackson 2** (`com.fasterxml`), while the services
  serialize with Jackson 3 (`tools.jackson`). Both are on the classpath on purpose; the split goes away only when
  swagger-core moves to Jackson 3. For Kotlin nullability (non-null = `required`), each service needs
  `com.fasterxml.jackson.module:jackson-module-kotlin` (`runtimeOnly`, version from the Boot BOM) and `kotlin-reflect`.
  Do not bump Jackson versions piecemeal (deferred to the Boot upgrade).
- Kotlin constructor parameters **with a default value** are not required. springdoc does not detect this, so mark them
  `@field:Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, ...)`. `@field:` works for classes that annotate
  Jackson with `@param:`; use `@get:` on abstract documentation-only types. Check the generated spec, not the source.
- A generic response type (`Page<List<CellDto>>`, `TreeItem<Execution>`) or a free-form JSONB map gets a
  documentation-only type (`NodeOutputPage`, `WorkflowTreeItem`, `NodeToOutputMap`) referenced with
  `@Schema(implementation = ...)`; the runtime type is unchanged.

## Configuration (`application.yml` of every service)

```yaml
server:
  forward-headers-strategy: framework   # keep X-Forwarded-Prefix/Host/Proto in Swagger redirects and server URLs
springdoc:
  api-docs:
    enabled: ${CONTINUUM_API_DOCS_ENABLED:true}
  swagger-ui:
    enabled: ${CONTINUUM_API_DOCS_ENABLED:true}
  default-produces-media-type: application/json
  default-consumes-media-type: application/json
continuum.core.api-docs:
  internal-group-enabled: ${CONTINUUM_API_DOCS_INTERNAL_ENABLED:false}
  server-url: ${CONTINUUM_API_DOCS_SERVER_URL:}
```

- `CONTINUUM_API_DOCS_ENABLED` (default `true` for local/dev) switches `/v3/api-docs/**` and `/swagger-ui/**` off completely.
  The Helm chart sets it to `false`. Docs never depend on an auth layer: Continuum has none.
- Remove the old `springdoc.swagger-ui.config-url/url: ../v3/...` overrides and the `../` server customizer.
- Exception: a service with an endpoint that reads `X-Forwarded-*` itself (cluster-manager `/authorize`, Traefik
  ForwardAuth) registers `ForwardedHeaderFilter` as a bean that skips that path instead of setting the strategy, because
  the filter removes those headers from the request.
- `springdoc.override-with-generic-response: false`: operations document their own errors; the advice's handlers must
  not be attached to every operation.
- Server URL: by default springdoc derives it from the request. With `forward-headers-strategy: framework` that includes
  `X-Forwarded-Prefix` (Traefik `stripPrefix` sets it for `/api-server` and `/credentials-manager`), so the URL is absolute
  and right for direct access, the workbench prefixes and openapi-generator. `CONTINUUM_API_DOCS_SERVER_URL`, when set,
  replaces it with one fixed absolute URL (`OpenApiCustomizer`).

## Groups and hidden endpoints

- `GroupedOpenApi` `public`: every public endpoint. Swagger UI shows it first (`springdoc.swagger-ui.urls-primary-name: public`).
- `GroupedOpenApi` `internal`: only internal endpoints, and the bean exists only when **both** docs are enabled and
  `continuum.core.api-docs.internal-group-enabled=true`. Tags that only internal operations use are added by the
  internal group's own customizer, so the public spec does not even name them.
- Internal endpoints (never in `public`, nor in the ungrouped `/v3/api-docs`):
  - api-server `/api/v1/admin/**` (admin, 404-unless-admin by design: it must stay undiscoverable)
  - cluster-manager `/api/v1/workbench/authorize` (Traefik ForwardAuth)
- Non-API controllers (SPA `UiFallbackController`, `/` redirects) are `@Hidden`.
- The test asserts `/v3/api-docs` and `/v3/api-docs/public` contain no internal path, whatever mechanism makes it so.

## Identity headers

- `x-continuum-user-id` / `x-continuum-user-groups` are **not** documented as parameters: annotate the `@RequestHeader`
  parameter with `@Parameter(hidden = true)`. Behavior is unchanged (still optional, default `anonymous`).
- They are described once as `components.securitySchemes`:
  - `continuumUserId`: `type: apiKey`, `in: header`, `name: x-continuum-user-id`
  - `continuumUserGroups`: `type: apiKey`, `in: header`, `name: x-continuum-user-groups` (only where used)
  - Description (adapt per service): "Caller identity. Continuum has no built-in authentication: this header is set by the
    trusted reverse proxy in front of the service from the authenticated user, and the proxy must strip any value sent by
    the client. Without it the caller is treated as `anonymous` (local development only)."
- User-scoped operations carry `@SecurityRequirement(name = "continuumUserId")`. Operations that are not user-scoped
  (e.g. node explorer, credential types, workbench tags/variants) carry none, and their description says
  "Not user-scoped: every caller sees the same data."

## Responses and errors

- Document every real status. `ResponseEntity` returns `201`, `204` or `404`: say so with `@ApiResponse`, and remove the
  phantom `200` springdoc adds (declare all responses explicitly on the operation).
- One error contract for all services: RFC 9457 `ProblemDetail`, media type `application/problem+json`.
  - Shared component schema `ProblemDetail` (`type`, `title`, `status`, `detail`, `instance`; `type` is `about:blank` unless set).
  - api-server only: `WorkflowValidationProblem` = `ProblemDetail` + `errors` and `warnings` (arrays of `WorkflowIssue`).
  - Services migrating from `Map<String,String>` bodies keep an `error` property with the same text (documented as
    deprecated) so existing clients that read `.error` keep working.
- Each service's `@RestControllerAdvice` extends `ResponseEntityExceptionHandler` (Spring MVC errors such as a missing
  parameter, a malformed body, an unparsable UUID, an unknown path or 405/406/415 become ProblemDetail) and has a
  catch-all `Exception` → 500 ProblemDetail with a generic `detail` (no exception message, no stack trace).
- Never scope that advice with `basePackages`/`assignableTypes`/`annotations`: Spring applies a scoped advice only when a
  controller method was resolved, so unknown paths, 405, 406 and 415 would get Boot's default error body.
- Map a wrapping exception by its cause, never as a whole. The Temporal SDK wraps every schedule failure in
  `ScheduleException`; only its gRPC status (`INVALID_ARGUMENT` → 400, `NOT_FOUND` → 404, else 500) says whose fault it is.
- A per-service `OperationCustomizer` adds `406` (an unsupported `Accept`; every operation declares `produces`, so the
  service really answers it) and `500` (ProblemDetail) to every operation, and `400` (ProblemDetail, "malformed
  request") to operations with parameters or a body, unless the operation already declares them. Specific errors
  (`404`, `409`, validation `400`) are declared per endpoint with an example.
- Exceptions to `produces`: an endpoint called with someone else's headers (Traefik ForwardAuth `/authorize` gets the
  browser's `Accept: text/html`) declares `produces = */*`; an internal endpoint that must be undiscoverable declares
  no `produces`/`consumes` at all (they would answer 406/415 before its access check).
- Every extra property a problem body can carry (`errors`, `warnings`, `fieldErrors`, the legacy `error`) is part of the
  documented problem schema, or Redocly rejects the examples.
- Numeric query parameters declare `@Parameter(schema = Schema(type = "integer", format = "int32", ...))` whenever
  `schema` is overridden for `minimum`/`maximum`/`defaultValue`; otherwise springdoc emits `type: string`.
- A security scheme is only added to a spec group whose operations use it (`x-continuum-user-groups` is internal only).
- Success responses use `application/json` (never `*/*`), except real non-JSON bodies (`text/markdown` documentation).

## Schemas

- Never expose JPA entities, `Map<String, Any>` or protobuf types directly when a typed DTO is feasible.
- Every schema name is unique and meaningful: nested classes get `@Schema(name = "...")`
  (`WorkflowRunDataResponse` → `WorkflowRunData`, never a bare `Data`, `Group`, `Problem` or `Execution`).
- Every DTO field has `@Schema(description, example)`; say the unit (`ms`, `percent 0-100`), format (`uuid`, `date-time`,
  `byte` for base64), enum meaning, and when it is null.
- Status fields are documented enums matching the Kotlin and Temporal values.
- `ByteArray` serializes as base64: `@Schema(type = "string", format = "byte")`.

## Metadata and operations

- One `OpenAPI` bean per service: real title, description of the service's role in Continuum, `version` = the build's
  `platformVersion` (from `BuildProperties`; `springBoot { buildInfo { excludes.set(setOf("time")) } }`), license Apache-2.0
  (`https://www.apache.org/licenses/LICENSE-2.0`), contact `Project Continuum` /
  `https://github.com/projectcontinuum-org/continuum-platform-core`, external docs `https://docs.projectcontinuum.org`.
- Every controller has `@Tag(name, description)` with a human name ("Workflows", not `workflow-controller`).
- Every operation has `@Operation(operationId, summary, description)`:
  - `operationId`: lowerCamelCase verb + noun, unique within the service and stable (it is the generated client method
    name): `startWorkflow`, `listWorkflowRuns`, `getCredential`. Never rename one without a release note.
  - `summary`: under 80 characters, imperative ("Start a workflow run").
  - `description`: what it does, who calls it, side effects (DB rows, Temporal, S3, Kafka, MQTT), idempotency, constraints.
- Every parameter has `@Parameter(description, example)`; every body `@RequestBody(description, content = [Content(examples = ...)])`
  with a realistic example. Content comes from `docs/api/ENDPOINT_INVENTORY.md`, never guesses: unclear behavior is a
  TODO in the inventory.

## Drift guard

- `OpenApiSpecTest` per service boots the full context (external systems mocked; `src/test/resources/application.yml`
  shadows the main one, so the test imports `file:src/main/resources/application.yml` to check the real settings),
  fetches the spec and asserts:
  every request mapping is documented, hidden endpoints are absent from `public` and the ungrouped spec, every operation
  has a summary, a tag and an operationId, operationIds are unique, every operation documents an error response, and the
  title is not the default "OpenAPI definition".
- It compares `/v3/api-docs.yaml/public` with `docs/api/<module>.openapi.yaml` and fails on a difference. The test sets
  `continuum.core.api-docs.server-url=/`, so the committed spec has the relative server URL `/` (valid directly, behind
  `/api-server` or `/credentials-manager`, and for openapi-generator; Redocly rejects localhost URLs). Regenerate with
  `./gradlew :<module>:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec`, then review and commit the diff.
- `redocly lint` (config `redocly.yaml` at the repo root) must pass with no errors and no warnings for every committed
  spec. `no-ambiguous-paths` is off: `/api/v1/workflow/schedule/{scheduleId}` and `/api/v1/workflow/{workflowId}/cancel`
  overlap by shape but Spring resolves them deterministically and the URLs are a published contract.
  `npx --yes @redocly/cli@2 lint docs/api/*.openapi.yaml --config redocly.yaml`.
