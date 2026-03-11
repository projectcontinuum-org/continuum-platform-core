# Plan: PostgreSQL-Backed Node Explorer APIs

## Context

The `NodeExplorerController` in `continuum-api-server` currently serves a **hardcoded mock tree** for the node explorer frontend widget. The `registered_nodes` PostgreSQL table (populated by `continuum-message-bridge` during Kafka-based feature registration) already contains most of the needed data — but the `categories` field (used to build the tree hierarchy) is missing from the pipeline. This plan adds `categories` end-to-end and replaces the mock service with real database queries.

---

## Changes by Module (build order)

### 1. `continuum-avro-schemas` — Add `categories` to Avro schema

**File:** `continuum-avro-schemas/src/main/avro/org/projectcontinuum/core/protocol/event/FeatureRegistrationProtocol.avdl`

Add `array<string> categories = [];` to `FeatureRegistrationRequest` (before `extensions`). The default `[]` ensures backward compatibility with existing consumers/producers.

After editing, run `./gradlew :continuum-avro-schemas:build` to regenerate the Java class.

---

### 2. `continuum-worker-springboot-starter` — Publish categories

**File:** `continuum-worker-springboot-starter/.../registration/FeatureRegistrationPublisher.kt`

Add `.setCategories(nodeModel.categories)` to the Avro builder in `buildRegistrationRequest()`. The `ContinuumNodeModel.categories` property already exists and is implemented by all nodes.

---

### 3. `continuum-message-bridge` — Persist categories

**3a. Schema** — `continuum-message-bridge/src/main/resources/schema.sql`
- Add `categories JSONB NOT NULL DEFAULT '[]'` column to `CREATE TABLE`
- Add `ALTER TABLE registered_nodes ADD COLUMN IF NOT EXISTS categories JSONB NOT NULL DEFAULT '[]';` for existing deployments

**3b. Entity** — `continuum-message-bridge/.../entity/RegisteredNodeEntity.kt`
- Add `@Column("categories") val categories: String = "[]"` field

**3c. Repository** — `continuum-message-bridge/.../repository/RegisteredNodeRepository.kt`
- Add `categories` parameter to `upsert()` method and SQL (with `CAST(:categories AS JSONB)`)

**3d. Handler** — `continuum-message-bridge/.../handler/FeatureRegistrationHandler.kt`
- Convert `request.getCategories()` (Avro `List<CharSequence>`) to JSON string via `ObjectMapper.writeValueAsString()`
- Pass `categories = categoriesJson` to `registeredNodeRepository.upsert()`

**3e. Env var rename** — `continuum-message-bridge/src/main/resources/application.yaml`
- Rename `CONTINUUM_BRIDGE_DB_URL` → `CONTINUUM_DB_URL`
- Rename `CONTINUUM_BRIDGE_DB_USERNAME` → `CONTINUUM_DB_USERNAME`
- Rename `CONTINUUM_BRIDGE_DB_PASSWORD` → `CONTINUUM_DB_PASSWORD`
- Both services now share the same env var names for database configuration

---

### 4. `continuum-api-server` — PostgreSQL integration + service rewrite

