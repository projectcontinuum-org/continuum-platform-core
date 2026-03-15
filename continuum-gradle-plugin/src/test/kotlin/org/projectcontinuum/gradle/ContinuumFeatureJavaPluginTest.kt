package org.projectcontinuum.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContinuumFeatureJavaPluginTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var propertiesFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(projectDir, "settings.gradle.kts").apply {
            writeText("""rootProject.name = "test-java-feature"""")
        }
        propertiesFile = File(projectDir, "gradle.properties").apply {
            writeText("repoName=test/test-java-feature\n")
        }
        buildFile = File(projectDir, "build.gradle.kts")
    }

    @Test
    fun `java feature plugin applies without errors`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature-java")
            }
            group = "com.test.feature"
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
    fun `java feature plugin configures java dependencies not kotlin`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature-java")
            }
            group = "com.test.feature"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencies", "--configuration", "compileClasspath", "--stacktrace")
            .build()

        assertTrue(result.output.contains("spring-boot-starter"))
        assertTrue(result.output.contains("continuum-commons"))
        assertTrue(result.output.contains("jackson-databind"))
        assertFalse(result.output.contains("jackson-module-kotlin"))
        assertFalse(result.output.contains("kotlin-reflect"))
    }

    @Test
    fun `java feature plugin configures publishing`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature-java")
            }
            group = "com.test.feature"
            version = "0.0.1"
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group", "publishing", "--stacktrace")
            .build()

        assertTrue(result.output.contains("publishMavenJavaPublicationToLocalStagingRepository"))
    }

    @Test
    fun `java worker plugin applies without errors`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker-java")
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
        assertTrue(result.output.contains("bootJar"))
        assertTrue(result.output.contains("jib"))
    }

    @Test
    fun `java worker plugin includes worker starter dependency`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.worker-java")
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
    }
}
