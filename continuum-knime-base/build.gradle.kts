plugins {
    kotlin("jvm") version "2.1.0"
    id("io.spring.dependency-management") version "1.1.6"
    `maven-publish`
    id("org.jreleaser")
}

group = "org.projectcontinuum.knime"
description = "Continuum KNIME Base — KNIME node compatibility layer"
val baseVersion = properties["platformVersion"].toString()
val isRelease = System.getenv("IS_RELEASE_BUILD")?.toBoolean() ?: false
version = if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withJavadocJar()
    withSourcesJar()
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
    val repoName = System.getenv("GITHUB_REPOSITORY") ?: property("repoName").toString()
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            group = project.group
            description = project.description
            version = project.version.toString()
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/$repoName")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("continuum-developer")
                        name.set("Continuum Developer")
                        email.set("projectdevcontinuum@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/$repoName.git")
                    developerConnection.set("scm:git:ssh://github.com/$repoName.git")
                    url.set("https://github.com/$repoName")
                }
            }
        }
    }
    repositories {
        maven {
            name = "localStaging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
        if (version.toString().endsWith("-SNAPSHOT")) {
            maven {
                name = "SonatypeSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                credentials {
                    username = System.getenv("MAVEN_REPO_USERNAME") ?: ""
                    password = System.getenv("MAVEN_REPO_PASSWORD") ?: ""
                }
            }
        }
    }
}

jreleaser {
    signing {
        active.set(org.jreleaser.model.Active.ALWAYS)
        armored.set(true)
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(org.jreleaser.model.Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                    skipPublicationCheck.set(false)
                    retryDelay.set(0)
                    maxRetries.set(0)
                }
            }
        }
    }
}
