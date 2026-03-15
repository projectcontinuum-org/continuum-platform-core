package org.projectcontinuum.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

/**
 * Convention plugin for Continuum **Java** feature modules.
 *
 * Applies: java-library, spring-dependency-management, maven-publish, jreleaser.
 * Adds: Spring Boot starters, continuum-commons, Jackson Databind, BOMs, publishing.
 *
 * Usage:
 * ```kotlin
 * plugins { id("org.projectcontinuum.feature-java") }
 * ```
 */
class ContinuumFeatureJavaPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = ContinuumFeatureBase.getOrCreateExtension(project, isKotlin = false)

        // Apply Java plugin
        project.pluginManager.apply("java-library")

        // Apply common plugins (dependency-management, maven-publish, jreleaser)
        ContinuumFeatureBase.applyCommonPlugins(project)

        // Configure toolchain, repos, JUnit, annotation validation
        ContinuumFeatureBase.configureCommon(project)

        // Configure dependencies and publishing after evaluation
        project.afterEvaluate {
            ContinuumFeatureBase.configureDependencies(project, ext)
            ContinuumFeatureBase.configurePublishingAndRelease(project, ext)
        }
    }
}
