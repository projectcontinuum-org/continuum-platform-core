plugins {
    kotlin("jvm") version "2.1.0"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.continuum.core"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson dependencies
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    // Parquet writer
    implementation("org.apache.avro:avro:1.12.0")
    implementation("org.apache.parquet:parquet-avro:1.15.0")
    implementation("org.apache.hadoop:hadoop-common:3.4.1")
    implementation("org.apache.hadoop:hadoop-mapreduce-client-core:3.3.1")

    // Project dependencies
    implementation(project(":continuum-avro-schemas"))

    // Temporal dependencies
    implementation("io.temporal:temporal-sdk")
    implementation("io.temporal:temporal-kotlin")
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
