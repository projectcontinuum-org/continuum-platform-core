package org.projectcontinuum.gradle

import org.gradle.testkit.runner.GradleRunner
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
    fun `worker plugin configures jib task`() {
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

        assertTrue(result.output.contains("jib"))
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
