package com.continuum.feature.ai.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.prototol.progress.NodeProgress
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.feature.ai.python.PythonEnvironmentManager
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Continuum node for fine-tuning Large Language Models using the Unsloth trainer.
 *
 * This node wraps the `train.py` Python script which provides:
 * - Memory-optimized streaming data loading from Parquet files
 * - LoRA (Low-Rank Adaptation) for efficient fine-tuning
 * - Unsloth acceleration on Linux+CUDA (2x faster, 60% less memory)
 * - Fallback to standard HuggingFace transformers on other platforms
 *
 * ## Python Environment
 * This node requires a Python virtual environment with the following packages:
 * - pyarrow, pandas, datasets, torch, transformers, peft, trl, accelerate
 * - For Unsloth acceleration: unsloth (Linux + CUDA only)
 *
 * Configure the virtual environment path via:
 * - Property: `com.continuum.feature.ai.unsloth-trainer.venv-path`
 *
 * ## Input Format
 * The input Parquet file should contain at least two columns:
 * - An instruction/input column (configurable, default: "instruction")
 * - A response/output column (configurable, default: "response")
 *
 * ## Output
 * The node outputs a single row containing information about the trained model:
 * - model_path: Path to the saved LoRA adapter weights
 * - base_model: The base model that was fine-tuned
 * - training_config: JSON string with all training parameters used
 *
 * ## Supported Models
 * Any HuggingFace causal language model, including:
 * - microsoft/phi-2, microsoft/phi-3
 * - mistralai/Mistral-7B-v0.3
 * - meta-llama/Llama-2-7b-hf
 * - And many more...
 *
 * @property pythonEnvironmentManager Manages Python venv creation and resolution (auto-creates if missing at startup)
 * @property cacheStoragePath Base path for storing trained models (configurable via properties)
 * @author Continuum Team
 * @since 1.0.0
 */
