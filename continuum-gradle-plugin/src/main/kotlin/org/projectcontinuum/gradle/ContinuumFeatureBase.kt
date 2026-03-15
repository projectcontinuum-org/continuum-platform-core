package org.projectcontinuum.gradle

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.jreleaser.gradle.plugin.JReleaserPlugin
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active

/**
 * Shared base logic for both Kotlin and Java feature plugins.
 * Not applied directly — used by [ContinuumFeaturePlugin] and [ContinuumFeatureJavaPlugin].
 */
object ContinuumFeatureBase {

    const val EXTENSION_NAME = "continuum"

    // Default versions — matching the current ecosystem
    const val DEFAULT_CONTINUUM_VERSION = "0.0.6"
    const val DEFAULT_CONTINUUM_GROUP = "org.projectcontinuum.core"
    const val DEFAULT_SPRING_BOOT_VERSION = "3.4.1"
    const val DEFAULT_SPRING_CLOUD_VERSION = "2024.0.0"
    const val DEFAULT_TEMPORAL_VERSION = "1.28.0"
    const val DEFAULT_AWS_SDK_VERSION = "2.30.7"
    const val DEFAULT_JACKSON_VERSION = "2.18.2"

    /**
     * Creates and configures the shared ContinuumExtension if it doesn't already exist.
     */
    fun getOrCreateExtension(project: Project, isKotlin: Boolean): ContinuumExtension {
        val existing = project.extensions.findByType<ContinuumExtension>()
        if (existing != null) return existing

        return project.extensions.create<ContinuumExtension>(EXTENSION_NAME).apply {
            continuumVersion.convention(DEFAULT_CONTINUUM_VERSION)
            continuumGroup.convention(DEFAULT_CONTINUUM_GROUP)
            useKotlin.convention(isKotlin)
            springBootVersion.convention(DEFAULT_SPRING_BOOT_VERSION)
            springCloudVersion.convention(DEFAULT_SPRING_CLOUD_VERSION)
            temporalVersion.convention(DEFAULT_TEMPORAL_VERSION)
            awsSdkVersion.convention(DEFAULT_AWS_SDK_VERSION)
            jacksonVersion.convention(DEFAULT_JACKSON_VERSION)
            publishToMavenCentral.convention(true)
        }
    }

    /**
     * Apply language-neutral plugins and configuration shared between Java and Kotlin.
     */
    fun applyCommonPlugins(project: Project) {
        project.pluginManager.apply(DependencyManagementPlugin::class.java)
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply(JReleaserPlugin::class.java)
    }

    /**
     * Configure Java toolchain, repos, JUnit, and the annotation validation task.
     */
    fun configureCommon(project: Project) {
        // Configure Java toolchain
        project.extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }

        // Configure repositories
        project.repositories.apply {
            mavenLocal()
            mavenCentral()
            maven { url = project.uri("https://packages.confluent.io/maven/") }
        }

        // Configure JUnit platform
        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        // Register ContinuumNode annotation validation task
        registerNodeAnnotationValidation(project)
    }

    /**
     * Configure dependencies and BOMs (called in afterEvaluate so extension values are resolved).
     */
    fun configureDependencies(project: Project, ext: ContinuumExtension) {
        val continuumVer = ext.continuumVersion.get()
        val continuumGrp = ext.continuumGroup.get()
        val jacksonVer = ext.jacksonVersion.get()
        val springBootVer = ext.springBootVersion.get()
        val temporalVer = ext.temporalVersion.get()
        val springCloudVer = ext.springCloudVersion.get()
        val isKotlin = ext.useKotlin.get()

        // Import BOMs via Spring dependency management
        project.extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVer")
                mavenBom("io.temporal:temporal-bom:$temporalVer")
                mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVer")
            }
        }

        // Add implementation dependencies
        project.dependencies.apply {
            add("implementation", "org.springframework.boot:spring-boot-starter")
            add("implementation", "org.springframework.boot:spring-boot-autoconfigure")
            add("implementation", "org.springframework.boot:spring-boot-starter-web")
            add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
            add("implementation", "$continuumGrp:continuum-commons:$continuumVer")

            if (isKotlin) {
                add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
                add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVer")
            } else {
                add("implementation", "com.fasterxml.jackson.core:jackson-databind:$jacksonVer")
            }

            // Test dependencies
            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")

            if (isKotlin) {
                add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
                add("testImplementation", "org.mockito.kotlin:mockito-kotlin:5.3.1")
            } else {
                add("testImplementation", "org.junit.jupiter:junit-jupiter")
                add("testImplementation", "org.mockito:mockito-core:5.11.0")
            }
        }
    }

    /**
     * Configure Maven publishing and JReleaser.
     */
    fun configurePublishingAndRelease(project: Project, ext: ContinuumExtension) {
        configurePublishing(project)
        if (ext.publishToMavenCentral.get()) {
            configureJReleaser(project)
        }
    }

    private fun configurePublishing(project: Project) {
        val repoName = System.getenv("GITHUB_REPOSITORY")
            ?: project.findProperty("repoName")?.toString()
            ?: ""

        project.extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(project.components.getByName("java"))
                    groupId = project.group.toString()
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
                    url = project.uri(project.layout.buildDirectory.dir("staging-deploy"))
                }
                if (project.version.toString().endsWith("-SNAPSHOT")) {
                    maven {
                        name = "SonatypeSnapshots"
                        url = project.uri("https://central.sonatype.com/repository/maven-snapshots/")
                        credentials {
                            username = System.getenv("MAVEN_REPO_USERNAME") ?: ""
                            password = System.getenv("MAVEN_REPO_PASSWORD") ?: ""
                        }
                    }
                }
            }
        }
    }

    private fun configureJReleaser(project: Project) {
        project.extensions.configure<JReleaserExtension> {
            signing {
                active.set(Active.ALWAYS)
                armored.set(true)
            }
            deploy {
                maven {
                    mavenCentral {
                        create("sonatype") {
                            active.set(Active.ALWAYS)
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
    }

    private fun registerNodeAnnotationValidation(project: Project) {
        val validateTask = project.tasks.register(
            "validateContinuumNodeAnnotations",
            ValidateContinuumNodeAnnotationTask::class.java
        ) {
            compilationClasspath.from(
                project.configurations.named("compileClasspath")
            )
        }

        // Wire up after the appropriate compile task produces output
        project.afterEvaluate {
            val ext = project.extensions.getByType<ContinuumExtension>()
            val isKotlin = ext.useKotlin.get()

            validateTask.configure {
                if (isKotlin) {
                    val compileKotlin = project.tasks.findByName("compileKotlin")
                    if (compileKotlin != null) {
                        dependsOn(compileKotlin)
                    }
                    classesDir.set(project.layout.buildDirectory.dir("classes/kotlin/main"))
                } else {
                    val compileJava = project.tasks.findByName("compileJava")
                    if (compileJava != null) {
                        dependsOn(compileJava)
                    }
                    classesDir.set(project.layout.buildDirectory.dir("classes/java/main"))
                }
            }
        }

        // Insert validation before classes
        project.tasks.named("classes") {
            dependsOn(validateTask)
        }
    }
}
