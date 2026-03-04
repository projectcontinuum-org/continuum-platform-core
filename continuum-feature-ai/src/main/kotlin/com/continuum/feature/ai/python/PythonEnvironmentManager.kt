package com.continuum.feature.ai.python

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages Python virtual environments for AI feature nodes.
 *
 * This component ensures that a Python virtual environment exists at the configured path
 * and that all required packages are installed. The check runs once at application startup
 * via [PostConstruct]. If the environment does not exist, it will be created automatically
 * and all dependencies will be installed before the application continues starting.
 *
 * ## Configuration
 * - `com.continuum.feature.ai.unsloth-trainer.venv-path` — path to the virtual environment
 *
 * ## Behavior
 * - If the venv directory exists and has an activation script, it is assumed to be valid.
 * - If the venv directory does not exist, it will be created using `python3 -m venv`.
 * - After creation, all packages in [REQUIRED_PACKAGES] are installed via `pip install`.
 *
 * ## Usage
 * Inject this component and use [resolvedVenvPath] to get the validated, ready-to-use path.
 *
 * @author Continuum Team
 * @since 1.0.0
 */
@Component
class PythonEnvironmentManager(
    @Value("\${com.continuum.feature.ai.unsloth-trainer.venv-path:~/.continuum/unsloth-env}")
    private val configuredVenvPath: String
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(PythonEnvironmentManager::class.java)

        /**
         * Required Python packages for the AI feature module.
         *
         * These packages are installed into the virtual environment when it is first created.
         * The list covers all dependencies needed by the training scripts (e.g., train.py).
         */
        val REQUIRED_PACKAGES: List<String> = listOf(
            "pyarrow",
            "pandas",
            "datasets",
            "torch",
            "transformers",
            "peft",
            "trl",
            "accelerate",
            "hf_transfer",
            "sentencepiece",
            "protobuf",
            "bitsandbytes"
        )

        /** Timeout for the venv creation process (in minutes) */
        private const val VENV_CREATION_TIMEOUT_MINUTES = 5L

        /** Timeout for the pip install process (in minutes) */
        private const val PIP_INSTALL_TIMEOUT_MINUTES = 30L
    }

    /**
     * The resolved, absolute path to the Python virtual environment.
     * Guaranteed to be valid after [PostConstruct] completes.
     */
    private lateinit var _resolvedVenvPath: String

    val resolvedVenvPath: String
        get() = _resolvedVenvPath

    /**
     * Runs at application startup to ensure the Python virtual environment exists
     * and all required packages are installed.
     *
     * If the venv is missing, it will be created and dependencies installed before
     * the application finishes starting.
     *
     * @throws PythonEnvironmentException if creation or package installation fails
     */
    @PostConstruct
    fun init() {
        LOGGER.info("Checking Python virtual environment at configured path: $configuredVenvPath")

        if (configuredVenvPath.isBlank()) {
            throw PythonEnvironmentException(
                "Python virtual environment path is not configured. " +
                "Set 'com.continuum.feature.ai.unsloth-trainer.venv-path' in application properties."
            )
        }

        val expandedPath = expandHome(configuredVenvPath)
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        val venvDir = File(expandedPath)
        val activateScript = if (isWindows) {
            File(venvDir, "Scripts/activate.bat")
        } else {
            File(venvDir, "bin/activate")
        }

        if (venvDir.exists() && activateScript.exists()) {
            LOGGER.info("Python virtual environment already exists at: $expandedPath")
        } else {
            LOGGER.info("Python virtual environment not found at: $expandedPath — creating it now...")
            createVenv(expandedPath)
            installPackages(expandedPath, isWindows)
            LOGGER.info("Python virtual environment is ready at: $expandedPath")
        }

        _resolvedVenvPath = expandedPath
    }

    /**
     * Creates a new Python virtual environment at the given path.
     *
     * @param venvPath Absolute path where the venv should be created
     * @throws PythonEnvironmentException if the process fails
     */
    private fun createVenv(venvPath: String) {
        LOGGER.info("Creating Python virtual environment at: $venvPath")

        // Ensure parent directory exists
        val parentDir = File(venvPath).parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val pythonCmd = findPythonCommand()

        val processBuilder = ProcessBuilder(pythonCmd, "-m", "venv", venvPath)
        processBuilder.redirectErrorStream(true)
        processBuilder.environment().putAll(System.getenv())

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(VENV_CREATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw PythonEnvironmentException(
                "Timed out creating Python virtual environment after $VENV_CREATION_TIMEOUT_MINUTES minutes"
            )
        }

        if (process.exitValue() != 0) {
            throw PythonEnvironmentException(
                "Failed to create Python virtual environment at $venvPath. Exit code: ${process.exitValue()}. Output: $output"
            )
        }

        LOGGER.info("Successfully created Python virtual environment at: $venvPath")
    }

    /**
     * Installs all [REQUIRED_PACKAGES] into the virtual environment using pip.
     *
     * @param venvPath Absolute path to the virtual environment
     * @param isWindows Whether the OS is Windows
     * @throws PythonEnvironmentException if pip install fails
     */
    private fun installPackages(venvPath: String, isWindows: Boolean) {
        LOGGER.info("Installing required packages: {}", REQUIRED_PACKAGES.joinToString(", "))

        val pipCmd = if (isWindows) {
            "$venvPath\\Scripts\\pip.exe"
        } else {
            "$venvPath/bin/pip"
        }

        // Upgrade pip first
        runPipCommand(
            pipCmd,
            listOf("install", "--upgrade", "pip"),
            "Upgrading pip"
        )

        // Install all required packages in a single pip install call
        runPipCommand(
            pipCmd,
            listOf("install") + REQUIRED_PACKAGES,
            "Installing required packages"
        )
    }

    /**
     * Runs a pip command and handles output and error checking.
     */
    private fun runPipCommand(pipCmd: String, args: List<String>, description: String) {
        LOGGER.info("$description...")

        val command = listOf(pipCmd) + args
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        processBuilder.environment().putAll(System.getenv())

        val process = processBuilder.start()

        // Stream output for visibility
        val outputReader = process.inputStream.bufferedReader()
        val output = StringBuilder()
        var line: String?
        while (outputReader.readLine().also { line = it } != null) {
            LOGGER.debug("[pip] {}", line)
            output.appendLine(line)
        }

        val completed = process.waitFor(PIP_INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw PythonEnvironmentException(
                "$description timed out after $PIP_INSTALL_TIMEOUT_MINUTES minutes"
            )
        }

        if (process.exitValue() != 0) {
            throw PythonEnvironmentException(
                "$description failed with exit code ${process.exitValue()}. Output:\n$output"
            )
        }

        LOGGER.info("$description completed successfully")
    }

    /**
     * Finds the appropriate Python command available on the system.
     *
     * Tries `python3` first (preferred on Linux/macOS), then falls back to `python`.
     *
     * @return The Python command string
     * @throws PythonEnvironmentException if no Python installation is found
     */
    private fun findPythonCommand(): String {
        for (cmd in listOf("python3", "python")) {
            try {
                val process = ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText().trim()
                val exited = process.waitFor(10, TimeUnit.SECONDS)
                if (exited && process.exitValue() == 0) {
                    LOGGER.info("Found Python: $cmd ($output)")
                    return cmd
                }
            } catch (_: Exception) {
                // Command not found, try next
            }
        }
        throw PythonEnvironmentException(
            "Python is not installed or not on PATH. Please install Python 3.8+ and ensure 'python3' or 'python' is available."
        )
    }

    /**
     * Expands ~ to the user's home directory.
     */
    private fun expandHome(path: String): String {
        return if (path.startsWith("~")) {
            path.replaceFirst("~", System.getProperty("user.home"))
        } else {
            path
        }
    }
}

/**
 * Exception thrown when Python environment setup fails.
 */
class PythonEnvironmentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)




