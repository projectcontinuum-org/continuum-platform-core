package org.projectcontinuum.gradle

import com.google.cloud.tools.jib.gradle.JibExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

/**
 * Convention plugin for Continuum **Java** worker modules.
 *
 * Extends the Java feature plugin with Spring Boot application, Jib containerization,
 * and the continuum-worker-springboot-starter dependency.
 *
 * Usage:
 * ```kotlin
 * plugins { id("org.projectcontinuum.worker-java") }
 * ```
 */
class ContinuumWorkerJavaPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Apply the Java feature plugin first
        project.pluginManager.apply(ContinuumFeatureJavaPlugin::class.java)

        // Apply worker-specific plugins
        project.pluginManager.apply("org.springframework.boot")

        val ext = project.extensions.getByType<ContinuumExtension>()

        project.afterEvaluate {
            val continuumVer = ext.continuumVersion.get()
            val continuumGrp = ext.continuumGroup.get()
            val awsSdkVer = ext.awsSdkVersion.get()

            // Add AWS SDK BOM
            project.extensions.configure<DependencyManagementExtension> {
                imports {
                    mavenBom("software.amazon.awssdk:bom:$awsSdkVer")
                }
            }

            // Add worker starter dependency
            project.dependencies.apply {
                add("implementation", "$continuumGrp:continuum-worker-springboot-starter:$continuumVer")
            }

            // Only apply/configure Jib if the module actually wants a container image
            if (ext.jibToImage.isPresent) {
                configureJib(project, ext)
            }
        }
    }

    private fun configureJib(project: Project, ext: ContinuumExtension) {
        project.pluginManager.apply("com.google.cloud.tools.jib")

        project.extensions.configure<JibExtension> {
            from {
                image = ext.jibFromImage.get()
            }
            to {
                image = ext.jibToImage.get()
                auth {
                    username = System.getenv("DOCKER_REPO_USERNAME") ?: ""
                    password = System.getenv("DOCKER_REPO_PASSWORD") ?: ""
                }
            }
        }
    }
}
