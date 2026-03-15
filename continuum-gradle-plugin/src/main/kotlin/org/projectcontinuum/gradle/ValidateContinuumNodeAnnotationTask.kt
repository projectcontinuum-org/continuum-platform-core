package org.projectcontinuum.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader

/**
 * Gradle task that validates all classes implementing ContinuumNodeModel
 * are annotated with @ContinuumNode. Runs after compilation to provide
 * a clear build-time error when the annotation is missing.
 */
abstract class ValidateContinuumNodeAnnotationTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDir: DirectoryProperty

    @get:Classpath
    abstract val compilationClasspath: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Validates that all ContinuumNodeModel implementations are annotated with @ContinuumNode"
    }

    @TaskAction
    fun validate() {
        val classesDirFile = classesDir.get().asFile
        if (!classesDirFile.exists()) return

        // Build a classloader with compiled classes + dependencies
        val urls = (listOf(classesDirFile) + compilationClasspath.files)
            .filter { it.exists() }
            .map { it.toURI().toURL() }
            .toTypedArray()

        val classLoader = URLClassLoader(urls, this::class.java.classLoader)

        // Load the interface and annotation class from the classpath
        val nodeModelInterface = try {
            classLoader.loadClass("org.projectcontinuum.core.commons.node.ContinuumNodeModel")
        } catch (e: ClassNotFoundException) {
            // continuum-commons not on classpath — nothing to check
            logger.info("ContinuumNodeModel not found on classpath, skipping validation")
            return
        }

        val nodeAnnotation = try {
            classLoader.loadClass("org.projectcontinuum.core.commons.annotation.ContinuumNode")
        } catch (e: ClassNotFoundException) {
            logger.info("@ContinuumNode annotation not found on classpath, skipping validation")
            return
        }

        // Find all .class files in the output directory
        val violations = mutableListOf<String>()

        classesDirFile.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .forEach { classFile ->
                val relativePath = classFile.relativeTo(classesDirFile).path
                val className = relativePath
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')

                // Skip inner classes, anonymous classes, and abstract classes
                if (className.contains('$')) return@forEach

                try {
                    val clazz = classLoader.loadClass(className)

                    // Skip abstract classes and interfaces
                    if (java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) return@forEach
                    if (clazz.isInterface) return@forEach

                    // Check if it implements ContinuumNodeModel
                    if (nodeModelInterface.isAssignableFrom(clazz)) {
                        // Check if it has @ContinuumNode annotation
                        val hasContinuumNode = clazz.annotations.any {
                            it.annotationClass.java.name == nodeAnnotation.name
                        }
                        if (!hasContinuumNode) {
                            violations.add(className)
                        }
                    }
                } catch (e: Throwable) {
                    // Skip classes that can't be loaded (e.g., missing dependencies)
                    logger.debug("Could not load class $className: ${e.message}")
                }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine()
                appendLine("Continuum Node Annotation Validation Failed")
                appendLine("============================================")
                appendLine()
                appendLine("The following classes implement ContinuumNodeModel but are missing the @ContinuumNode annotation:")
                appendLine()
                violations.forEach { className ->
                    appendLine("  - $className")
                }
                appendLine()
                appendLine("Fix: Add @ContinuumNode to each class:")
                appendLine()
                appendLine("  import org.projectcontinuum.core.commons.annotation.ContinuumNode")
                appendLine()
                appendLine("  @ContinuumNode")
                appendLine("  class ${violations.first().substringAfterLast('.')} : ProcessNodeModel() { ... }")
                appendLine()
            }
            throw GradleException(message)
        }

        if (logger.isInfoEnabled) {
            logger.info("Continuum node annotation validation passed")
        }
    }
}
