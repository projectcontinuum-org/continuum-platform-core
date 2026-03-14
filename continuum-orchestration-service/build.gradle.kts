plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.0"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.google.cloud.tools.jib") version "3.4.1"
}

group = "org.projectcontinuum.core"
description = "Continuum Orchestration Service — standalone workflow orchestration engine for DAG execution"
val baseVersion = property("platformVersion").toString()
val isRelease = System.getenv("IS_RELEASE_BUILD")?.toBoolean() ?: false
version = if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Spring Cloud Stream dependencies
    implementation("io.confluent:kafka-avro-serializer:7.6.1")
    implementation("io.confluent:kafka-schema-registry-client:7.6.1")
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")

    // Project dependencies
    implementation(project(":continuum-commons"))
    implementation(project(":continuum-avro-schemas"))

    // Jackson dependencies
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    // Temporal dependencies
    implementation("io.temporal:temporal-sdk")
    implementation("io.temporal:temporal-kotlin")
    implementation("io.temporal:temporal-spring-boot-starter")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.temporal:temporal-testing")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.springframework.cloud:spring-cloud-stream-test-support")
    testImplementation("org.springframework.cloud:spring-cloud-stream-test-binder")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
        mavenBom("io.temporal:temporal-bom:1.28.0")
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
