import com.github.gradle.node.npm.task.NpmTask

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
    id("com.github.node-gradle.node") version "3.2.1"
}

group = "org.projectcontinuum.core"
description = "Continuum KNIME Scheduler — object-store CRUD proxy for KNIME workflow (.knwf) files"
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
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Swagger-UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // PostgreSQL / JPA bookkeeping
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql")

    // AWS S3 (minio + aws-s3 backends)
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:2.30.7")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-java-parameters")
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
    inputs.file(file("frontend/package-lock.json"))
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