@Component
class UnslothTrainerNodeModel(
  private val pythonEnvironmentManager: PythonEnvironmentManager,
  @param:Value("\${com.continuum.feature.ai.unsloth-trainer.cache-storage-path:./.continuum-cache/workflow-data}")
  private val cacheStoragePath: String
) : ProcessNodeModel() {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(UnslothTrainerNodeModel::class.java)
    private val objectMapper = ObjectMapper()

    /** Default batch size for reading parquet files in chunks */
    private const val DEFAULT_PARQUET_BATCH_SIZE = 10000

    /** Resource path for the train.py script */
    private const val TRAIN_SCRIPT_RESOURCE = "scripts/train.py"
  }

  // =========================================================================
  // Port Definitions
  // =========================================================================

  final override val inputPorts = mapOf(
    "training_data" to ContinuumWorkflowModel.NodePort(
      name = "Training Data",
      contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "model_info" to ContinuumWorkflowModel.NodePort(
      name = "Model Info",
      contentType = MediaType.APPLICATION_JSON_VALUE
    )
  )

  override val categories = listOf("Machine Learning", "LLM Training")

  // =========================================================================
  // Properties Schema (JSON Schema for node configuration)
  // =========================================================================

  private val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
    {
      "type": "object",
      "properties": {
        "model": {
          "type": "string",
          "title": "Base Model",
          "description": "HuggingFace model identifier. Select from popular models or enter a custom model ID.",
          "default": "unsloth/Phi-4",
          "examples": [
            "unsloth/Phi-4",
            "unsloth/Phi-4-mini-instruct",
            "unsloth/mistral-7b-v0.3",
            "unsloth/mistral-7b-instruct-v0.3",
            "unsloth/llama-3-8b",
            "unsloth/llama-3-8b-instruct",
            "unsloth/llama-3.1-8b",
            "unsloth/llama-3.1-8b-instruct",
            "unsloth/llama-3.2-1b",
            "unsloth/llama-3.2-1b-instruct",
            "unsloth/llama-3.2-3b",
            "unsloth/llama-3.2-3b-instruct",
            "unsloth/gemma-2-2b",
            "unsloth/gemma-2-2b-it",
            "unsloth/gemma-2-9b",
            "unsloth/gemma-2-9b-it",
            "unsloth/Qwen2.5-1.5B",
            "unsloth/Qwen2.5-1.5B-Instruct",
            "unsloth/Qwen2.5-7B",
            "unsloth/Qwen2.5-7B-Instruct",
            "microsoft/phi-2",
            "microsoft/Phi-3-mini-4k-instruct",
            "mistralai/Mistral-7B-v0.3",
            "mistralai/Mistral-7B-Instruct-v0.3",
            "meta-llama/Llama-2-7b-hf",
            "meta-llama/Llama-2-7b-chat-hf",
            "google/gemma-2b",
            "google/gemma-7b",
            "Qwen/Qwen2-7B",
            "Qwen/Qwen2-7B-Instruct",
            "tiiuae/falcon-7b",
            "tiiuae/falcon-7b-instruct"
          ]
        },
        "inputColumn": {
          "type": "string",
          "title": "Input Column",
          "description": "Column name containing instruction/input text",
          "default": "instruction"
        },
        "outputColumn": {
          "type": "string",
          "title": "Output Column",
          "description": "Column name containing response/output text",
          "default": "response"
        },
        "systemPrompt": {
          "type": "string",
          "title": "System Prompt",
          "description": "Optional system prompt to prepend to all training examples"
        },
        "epochs": {
          "type": "integer",
          "title": "Epochs",
          "description": "Number of training epochs (full passes through data)",
          "default": 1,
          "minimum": 1,
          "maximum": 100
        },
        "batchSize": {
          "type": "integer",
          "title": "Batch Size",
          "description": "Number of samples per training step (higher = more memory, faster)",
          "default": 2,
          "minimum": 1,
          "maximum": 64
        },
        "gradientAccumulation": {
          "type": "integer",
          "title": "Gradient Accumulation Steps",
          "description": "Accumulate gradients over N steps (simulates larger batch size)",
          "default": 4,
          "minimum": 1,
          "maximum": 128
        },
        "learningRate": {
          "type": "number",
          "title": "Learning Rate",
          "description": "Step size for optimizer (too high = unstable, too low = slow)",
          "default": 0.0002,
          "minimum": 0.0000001,
          "maximum": 0.1
        },
        "maxSeqLength": {
          "type": "integer",
          "title": "Max Sequence Length",
          "description": "Maximum token sequence length (longer = more memory)",
          "default": 2048,
          "minimum": 128,
          "maximum": 32768
        },
        "loraR": {
          "type": "integer",
          "title": "LoRA Rank (r)",
          "description": "Rank of LoRA matrices (higher = more capacity but slower)",
          "default": 16,
          "minimum": 1,
          "maximum": 256
        },
        "loraAlpha": {
          "type": "integer",
          "title": "LoRA Alpha",
          "description": "LoRA scaling factor (typically set equal to LoRA rank)",
          "default": 16,
          "minimum": 1,
          "maximum": 256
        },
        "loraDropout": {
          "type": "number",
          "title": "LoRA Dropout",
          "description": "Dropout rate for LoRA layers (0.0 = no dropout)",
          "default": 0.0,
          "minimum": 0.0,
          "maximum": 0.5
        },
        "warmupSteps": {
          "type": "integer",
          "title": "Warmup Steps",
          "description": "Gradually increase learning rate for N steps at start",
          "default": 5,
          "minimum": 0,
          "maximum": 1000
        },
        "weightDecay": {
          "type": "number",
          "title": "Weight Decay",
          "description": "L2 regularization to prevent overfitting",
          "default": 0.01,
          "minimum": 0.0,
          "maximum": 1.0
        },
        "seed": {
          "type": "integer",
          "title": "Random Seed",
          "description": "Random seed for reproducibility",
          "default": 42
        },
        "saveSteps": {
          "type": "integer",
          "title": "Save Steps",
          "description": "Save checkpoint every N steps",
          "default": 100,
          "minimum": 1
        },
        "loggingSteps": {
          "type": "integer",
          "title": "Logging Steps",
          "description": "Log metrics every N steps",
          "default": 10,
          "minimum": 1
        },
        "use4Bit": {
          "type": "boolean",
          "title": "Use 4-bit Quantization",
          "description": "Use 4-bit quantization to reduce memory usage (recommended)",
          "default": true
        },
        "parquetBatchSize": {
          "type": "integer",
          "title": "Parquet Batch Size",
          "description": "Rows to read at once from parquet file (lower = less memory)",
          "default": 10000,
          "minimum": 100,
          "maximum": 100000
        },
        "hfToken": {
          "type": "string",
          "title": "HuggingFace Token",
          "description": "Optional HuggingFace access token for downloading gated or private models (e.g., Llama, Gemma). Get yours at https://huggingface.co/settings/tokens"
        }
      },
      "required": ["model"]
    }
    """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  // =========================================================================
  // UI Schema (JSON Forms UI configuration)
  // =========================================================================

  private val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
    """
    {
      "type": "Categorization",
      "elements": [
        {
          "type": "Category",
          "label": "Model",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/model",
              "options": {
                "autocomplete": true,
                "freeSolo": true
              }
            }
          ]
        },
        {
          "type": "Category",
          "label": "Data",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/inputColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/outputColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/systemPrompt",
              "options": {
                "multi": true,
                "rows": 3
              }
            }
          ]
        },
        {
          "type": "Category",
          "label": "Training",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/epochs"
            },
            {
              "type": "Control",
              "scope": "#/properties/batchSize"
            },
            {
              "type": "Control",
              "scope": "#/properties/gradientAccumulation"
            },
            {
              "type": "Control",
              "scope": "#/properties/learningRate"
            },
            {
              "type": "Control",
              "scope": "#/properties/maxSeqLength"
            },
            {
              "type": "Control",
              "scope": "#/properties/warmupSteps"
            },
            {
              "type": "Control",
              "scope": "#/properties/weightDecay"
            }
          ]
        },
        {
          "type": "Category",
          "label": "LoRA",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/loraR"
            },
            {
              "type": "Control",
              "scope": "#/properties/loraAlpha"
            },
            {
              "type": "Control",
              "scope": "#/properties/loraDropout"
            }
          ]
        },
        {
          "type": "Category",
          "label": "Advanced",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/seed"
            },
            {
              "type": "Control",
              "scope": "#/properties/saveSteps"
            },
            {
              "type": "Control",
              "scope": "#/properties/loggingSteps"
            },
            {
              "type": "Control",
              "scope": "#/properties/use4Bit"
            },
            {
              "type": "Control",
              "scope": "#/properties/parquetBatchSize"
            },
            {
              "type": "Control",
              "scope": "#/properties/hfToken",
              "options": {
                "format": "password"
              }
            }
          ]
        }
      ]
    }
    """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  // =========================================================================
  // Node Metadata
  // =========================================================================

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Fine-tune Large Language Models using LoRA with Unsloth acceleration",
    title = "LLM Trainer (Unsloth)",
    subTitle = "Fine-tune LLMs with LoRA adapters",
    nodeModel = this.javaClass.name,
    icon = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
      </svg>
    """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "model" to "unsloth/Phi-4",
      "inputColumn" to "instruction",
      "outputColumn" to "response",
      "epochs" to 1,
      "batchSize" to 2,
      "gradientAccumulation" to 4,
      "learningRate" to 0.0002,
      "maxSeqLength" to 2048,
      "loraR" to 16,
      "loraAlpha" to 16,
      "loraDropout" to 0.0,
      "warmupSteps" to 5,
      "weightDecay" to 0.01,
      "seed" to 42,
      "saveSteps" to 100,
      "loggingSteps" to 10,
      "use4Bit" to true,
      "parquetBatchSize" to DEFAULT_PARQUET_BATCH_SIZE
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  // =========================================================================
  // Execution Logic
  // =========================================================================

  /**
   * Executes the LLM fine-tuning process.
   *
   * This method:
   * 1. Extracts configuration from node properties
   * 2. Gets the input parquet file path from the training data reader
   * 3. Builds and executes the train.py command with all parameters
   * 4. Parses progress updates from the script's IPC output
   * 5. Writes model information to the output port
   *
   * @param properties Node configuration properties
   * @param inputs Map of input port IDs to their readers
   * @param nodeOutputWriter Writer for output port data
   * @param nodeProgressCallback Callback for reporting progress
   * @throws NodeRuntimeException if training fails or script is not found
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
  ) {
    // Get the input data reader
    val trainingDataReader = inputs["training_data"] ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "Training data input is required"
    )

    // Get the parquet file path
    val dataFilePath = trainingDataReader.getFilePath().toAbsolutePath().toString()
    LOGGER.info("Training data file: $dataFilePath")

    // Extract configuration from properties with defaults
    val model = properties?.get("model")?.toString() ?: "unsloth/Phi-4"
    val inputColumn = properties?.get("inputColumn")?.toString() ?: "instruction"
    val outputColumn = properties?.get("outputColumn")?.toString() ?: "response"

    // Generate unique output path: {cacheStoragePath}/unsloth/{uuid}
    val modelId = UUID.randomUUID().toString()
    val outputPath = java.io.File(cacheStoragePath, "unsloth/$modelId").absolutePath
    LOGGER.info("Model will be saved to: $outputPath")

    val systemPrompt = properties?.get("systemPrompt")?.toString()
    val epochs = (properties?.get("epochs") as? Number)?.toInt() ?: 1
    val batchSize = (properties?.get("batchSize") as? Number)?.toInt() ?: 2
    val gradientAccumulation = (properties?.get("gradientAccumulation") as? Number)?.toInt() ?: 4
    val learningRate = (properties?.get("learningRate") as? Number)?.toDouble() ?: 0.0002
    val maxSeqLength = (properties?.get("maxSeqLength") as? Number)?.toInt() ?: 2048
    val loraR = (properties?.get("loraR") as? Number)?.toInt() ?: 16
    val loraAlpha = (properties?.get("loraAlpha") as? Number)?.toInt() ?: 16
    val loraDropout = (properties?.get("loraDropout") as? Number)?.toDouble() ?: 0.0
    val warmupSteps = (properties?.get("warmupSteps") as? Number)?.toInt() ?: 5
    val weightDecay = (properties?.get("weightDecay") as? Number)?.toDouble() ?: 0.01
    val seed = (properties?.get("seed") as? Number)?.toInt() ?: 42
    val saveSteps = (properties?.get("saveSteps") as? Number)?.toInt() ?: 100
    val loggingSteps = (properties?.get("loggingSteps") as? Number)?.toInt() ?: 10
    val use4Bit = properties?.get("use4Bit") as? Boolean ?: true
    val parquetBatchSize = (properties?.get("parquetBatchSize") as? Number)?.toInt() ?: DEFAULT_PARQUET_BATCH_SIZE
    val hfToken = properties?.get("hfToken")?.toString()?.takeIf { it.isNotBlank() }

    // Get the resolved virtual environment path from PythonEnvironmentManager
    // (venv is guaranteed to exist — created and packages installed at startup if missing)
    val resolvedVenvPath = pythonEnvironmentManager.resolvedVenvPath

    // Get the train.py script from resources
    val scriptPath = getTrainScriptPath()
    LOGGER.info("Using train script: $scriptPath")

    // Build the command arguments
    val commandArgs = mutableListOf(
      "--data", dataFilePath,
      "--model", model,
      "--output", outputPath,
      "--input-column", inputColumn,
      "--output-column", outputColumn,
      "--epochs", epochs.toString(),
      "--batch-size", batchSize.toString(),
      "--gradient-accumulation", gradientAccumulation.toString(),
      "--learning-rate", learningRate.toString(),
      "--max-seq-length", maxSeqLength.toString(),
      "--lora-r", loraR.toString(),
      "--lora-alpha", loraAlpha.toString(),
      "--lora-dropout", loraDropout.toString(),
      "--warmup-steps", warmupSteps.toString(),
      "--weight-decay", weightDecay.toString(),
      "--seed", seed.toString(),
      "--save-steps", saveSteps.toString(),
      "--logging-steps", loggingSteps.toString(),
      "--parquet-batch-size", parquetBatchSize.toString(),
      "--silent",  // Suppress non-JSON output for cleaner IPC parsing
      "--ipc"  // Enable IPC mode for progress reporting
    )

    // Add optional parameters
    if (!systemPrompt.isNullOrBlank()) {
      commandArgs.addAll(listOf("--system-prompt", systemPrompt))
    }
    if (!use4Bit) {
      commandArgs.add("--no-4bit")
    }

    // Build the process with venv activation if specified
    val extraEnv = mutableMapOf<String, String>()
    if (hfToken != null) {
      extraEnv["HF_TOKEN"] = hfToken
      LOGGER.info("HuggingFace token provided — will be used for model downloads")
    }
    val processBuilder = buildProcessBuilder(resolvedVenvPath, scriptPath, commandArgs, extraEnv)
    LOGGER.info("Executing training script with venv: $resolvedVenvPath")
    LOGGER.debug("Command: {}", processBuilder.command().joinToString(" "))

    val process = processBuilder.start()

    // Register a shutdown hook to ensure the child process is killed before JVM exit
    val shutdownHook = Thread {
      if (process.isAlive) {
        LOGGER.warn("JVM shutting down — destroying training child process")
        process.destroyForcibly()
        try {
          process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    // Read stdout for IPC progress messages (JSON format)
    val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))

    val exitCode: Int
    // Process IPC progress messages from stdout
    try {
      var line: String?
      while (stdoutReader.readLine().also { line = it } != null) {
        line?.let { progressLine ->
          try {
            // Parse the JSON progress message directly into NodeProgress object
            // The train.py script outputs JSON that matches NodeProgress exactly
            val nodeProgress = objectMapper.readValue(progressLine, NodeProgress::class.java)

            // Report progress to the workflow with full details
            nodeProgressCallback.report(nodeProgress)

            LOGGER.debug(
              "Training progress: {}% - {} | Stages: {} | Stage duration: {}ms | Total duration: {}ms",
              nodeProgress.progressPercentage,
              nodeProgress.message ?: "No message",
              nodeProgress.stageStatus?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "N/A",
              nodeProgress.stageDurationMs ?: "N/A",
              nodeProgress.totalDurationMs ?: "N/A"
            )
          } catch (_: Exception) {
            // Not a valid JSON progress message, log it
            LOGGER.debug("Non-JSON output: $progressLine")
          }
        }
      }

      // Wait for the process to complete
      exitCode = process.waitFor()
    } catch (e: Exception) {
      // On any exception (including InterruptedException), kill the child process
      if (process.isAlive) {
        LOGGER.warn("Destroying training child process due to exception: ${e.message}")
        process.destroyForcibly()
        process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
      }
      throw e
    } finally {
      stdoutReader.close()
      // Remove the shutdown hook since the process has terminated (or been killed)
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
      } catch (_: IllegalStateException) {
        // JVM is already shutting down — hook cannot be removed, which is fine
      }
    }

    if (exitCode != 0) {
      // Read stderr only on failure
      val errorMessage = process.errorStream.bufferedReader().use { it.readText() }.ifBlank { "Unknown error" }
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Training script failed with exit code $exitCode: $errorMessage",
        isRetriable = false
      )
    }

    LOGGER.info("Training completed successfully")

    // Write output - model information
    val modelInfo = mapOf(
      "model_path" to "$outputPath/final",
      "base_model" to model,
      "training_config" to objectMapper.writeValueAsString(
        mapOf(
          "epochs" to epochs,
          "batch_size" to batchSize,
          "learning_rate" to learningRate,
          "lora_r" to loraR,
          "lora_alpha" to loraAlpha,
          "max_seq_length" to maxSeqLength,
          "input_column" to inputColumn,
          "output_column" to outputColumn
        )
      ),
      "status" to "completed"
    )

    nodeOutputWriter.createOutputPortWriter("model_info").use { writer ->
      writer.write(0, modelInfo)
    }
  }


  /**
   * Builds the command to execute the training script.
   *
   * The command will be wrapped in a shell that first activates the
   * virtual environment before running Python.
   *
   * @param venvPath Path to the Python virtual environment
   * @param scriptPath Path to the train.py script
   * @param args List of arguments to pass to the script
   * @return ProcessBuilder configured to run the command
   */
  private fun buildProcessBuilder(venvPath: String, scriptPath: String, args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    // Build command that activates venv and runs python
    val pythonCommand = listOf("python", scriptPath) + args
    val pythonCommandStr = pythonCommand.joinToString(" ") { arg ->
      // Quote arguments that contain spaces
      if (arg.contains(" ")) "\"$arg\"" else arg
    }

    val processBuilder = if (isWindows) {
      // Windows: use cmd /c to run activate.bat && python ...
      val activateScript = "$venvPath\\Scripts\\activate.bat"
      ProcessBuilder("cmd", "/c", "$activateScript && $pythonCommandStr")
    } else {
      // Unix: use bash -c to source activate && python ...
      val activateScript = "$venvPath/bin/activate"
      ProcessBuilder("bash", "-c", "source \"$activateScript\" && $pythonCommandStr")
    }

    // Ensure stderr is NOT merged with stdout so we can read them separately
    processBuilder.redirectErrorStream(false)

    // Inherit environment variables (needed for CUDA, etc.)
    processBuilder.environment().putAll(System.getenv())

    // Enable hf_transfer for faster HuggingFace model downloads (Rust-based parallel chunked transfers)
    processBuilder.environment()["HF_HUB_ENABLE_HF_TRANSFER"] = "1"

    // Apply any extra environment variables (e.g., HF_TOKEN for gated model access)
    processBuilder.environment().putAll(extraEnv)

    return processBuilder
  }

  /**
   * Extracts the train.py script from resources to a temporary file.
   *
   * The script is bundled in the JAR under `scripts/train.py` and is extracted
   * to a new temporary file on each call. This ensures thread-safety and no
   * shared state between executions.
   *
   * @return Absolute path to the extracted train.py script
   * @throws NodeRuntimeException if the script resource is not found
   */
  private fun getTrainScriptPath(): String {
    // Extract script from resources to a temporary file
    val resourceStream = this::class.java.classLoader.getResourceAsStream(TRAIN_SCRIPT_RESOURCE)
      ?: throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Could not find train.py script in resources at '$TRAIN_SCRIPT_RESOURCE'",
        isRetriable = false
      )

    val tempScript = Files.createTempFile("unsloth_train_", ".py")
    resourceStream.use { input ->
      Files.copy(input, tempScript, StandardCopyOption.REPLACE_EXISTING)
    }

    // Make the script executable
    tempScript.toFile().setExecutable(true)

    // Mark for deletion on JVM exit
    tempScript.toFile().deleteOnExit()

    LOGGER.info("Extracted train.py script to: $tempScript")
    return tempScript.toAbsolutePath().toString()
  }
}

