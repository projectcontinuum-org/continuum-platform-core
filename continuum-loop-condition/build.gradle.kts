plugins {
    kotlin("jvm")
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.projectcontinuum.core"
description = "Continuum Loop Condition — cel-java implementation of the Loop condition contract (continuum-commons validation.loop)"
version = property("platformVersion").toString()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Not published. Only continuum-orchestration-service and continuum-api-server depend on this module: cel-java brings
// protobuf-java 4.x and guava 33.x, which must not reach continuum-commons, feature workers or continuum-knime-base.
dependencies {
    // The contract this module implements (LoopConditionValidator, LoopConditionEvaluator, issue codes) is part of its API.
    api(project(":continuum-commons"))

    // Google cel-java: the CEL validator and evaluator. Its types never appear in this module's API.
    implementation(libs.cel)

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Reads the CEL condition table (src/test/resources/loop-condition/cel-conditions.json)
    testImplementation("tools.jackson.module:jackson-module-kotlin")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
        // Versions of continuum-commons' own dependencies (Temporal), the same BOM commons builds with
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
