# Feature Registration System

## Overview

Workers publish their available node models to a Kafka topic at startup using Avro binary serialization. The `continuum-message-bridge` consumes these messages and upserts them into a PostgreSQL database, creating a centralized registry of all nodes available across all task queues.

```
┌──────────────────────┐      Avro binary       ┌──────────────────────┐       PostgreSQL
│  Worker (startup)    │ ──── Kafka topic ─────► │  Message Bridge      │ ────► registered_nodes
│                      │  FeatureRegistration    │                      │       (nodeId + taskQueue)
│  Discovers all       │                         │  Consumes & upserts  │
│  ContinuumNodeModel  │                         │  per node            │
│  beans via Spring    │                         │                      │
└──────────────────────┘                         └──────────────────────┘
```

---

## AVRO Schema

**File:** `continuum-avro-schemas/src/main/avro/com/continuum/core/event/FeatureRegistrationProtocol.avdl`

```avdl
@namespace("org.projectcontinuum.core.event")
protocol FeatureRegistrationProtocol {

    record FeatureRegistrationRequest {
        string nodeId;
        string workerId;
        string featureId;
        string taskQueue;
        string nodeManifest;
        string documentationMarkdown;
        @logicalType("timestamp-millis")
        long registeredAtTimestampUtc;
        map<string> extensions = {};
    }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | `string` | Fully qualified class name (e.g., `org.projectcontinuum.feature.analytics.node.CreateTableNodeModel`) |
| `workerId` | `string` | Unique worker instance ID (`worker-{UUID}`), changes on each restart |
| `featureId` | `string` | Root package of the feature module (e.g., `org.projectcontinuum.feature.analytics`) |
| `taskQueue` | `string` | Temporal activity task queue name (default: `ACTIVITY_TASK_QUEUE`) |
| `nodeManifest` | `string` | Full `NodeData` metadata serialized as JSON string (title, description, icon, ports, schemas) |
| `documentationMarkdown` | `string` | Node documentation markup text (always present) |
| `registeredAtTimestampUtc` | `timestamp-millis` | Registration timestamp |
| `extensions` | `map<string>` | Reserved for future use without breaking schema compatibility |

**Design decisions:**
- `nodeManifest` is an opaque JSON string rather than typed Avro fields — forward-compatible with any `NodeData` changes
- One message per node (not batched) for clean idempotent upsert semantics
- Avro binary serialization via Schema Registry (not JSON) on the `continuum-core-event-FeatureRegistration` topic

---

## Producer — `continuum-worker-springboot-starter`

**File:** `continuum-worker-springboot-starter/src/main/kotlin/com/continuum/core/worker/registration/FeatureRegistrationPublisher.kt`

### Behavior

1. Listens on `ApplicationReadyEvent` (fires after all beans are initialized and StreamBridge is ready)
2. Iterates all `ContinuumNodeModel` beans via `ObjectProvider<ContinuumNodeModel>`
3. For each node, builds a `FeatureRegistrationRequest` (Avro-generated class) and publishes to Kafka
4. Kafka message key = `nodeId` for deterministic partition assignment

### featureId Derivation

Strips the trailing `.node` package segment from the node's fully qualified class name:

```
org.projectcontinuum.feature.analytics.node.CreateTableNodeModel → org.projectcontinuum.feature.analytics
org.projectcontinuum.feature.ai.node.UnslothTrainerNodeModel     → org.projectcontinuum.feature.ai
org.projectcontinuum.feature.template.node.ColumnJoinerNodeModel  → org.projectcontinuum.feature.template
```

### Kafka Binding Configuration

```yaml
# spring.cloud.stream.bindings
continuum-core-event-FeatureRegistration-output:
  binder: main-kafka-cluster
  destination: continuum-core-event-FeatureRegistration
  content-type: application/*+avro
  producer:
    use-native-encoding: true

# spring.cloud.stream.kafka.bindings (per-binding override)
continuum-core-event-FeatureRegistration-output:
  producer:
    configuration:
      value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      schema.registry.url: ${CONTINUUM_KAFKA_MAIN_SCHEMA_REGISTRY}
```

---

## Consumer — `continuum-message-bridge`

**File:** `continuum-message-bridge/src/main/kotlin/com/continuum/core/bridge/handler/FeatureRegistrationHandler.kt`

### Behavior

1. Non-reactive `Consumer<Message<FeatureRegistrationRequest>>` (Spring Cloud Stream functional consumer)
2. Deserializes Avro binary messages using `KafkaAvroDeserializer` + Schema Registry
3. Calls `RegisteredNodeRepository.upsert()` — a single atomic `INSERT ... ON CONFLICT DO UPDATE` query

### Kafka Binding Configuration

```yaml
# spring.cloud.stream.bindings
continuum-core-event-FeatureRegistration-input-in-0:
  binder: main-kafka-cluster
  destination: continuum-core-event-FeatureRegistration
  content-type: application/*+avro
  group: ${spring.application.name}
  consumer:
    use-native-decoding: true

# spring.cloud.stream.kafka.bindings (per-binding override)
continuum-core-event-FeatureRegistration-input-in-0:
  consumer:
    configuration:
      value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      schema.registry.url: ${CONTINUUM_KAFKA_MAIN_SCHEMA_REGISTRY:http://localhost:38080}
      specific.avro.reader: true
```

---

## Database Schema

**Table:** `registered_nodes` on PostgreSQL database `continuum_bridge` (port 35432)

```sql
CREATE TABLE IF NOT EXISTS registered_nodes (
    id                      BIGSERIAL PRIMARY KEY,
    node_id                 VARCHAR(500) NOT NULL,
    task_queue              VARCHAR(255) NOT NULL,
    worker_id               VARCHAR(255) NOT NULL,
    feature_id              VARCHAR(500) NOT NULL,
    node_manifest           JSONB NOT NULL,
    documentation_markdown  TEXT NOT NULL,
    extensions              JSONB NOT NULL DEFAULT '{}',
    registered_at           TIMESTAMP NOT NULL,
    last_seen_at            TIMESTAMP NOT NULL,
    UNIQUE (node_id, task_queue)
);
```

- **Primary key:** Surrogate `id` (auto-generated)
- **Upsert key:** `UNIQUE (node_id, task_queue)` — same node on same task queue overwrites; same node on different task queues creates separate rows
- **JSONB columns:** `node_manifest` and `extensions` are queryable with PostgreSQL JSON operators (e.g., `node_manifest->>'title'`)
- **`last_seen_at`:** Updated on every registration — useful for detecting stale registrations

### Upsert Query

```sql
INSERT INTO registered_nodes (node_id, task_queue, worker_id, feature_id, ...)
VALUES (:nodeId, :taskQueue, :workerId, :featureId, ...)
ON CONFLICT (node_id, task_queue) DO UPDATE SET
  worker_id = :workerId,
  feature_id = :featureId,
  node_manifest = CAST(:nodeManifest AS JSONB),
  ...
  last_seen_at = :lastSeenAt
```

---

## Infrastructure Changes

### Docker Compose

The existing PostgreSQL instance (used by Temporal) now also hosts the `continuum_bridge` database. An init script is mounted:

```yaml
postgresql:
  volumes:
    - ./postgres/init-bridge-db.sh:/docker-entrypoint-initdb.d/init-bridge-db.sh
```

> **Note:** Init scripts only run on first container start (empty volume). For existing deployments, run manually: `CREATE DATABASE continuum_bridge;`

### New Dependencies — `continuum-message-bridge`

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
runtimeOnly("org.postgresql:postgresql")
implementation(project(":continuum-avro-schemas"))
```

---

## File Inventory

### New Files

| File | Module | Purpose |
|------|--------|---------|
| `continuum-avro-schemas/.../FeatureRegistrationProtocol.avdl` | avro-schemas | Avro schema definition |
| `continuum-worker-springboot-starter/.../FeatureRegistrationPublisher.kt` | worker-starter | Publishes registrations on startup |
| `continuum-message-bridge/.../entity/RegisteredNodeEntity.kt` | message-bridge | Spring Data JDBC entity |
| `continuum-message-bridge/.../repository/RegisteredNodeRepository.kt` | message-bridge | Repository with native upsert |
| `continuum-message-bridge/.../handler/FeatureRegistrationHandler.kt` | message-bridge | Kafka consumer → DB upsert |
| `continuum-message-bridge/src/main/resources/schema.sql` | message-bridge | DDL for `registered_nodes` table |
| `docker/postgres/init-bridge-db.sh` | docker | Creates `continuum_bridge` database |

### Modified Files

| File | Change |
|------|--------|
| `continuum-commons/.../event/Channels.kt` | Added `CONTINUUM_FEATURE_REGISTRATION_OUTPUT` channel constant |
| `continuum-worker-springboot-starter/.../application.yaml` | Added Avro output binding + `KafkaAvroSerializer` config |
| `continuum-message-bridge/build.gradle.kts` | Added `data-jdbc`, `postgresql`, `avro-schemas` dependencies |
| `continuum-message-bridge/.../application.yaml` | Added Avro input binding, `KafkaAvroDeserializer` config, datasource |
| `continuum-message-bridge/.../WorkflowExecutionSnapshotHandler.kt` | Migrated from reactive to non-reactive consumer |
| `docker/docker-compose.yml` | Mounted PostgreSQL init script |

---

## Verification Checklist

1. `docker compose up` — verify `continuum_bridge` database is created
2. `./gradlew build` — all modules compile
3. Start message-bridge — verify PostgreSQL connection and `registered_nodes` table creation
4. Start any worker — check logs for `"Publishing feature registrations"` and per-node `"Published feature registration for node:"`
5. Kafka UI (port 38082) — verify messages on `continuum-core-event-FeatureRegistration` topic
6. Message-bridge logs — verify `"Upserted registered node:"` messages
7. Query database:
   ```sql
   SELECT node_id, task_queue, feature_id, node_manifest->>'title' AS title
   FROM registered_nodes;
   ```
8. Restart worker — verify idempotent upsert (no duplicate rows, `last_seen_at` updated)
