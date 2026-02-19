plugins {
    kotlin("jvm") version "2.1.0"
    id("io.spring.dependency-management") version "1.1.6"
    `maven-publish`
}

group = "com.continuum.knime"
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

    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Project dependencies
    implementation(project(":continuum-commons"))

    // Jackson dependencies
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.0")
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            group = project.group
            description = project.description
            version = project.version.toString()
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/roushan65/Continuum")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/roushan65/Continuum")
            credentials {
                username = System.getenv("MAVEN_REPO_USERNAME")
                password = System.getenv("MAVEN_REPO_PASSWORD")
            }
        }
    }
}
