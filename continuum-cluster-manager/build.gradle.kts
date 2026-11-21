import com.github.gradle.node.npm.task.NpmTask

plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot") version "4.0.6"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.google.cloud.tools.jib") version "3.4.4"
  id("com.github.node-gradle.node") version "3.2.1"
}

group = "org.projectcontinuum.core"
description = "Continuum cluster management API Server — REST API for managing and monitoring continuum cluster resources"
version = property("platformVersion").toString()

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // Swagger-UI Dependencies (springdoc 3.0.x = Spring Boot 4.0.x; version in gradle/libs.versions.toml)
  implementation(libs.springdoc.openapi.starter.webmvc.ui)

  // Kubernetes client
  implementation("io.fabric8:kubernetes-client:7.7.0")

  // Template engine for K8s manifests
  implementation("org.freemarker:freemarker:2.3.34")

  // JSON. HTTP bodies are (de)serialized by Jackson 3 (tools.jackson); its Kotlin module makes Kotlin defaults apply
  // to missing fields (e.g. a partial `resources` object) and turns a missing required field into a 400.
  implementation("tools.jackson.module:jackson-module-kotlin")
  // Jackson 2 Kotlin module: still used by WorkbenchService/OverlayService (audit JSON, YAML) and by springdoc.
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  // Database
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.postgresql:postgresql")

  // Caching
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("com.github.ben-manes.caffeine:caffeine")

  // Test dependencies
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
  testImplementation("io.fabric8:kubernetes-server-mock:7.7.0")
  testImplementation("io.fabric8:mockwebserver:7.7.0")
  testImplementation("com.h2database:h2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// META-INF/build-info.properties: the OpenAPI `info.version` is the build's platformVersion.
// No timestamp, so the generated spec (docs/api/continuum-cluster-manager.openapi.yaml) is reproducible.
springBoot {
  buildInfo {
    excludes.set(setOf("time"))
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

// The committed OpenAPI spec that OpenApiSpecTest compares the generated one with.
// Regenerate after an intended API change: ./gradlew :continuum-cluster-manager:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec
val committedOpenApiSpec = rootProject.file("docs/api/${project.name}.openapi.yaml")

tasks.withType<Test> {
  useJUnitPlatform()
  systemProperty("continuum.openapi.spec-file", committedOpenApiSpec.absolutePath)
  systemProperty("continuum.openapi.update-spec", project.hasProperty("updateOpenApiSpec").toString())
  // Optional: -PdumpOpenApiSpecs=<dir> writes every fetched spec as JSON, for inspection while editing annotations.
  findProperty("dumpOpenApiSpecs")?.let { dumpDir -> systemProperty("continuum.openapi.dump-dir", dumpDir.toString()) }
  // A changed committed spec re-runs the tests (on the first generation the file does not exist yet).
  inputs.files(fileTree(committedOpenApiSpec.parentFile) { include(committedOpenApiSpec.name) })
    .withPropertyName("committedOpenApiSpec")
  if (project.hasProperty("updateOpenApiSpec")) {
    outputs.upToDateWhen { false }
  }
}

// ============================================================================
// Frontend build tasks (uses node-gradle plugin to auto-download Node.js)
// ============================================================================
node {
  version.set("22.12.0")
  download.set(true)
  nodeProjectDir.set(file("frontend"))
}

val frontendDist = file("frontend/dist")

val frontendInstall by tasks.registering(NpmTask::class) {
  description = "Install frontend npm dependencies"
  group = "frontend"
  args.set(listOf("install"))
  inputs.file(file("frontend/package.json"))
  outputs.dir(file("frontend/node_modules"))
}

val frontendBuild by tasks.registering(NpmTask::class) {
  description = "Build the frontend (Vite + React)"
  group = "frontend"
  dependsOn(frontendInstall)
  args.set(listOf("run", "build"))
  inputs.dir(file("frontend/src"))
  inputs.file(file("frontend/index.html"))
  inputs.file(file("frontend/vite.config.ts"))
  inputs.file(file("frontend/tsconfig.json"))
  inputs.file(file("frontend/tailwind.config.js"))
  inputs.file(file("frontend/postcss.config.js"))
  outputs.dir(frontendDist)
}

val copyFrontend by tasks.registering(Copy::class) {
  description = "Copy built frontend into Spring Boot static resources"
  group = "frontend"
  dependsOn(frontendBuild)
  from(frontendDist)
  into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("processResources") {
  dependsOn(copyFrontend)
}

val frontendClean by tasks.registering(Delete::class) {
  description = "Clean frontend build output"
  group = "frontend"
  delete(frontendDist)
}

tasks.named("clean") {
  dependsOn(frontendClean)
}

jib {
  from {
    image = "eclipse-temurin:21-jre"
  }

  to {
    image = "docker.io/projectcontinuum/${project.name.lowercase()}:${project.version}"
    auth {
      username = System.getenv("DOCKER_REPO_USERNAME") ?: ""
      password = System.getenv("DOCKER_REPO_PASSWORD") ?: ""
    }
  }
}
