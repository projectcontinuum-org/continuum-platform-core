package org.projectcontinuum.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContinuumFeaturePluginTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var propertiesFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(projectDir, "settings.gradle.kts").apply {
            writeText("""rootProject.name = "test-feature"""")
        }
        propertiesFile = File(projectDir, "gradle.properties").apply {
            writeText("repoName=test/test-feature\n")
        }
        buildFile = File(projectDir, "build.gradle.kts")
    }

    @Test
    fun `feature plugin applies without errors`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature")
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
    fun `feature plugin configures dependencies`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature")
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
        assertTrue(result.output.contains("jackson-module-kotlin"))
    }

    @Test
    fun `feature plugin respects custom continuum version`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature")
            }
            group = "com.test.feature"
            version = "0.0.1"

            continuum {
                continuumVersion.set("0.0.5")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencies", "--configuration", "compileClasspath", "--stacktrace")
            .build()

        assertTrue(result.output.contains("continuum-commons:0.0.5"))
    }

    @Test
    fun `feature plugin configures publishing`() {
        buildFile.writeText("""
            plugins {
                id("org.projectcontinuum.feature")
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
}
