plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("org.jreleaser")
}

group = "org.projectcontinuum.core"
description = "Continuum Gradle Plugin — convention plugins for Continuum feature and worker modules"
val baseVersion = properties["platformVersion"].toString()
val isRelease = System.getenv("IS_RELEASE_BUILD")?.toBoolean() ?: false
version = if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Plugin APIs — these are the plugins our convention plugins apply programmatically
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.25")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.6")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.4.1")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.4.1")
    implementation("org.jreleaser:jreleaser-gradle-plugin:1.23.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("continuumFeature") {
            id = "org.projectcontinuum.feature"
            implementationClass = "org.projectcontinuum.gradle.ContinuumFeaturePlugin"
            displayName = "Continuum Feature Plugin (Kotlin)"
            description = "Convention plugin for Continuum Kotlin feature modules — applies Spring Boot, Kotlin, dependency management, and publishing"
        }
        create("continuumFeatureJava") {
            id = "org.projectcontinuum.feature-java"
            implementationClass = "org.projectcontinuum.gradle.ContinuumFeatureJavaPlugin"
            displayName = "Continuum Feature Plugin (Java)"
            description = "Convention plugin for Continuum Java feature modules — applies Spring Boot, java-library, dependency management, and publishing"
        }
        create("continuumWorker") {
            id = "org.projectcontinuum.worker"
            implementationClass = "org.projectcontinuum.gradle.ContinuumWorkerPlugin"
            displayName = "Continuum Worker Plugin (Kotlin)"
            description = "Convention plugin for Continuum Kotlin worker modules — extends feature plugin with Spring Boot app, Jib, and worker starter"
        }
        create("continuumWorkerJava") {
            id = "org.projectcontinuum.worker-java"
            implementationClass = "org.projectcontinuum.gradle.ContinuumWorkerJavaPlugin"
            displayName = "Continuum Worker Plugin (Java)"
            description = "Convention plugin for Continuum Java worker modules — extends Java feature plugin with Spring Boot app, Jib, and worker starter"
        }
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
