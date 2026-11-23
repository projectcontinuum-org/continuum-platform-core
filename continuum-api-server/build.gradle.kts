plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
}

group = "org.projectcontinuum.core"
description = "Continuum API Server — REST API for workflow management and execution"
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

    // Project dependencies
    implementation(project(":continuum-commons"))

    // Swagger-UI Dependencies (springdoc 3.0.x = Spring Boot 4.0.x; version in gradle/libs.versions.toml)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    // springdoc/swagger-core read models with Jackson 2. Its Kotlin module makes non-null fields `required`
    // (version from the Spring Boot BOM; the app itself serializes with Jackson 3, tools.jackson).
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Temporal Dependencies
    implementation("io.temporal:temporal-sdk")
    implementation("io.temporal:temporal-kotlin")

    // MCP server (Spring AI) — exposes the continuum node registry as MCP tools/resources
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // DuckDB Dependencies
    implementation("org.duckdb:duckdb_jdbc:1.2.2.0")

    // PostgreSQL Dependencies
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql")

    // Hibernate JSONB support (native Hibernate 7 @JdbcTypeCode used instead)

    // RSQL query support
    implementation("io.github.perplexhub:rsql-jpa-spring-boot-starter:6.0.27")

    // AWS dependencies
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:sso")
    implementation("software.amazon.awssdk:ssooidc")
    implementation("software.amazon.awssdk.crt:aws-crt:0.33.10")
    implementation("software.amazon.awssdk:s3-transfer-manager")

    // Spring cache abstraction + Caffeine: short-lived cache of node registry schemas (versions from the Spring Boot BOM)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    // In-memory database for OpenApiSpecTest, which boots the full context without PostgreSQL
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// META-INF/build-info.properties: the OpenAPI `info.version` is the build's platformVersion.
// No timestamp, so the generated spec (docs/api/continuum-api-server.openapi.yaml) is reproducible.
springBoot {
    buildInfo {
        excludes.set(setOf("time"))
    }
}

dependencyManagement {
    imports {
        mavenBom("io.temporal:temporal-bom:1.28.0")
        mavenBom("software.amazon.awssdk:bom:2.30.7")
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-java-parameters")
    }
}

// The committed OpenAPI spec that OpenApiSpecTest compares the generated one with.
// Regenerate after an intended API change: ./gradlew :continuum-api-server:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec
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

// `check` (so `build`) regenerates the committed spec first: see gradle/openapi-spec.gradle.kts.
apply(from = rootProject.file("gradle/openapi-spec.gradle.kts"))

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
