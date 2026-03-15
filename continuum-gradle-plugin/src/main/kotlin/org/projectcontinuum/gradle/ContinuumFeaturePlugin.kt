package org.projectcontinuum.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

/**
 * Convention plugin for Continuum **Kotlin** feature modules.
 *
 * Applies: kotlin-jvm, kotlin-spring, spring-dependency-management, maven-publish, jreleaser.
 * Adds: Spring Boot starters, continuum-commons, Jackson Kotlin, kotlin-reflect, BOMs, publishing.
 *
 * Usage:
 * ```kotlin
 * plugins { id("org.projectcontinuum.feature") }
 * ```
 */
class ContinuumFeaturePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = ContinuumFeatureBase.getOrCreateExtension(project, isKotlin = true)

        // Apply Kotlin plugins
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.spring")

        // Apply common plugins (dependency-management, maven-publish, jreleaser)
        ContinuumFeatureBase.applyCommonPlugins(project)

        // Configure toolchain, repos, JUnit, annotation validation
        ContinuumFeatureBase.configureCommon(project)

        // Configure Kotlin compiler options
        project.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict")
            }
        }

        // Configure dependencies and publishing after evaluation
        project.afterEvaluate {
            ContinuumFeatureBase.configureDependencies(project, ext)
            ContinuumFeatureBase.configurePublishingAndRelease(project, ext)
        }
    }
}
