plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
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
