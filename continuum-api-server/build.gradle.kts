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

    // Swagger-UI Dependencies
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // Temporal Dependencies
    implementation("io.temporal:temporal-sdk")
    implementation("io.temporal:temporal-kotlin")

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

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("io.temporal:temporal-bom:1.28.0")
        mavenBom("software.amazon.awssdk:bom:2.30.7")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
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
