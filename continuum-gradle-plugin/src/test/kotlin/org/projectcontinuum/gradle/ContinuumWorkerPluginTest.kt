package org.projectcontinuum.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContinuumWorkerPluginTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var propertiesFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(projectDir, "settings.gradle.kts").apply {
            writeText("""rootProject.name = "test-worker"""")
        }
        propertiesFile = File(projectDir, "gradle.properties").apply {
            writeText("repoName=test/test-worker\n")
        }
        buildFile = File(projectDir, "build.gradle.kts")
    }

    @Test
    fun `worker plugin applies without errors`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `worker plugin includes worker starter dependency`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencies", "--configuration", "compileClasspath", "--stacktrace")
            .build()

        assertTrue(result.output.contains("continuum-worker-springboot-starter"))
        assertTrue(result.output.contains("spring-boot-starter"))
    }

    @Test
    fun `worker plugin configures jib task when jibToImage is set`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
            continuum {
                jibToImage.set("test/test-worker:0.0.1")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all", "--stacktrace")
            .build()

        assertTrue(result.output.contains("jib"))
    }

    @Test
    fun `jibToImage can be overridden via continuum extension`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
            continuum {
                jibToImage.set("myorg/custom-image:0.0.1")
            }
            tasks.register("printJibImage") {
                doLast {
                    val jib = project.extensions.getByType(com.google.cloud.tools.jib.gradle.JibExtension::class.java)
                    println("JIB_IMAGE=" + jib.to.image)
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("printJibImage", "--stacktrace")
            .build()

        assertTrue(result.output.contains("JIB_IMAGE=myorg/custom-image:0.0.1"))
    }

    @Test
    fun `jib plugin is not applied when jibToImage is not set`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(result.output.contains("jib - "))
    }

    @Test
    fun `worker plugin has bootJar task from Spring Boot`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker")
            }
            group = "com.test.worker"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all", "--stacktrace")
            .build()

        assertTrue(result.output.contains("bootJar"))
    }
}