**4a. Dependencies** — `continuum-api-server/build.gradle.kts`
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
runtimeOnly("org.postgresql:postgresql")
```

**4b. Configuration** — `continuum-api-server/src/main/resources/application.yml`
```yaml
spring:
  datasource:
    url: ${CONTINUUM_DB_URL:jdbc:postgresql://localhost:35432/continuum_bridge}
    username: ${CONTINUUM_DB_USERNAME:temporal}
    password: ${CONTINUUM_DB_PASSWORD:temporal}
    driver-class-name: org.postgresql.Driver
```
Uses service-agnostic env var names (`CONTINUUM_DB_*`) since the registration consumer may be extracted from the bridge into a dedicated service in the future. Reads from the same `continuum_bridge` database. No `spring.sql.init.mode` — only the registration service owns schema creation.

**4c. New entity** — `continuum-api-server/.../entity/RegisteredNodeEntity.kt`
- Duplicate of message-bridge's entity (modules are independent, no shared dependency)
- Maps to `registered_nodes` table with all columns including `categories`

**4d. New repository** — `continuum-api-server/.../repository/RegisteredNodeRepository.kt`
- Five queries:
  - `findAllDistinctCategories()` — `SELECT DISTINCT jsonb_array_elements_text(categories) FROM registered_nodes ORDER BY 1`
  - `findByCategoriesContaining(category)` — `SELECT * FROM registered_nodes WHERE categories @> CAST(:category AS JSONB)` (uses JSONB containment — a node with `["analytics/data", "math/transform"]` is found when querying for `["analytics/data"]`)
  - `findByEmptyCategories()` — `SELECT * FROM registered_nodes WHERE categories = '[]'::jsonb` (root-level nodes with no category)
  - `searchNodes(pattern)` — `SELECT * FROM registered_nodes WHERE node_manifest->>'title' ILIKE :pattern OR node_manifest->>'description' ILIKE :pattern OR node_id ILIKE :pattern`
  - `findAll()` — inherited from `CrudRepository` (used as fallback if needed)

**4e. Rewrite service** — `continuum-api-server/.../service/NodeExplorerService.kt`
- Replace entire mock implementation
- **Multi-parent support**: A node can belong to multiple categories (e.g., `["analytics/data", "math/transform"]`). The same node will appear under each of its categories in the tree. The `@>` JSONB containment query handles this naturally.
- `getChildren("")`:
  - Query all distinct categories, split each on `/`, collect unique root-level segments as CATEGORY items
  - Also query nodes with empty categories `[]` — these appear at the root as NODE items (no parent)
- `getChildren("analytics")`:
  - Find sub-categories: any category starting with `analytics/` → extract next segment (e.g., `analytics/data` → `data`) as CATEGORY items
  - Find leaf nodes: nodes whose categories array contains the exact value `"analytics"` as NODE items
- `getChildren("analytics/data")`:
  - Same pattern: find sub-sub-categories and nodes with exact match `"analytics/data"`
  - Works to arbitrary depth
- `search(query)`: pass `%query%` pattern to `searchNodes()`, deserialize `node_manifest` JSONB into `NodeData`, return as NODE items
- Uses Jackson `ObjectMapper` to deserialize `node_manifest` string into `ContinuumWorkflowModel.NodeData`

**4f. Add documentation endpoint** — `continuum-api-server/.../controller/NodeExplorerController.kt`
- Add `GET /api/v1/node-explorer/nodes/{nodeId}/documentation` — returns the `documentation_markdown` for a specific node
- Returns `String` (plain text/markdown) with `produces = "text/markdown"`
- The `documentation_markdown` is NOT included in tree/search responses since it can be very large
- Repository query: `findByNodeId(nodeId)` — `SELECT documentation_markdown FROM registered_nodes WHERE node_id = :nodeId LIMIT 1`
- Service method: `getDocumentation(nodeId): String?`

**4g. No changes needed to:**
- `NodeExplorerTreeItem.kt` — DTO stays the same

---

## Files Modified/Created

| File | Action |
|------|--------|
| `continuum-avro-schemas/.../FeatureRegistrationProtocol.avdl` | Modify — add `categories` field |
| `continuum-worker-springboot-starter/.../FeatureRegistrationPublisher.kt` | Modify — add `.setCategories()` |
| `continuum-message-bridge/src/main/resources/schema.sql` | Modify — add `categories` column |
| `continuum-message-bridge/.../entity/RegisteredNodeEntity.kt` | Modify — add `categories` field |
| `continuum-message-bridge/.../repository/RegisteredNodeRepository.kt` | Modify — add `categories` to upsert |
| `continuum-message-bridge/.../handler/FeatureRegistrationHandler.kt` | Modify — serialize & pass categories |
| `continuum-message-bridge/src/main/resources/application.yaml` | Modify — rename env vars to `CONTINUUM_DB_*` |
| `continuum-api-server/build.gradle.kts` | Modify — add JDBC + PostgreSQL deps |
| `continuum-api-server/src/main/resources/application.yml` | Modify — add datasource config |
| `continuum-api-server/.../entity/RegisteredNodeEntity.kt` | **Create** |
| `continuum-api-server/.../repository/RegisteredNodeRepository.kt` | **Create** |
| `continuum-api-server/.../service/NodeExplorerService.kt` | Modify — full rewrite |
| `continuum-api-server/.../controller/NodeExplorerController.kt` | Modify — add documentation endpoint |

---

## Verification

1. **Build all modules**: `./gradlew build` from monorepo root — confirms compilation and Avro codegen
2. **Start infrastructure**: `docker compose -f docker/docker-compose.yml up -d` (Postgres, Kafka, Schema Registry, etc.)
3. **Start message-bridge**: verify `categories` column is created in `registered_nodes` table
4. **Start a feature worker** (e.g., continuum-feature-base worker): verify Kafka messages include `categories` and the bridge persists them — check via `psql` query: `SELECT node_id, categories FROM registered_nodes;`
5. **Start api-server**: verify endpoints return real data
   - `curl http://localhost:8080/api/v1/node-explorer/children` — should return categories from DB
   - `curl http://localhost:8080/api/v1/node-explorer/children?parentId=Processing` — should return nodes
   - `curl http://localhost:8080/api/v1/node-explorer/search?query=table` — should find matching nodes
   - `curl http://localhost:8080/api/v1/node-explorer/nodes/org.projectcontinuum.feature.base.analytics.node.CreateTableNodeModel/documentation` — should return markdown documentation
6. **Open workbench frontend**: verify node explorer widget displays the tree correctly
