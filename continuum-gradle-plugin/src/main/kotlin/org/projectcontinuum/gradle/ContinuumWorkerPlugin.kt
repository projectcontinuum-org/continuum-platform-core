package org.projectcontinuum.gradle

import com.google.cloud.tools.jib.gradle.JibExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class ContinuumWorkerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Apply the feature plugin first (sets up extension, base deps, publishing, etc.)
        project.pluginManager.apply(ContinuumFeaturePlugin::class.java)

        // Apply worker-specific plugins
        project.pluginManager.apply("org.springframework.boot")
        project.pluginManager.apply("com.google.cloud.tools.jib")

        // Get the shared extension (already created by feature plugin)
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

            // Configure Jib
            configureJib(project)
        }
    }

    private fun configureJib(project: Project) {
        val repoName = (System.getenv("GITHUB_REPOSITORY")
            ?: project.findProperty("repoName")?.toString()
            ?: "").lowercase()

        project.extensions.configure<JibExtension> {
            from {
                image = "eclipse-temurin:21-jre"
            }
            to {
                image = "ghcr.io/$repoName/${project.name.lowercase()}:${project.version}"
                auth {
                    username = System.getenv("DOCKER_REPO_USERNAME") ?: ""
                    password = System.getenv("DOCKER_REPO_PASSWORD") ?: ""
                }
            }
        }
    }
}
