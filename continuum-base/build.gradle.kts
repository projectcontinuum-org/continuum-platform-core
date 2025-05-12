plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "1.9.25"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.continuum.base"
version = "1.0.0"

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
    // Springboot dependencies
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Spring Cloud stream dependencies
    implementation("io.confluent:kafka-avro-serializer:7.6.1")
    implementation("io.confluent:kafka-schema-registry-client:7.6.1")
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")

    // Project dependencies
    implementation(project(":continuum-commons"))
    implementation(project(":continuum-avro-schemas"))

    // Jackson dependencies
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    // Temporal dependency
    implementation("io.temporal:temporal-sdk")
    implementation("io.temporal:temporal-kotlin")
    implementation("io.temporal:temporal-spring-boot-starter")

    // MQTT dependencies
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // AWS dependencies
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:sso")
    implementation("software.amazon.awssdk:ssooidc")
    implementation("software.amazon.awssdk.crt:aws-crt:0.33.10")
    implementation("software.amazon.awssdk:s3-transfer-manager")

    // Nodes
    implementation(project(":continuum-knime-base"))

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
