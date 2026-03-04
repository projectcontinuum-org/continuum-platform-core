# LLM Trainer (Unsloth) Node
Fine-tune Large Language Models using LoRA (Low-Rank Adaptation) with optional Unsloth acceleration for memory-optimized training.
## Input Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| training_data | Table | Parquet | Training data with instruction and response columns |
## Output Ports
| Port | Type | Format | Description |
|------|------|--------|-------------|
| model_info | Table | JSON | Information about the trained model including path and configuration |
## Properties
### Model Configuration
- **model** (string, required): HuggingFace model identifier (e.g., `microsoft/phi-2`, `mistralai/Mistral-7B-v0.3`)
- **outputPath** (string, required): Directory where the fine-tuned model will be saved
### Data Configuration
- **inputColumn** (string, default: "instruction"): Column name containing instruction/input text
- **outputColumn** (string, default: "response"): Column name containing response/output text
- **systemPrompt** (string, optional): System prompt to prepend to all training examples
### Training Parameters
- **epochs** (integer, default: 1): Number of training epochs (full passes through data)
- **batchSize** (integer, default: 2): Samples per training step (higher = more memory)
- **gradientAccumulation** (integer, default: 4): Accumulate gradients over N steps
- **learningRate** (number, default: 0.0002): Optimizer learning rate
- **maxSeqLength** (integer, default: 2048): Maximum token sequence length
- **warmupSteps** (integer, default: 5): Gradually increase LR for N steps at start
- **weightDecay** (number, default: 0.01): L2 regularization to prevent overfitting
### LoRA Configuration
- **loraR** (integer, default: 16): LoRA rank (higher = more capacity but slower)
- **loraAlpha** (integer, default: 16): LoRA scaling factor (typically equals loraR)
- **loraDropout** (number, default: 0.0): Dropout rate for LoRA layers
### Advanced Options
- **seed** (integer, default: 42): Random seed for reproducibility
- **saveSteps** (integer, default: 100): Save checkpoint every N steps
- **loggingSteps** (integer, default: 10): Log metrics every N steps
- **use4Bit** (boolean, default: true): Use 4-bit quantization to reduce memory
- **parquetBatchSize** (integer, default: 10000): Rows to read at once from parquet
## Behavior
The node executes the following pipeline:
1. **Script Extraction**: Extracts the bundled `train.py` script from JAR resources to a temp file
2. **Data Loading**: Streams training data from the input Parquet file (memory-efficient)
3. **Model Loading**: Loads the base model with optional 4-bit quantization
4. **LoRA Setup**: Configures Low-Rank Adaptation layers for efficient fine-tuning
5. **Training**: Runs supervised fine-tuning with the configured hyperparameters
6. **Saving**: Saves LoRA adapter weights to the output directory
**Platform Support:**
- **Linux + CUDA**: Uses Unsloth for 2x faster training and 60% less memory
- **macOS/Windows/CPU**: Falls back to standard HuggingFace transformers
**Progress Reporting:**
The node reports training progress in real-time via IPC, including:
- `progressPercentage`: Overall progress 0-100%
- `message`: Human-readable status message
- `stageStatus`: Map of stage names to status (PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED)
- `stageDurationMs`: Duration of the current/completed stage in milliseconds
- `totalDurationMs`: Total elapsed time since training started
Training stages reported:
1. `initialization` - Setting up the training environment
2. `loading_dataset` - Loading and validating the parquet data
3. `loading_model` - Downloading/loading the base model
4. `configuring_lora` - Setting up LoRA adapters
5. `training` - Running the training loop
6. `saving_model` - Saving the fine-tuned model
## Input Data Format
The input Parquet file should contain at least two columns:
| Column | Type | Description |
|--------|------|-------------|
| instruction | String | The input/question/prompt text |
| response | String | The expected output/answer text |
**Supported Formats:**
1. Standard columnar Parquet with named columns
2. Continuum DataRow format with cells array
## Output Data Format
The output contains a single row with model information:
```json
{
  "model_path": "./trained-model/final",
  "base_model": "microsoft/phi-2",
  "training_config": "{\"epochs\":1,\"batch_size\":2,...}",
  "status": "completed"
}
```
## Example 1: Basic Fine-tuning
**Input Table (training_data):**
```json
[
  {"instruction": "What is the capital of France?", "response": "The capital of France is Paris."},
  {"instruction": "What is 2+2?", "response": "2+2 equals 4."},
  {"instruction": "Who wrote Romeo and Juliet?", "response": "William Shakespeare wrote Romeo and Juliet."}
]
```
**Properties:**
```json
{
  "model": "microsoft/phi-2",
  "outputPath": "./my-finetuned-model",
  "inputColumn": "instruction",
  "outputColumn": "response",
  "epochs": 1,
  "batchSize": 2,
  "learningRate": 0.0002
}
```
**Output (model_info):**
```json
[
  {
    "model_path": "./my-finetuned-model/final",
    "base_model": "microsoft/phi-2",
    "training_config": "{\"epochs\":1,\"batch_size\":2,\"learning_rate\":0.0002,...}",
    "status": "completed"
  }
]
```
## Example 2: Custom Columns with System Prompt
**Input Table (training_data):**
```json
[
  {"question": "Explain quantum computing", "answer": "Quantum computing uses qubits..."},
  {"question": "What is machine learning?", "answer": "Machine learning is a subset of AI..."}
]
```
**Properties:**
```json
{
  "model": "mistralai/Mistral-7B-v0.3",
  "outputPath": "./science-tutor-model",
  "inputColumn": "question",
  "outputColumn": "answer",
  "systemPrompt": "You are a helpful science tutor. Provide clear, accurate explanations.",
  "epochs": 3,
  "loraR": 32,
  "loraAlpha": 32,
  "maxSeqLength": 4096
}
```
## Supported Models
Any HuggingFace causal language model, including:
- `microsoft/phi-2`, `microsoft/phi-3-mini-4k-instruct`
- `mistralai/Mistral-7B-v0.3`, `mistralai/Mixtral-8x7B-v0.1`
- `meta-llama/Llama-2-7b-hf`, `meta-llama/Llama-3-8B`
- `google/gemma-2b`, `google/gemma-7b`
- `Qwen/Qwen2-7B`, `Qwen/Qwen2-1.5B`
## Memory Requirements
| Model Size | 4-bit Quantization | 16-bit |
|------------|-------------------|--------|
| 2B params  | ~4GB VRAM         | ~8GB   |
| 7B params  | ~8GB VRAM         | ~16GB  |
| 13B params | ~12GB VRAM        | ~26GB  |
## Error Handling
The node will fail with descriptive errors for:
- Missing required columns in input data
- Python dependencies not installed
- CUDA out of memory errors
- Invalid hyperparameter values
- Training script execution failures
## Prerequisites
The Python environment must have these packages installed:
```bash
pip install pyarrow pandas datasets torch transformers peft trl accelerate
```
For Unsloth acceleration (Linux + CUDA only):
```bash
pip install "unsloth[colab-new] @ git+https://github.com/unslothai/unsloth.git"
```
## Configuration
Configure the Python executable via Spring properties:
```yaml
continuum:
  node:
    unsloth-trainer:
      python-executable: python3  # Path to Python executable
```
The `train.py` script is bundled within the JAR and automatically extracted at runtime.
