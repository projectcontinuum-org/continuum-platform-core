plugins {
  kotlin("jvm") version "2.1.0"
  kotlin("plugin.spring") version "1.9.25"
  id("org.springframework.boot") version "3.4.0"
  id("io.spring.dependency-management") version "1.1.6"
  id("com.google.cloud.tools.jib") version "3.4.1"
}

group = "org.projectcontinuum.core"
description = "Continuum Cloud Gateway — reverse proxy for workbench instances, scalable independently"
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
  implementation("org.springframework.boot:spring-boot-starter-websocket")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // JSON
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  // Spring Cloud Gateway Server MVC (servlet-based reverse proxy)
  implementation("org.springframework.cloud:spring-cloud-gateway-server-mvc")

  // Database (read-only access to workbench_instances for endpoint resolution)
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.postgresql:postgresql")

  // Test dependencies
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("com.h2database:h2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.1")
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
