## Purpose

Convention Gradle plugins for feature modules and worker apps. Centralises dependency management, publishing configuration, Jib setup, and the `@ContinuumNode` annotation validation task across all feature repos.

## Ownership

Core platform team. Published to Maven Central as `org.projectcontinuum.core:continuum-gradle-plugin`.

## Local Contracts

- `org.projectcontinuum.feature` (`ContinuumFeaturePlugin`) — Kotlin library: applies kotlin-jvm, kotlin-spring, dependency-management, maven-publish, JReleaser; adds Spring Boot starters, continuum-commons, Jackson, kotlin-reflect, BOMs.
- `org.projectcontinuum.worker` (`ContinuumWorkerPlugin`) — extends the feature plugin; additionally applies `org.springframework.boot` and Jib; adds AWS SDK BOM and `continuum-worker-springboot-starter`.
- `org.projectcontinuum.feature.java` (`ContinuumFeatureJavaPlugin`) — same as feature but for Java (no Kotlin compiler setup).
- `org.projectcontinuum.worker.java` (`ContinuumWorkerJavaPlugin`) — worker variant for Java.
- `ContinuumExtension` — DSL block `continuum { }` exposing: `continuumVersion`, `continuumGroup`, `springBootVersion`, `springCloudVersion`, `temporalVersion`, `awsSdkVersion`, `jacksonVersion`, `publishToMavenCentral`.
- `ValidateContinuumNodeAnnotationTask` — wired before `classes` task. Scans compiled `.class` files for `ProcessNodeModel`/`TriggerNodeModel` subclasses missing `@ContinuumNode`. Fails the build if any are found.
- Publishing target: Maven Central via JReleaser (staging to `build/staging-deploy`, then JReleaser deploy). SNAPSHOT versions also publish to Sonatype Snapshots.
- Container images: Jib to `docker.io/{repoName}:{version}`. `repoName` from `GITHUB_REPOSITORY` env or `gradle.properties`.

## Work Guidance

- The plugin uses `afterEvaluate` to read `continuum { }` extension values — changes to extension defaults take effect in the next evaluation, not immediately in `apply()`.
- `DEFAULT_*` constants define current ecosystem defaults. Update them when bumping platform-wide versions.
- Tests (`ContinuumFeaturePluginTest`, `ContinuumWorkerPluginTest`) use Gradle TestKit — they apply the plugin to a temporary project and verify task outcomes.

## Verification

```bash
./gradlew :continuum-gradle-plugin:test
```
