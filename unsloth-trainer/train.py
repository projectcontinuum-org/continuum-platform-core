#!/usr/bin/env python3
"""
Unsloth Trainer - Standalone LLM Fine-tuning Script

Fine-tune LLM models using LoRA with data from Continuum parquet files.
Uses Unsloth for accelerated training on Linux+CUDA, falls back to
standard HuggingFace transformers on other platforms.

Requirements:
    pip install pyarrow pandas datasets torch transformers peft trl accelerate

For Unsloth acceleration (Linux + CUDA only):
    pip install "unsloth[colab-new] @ git+https://github.com/unslothai/unsloth.git"

Usage:
    python train.py --data input.parquet --model microsoft/phi-2 --output ./output

Examples:
    # Basic fine-tuning
    python train.py -d data.parquet -m microsoft/phi-2 -o ./my-model

    # Custom training parameters
    python train.py -d data.parquet -m mistralai/Mistral-7B-v0.3 -o ./my-model \\
        --epochs 3 --batch-size 4 --learning-rate 2e-4

    # Custom column names for instruction tuning
    python train.py -d data.parquet -m microsoft/phi-2 -o ./my-model \\
        --input-column question --output-column answer

    # Silent mode (no output)
    python train.py -d data.parquet -m microsoft/phi-2 -o ./my-model --silent

    # List available columns in parquet file
    python train.py -d data.parquet -m x -o x --list-columns

Supported Parquet Formats:
    1. Continuum DataRow format (with cells array containing name, value, contentType)
    2. Standard columnar parquet (columns directly accessible)

Content Types (Continuum format):
    application/vnd.continuum.x-string   -> str
    application/vnd.continuum.x-int      -> int
    application/vnd.continuum.x-long     -> int
    application/vnd.continuum.x-float    -> float
    application/vnd.continuum.x-double   -> float
    application/vnd.continuum.x-boolean  -> bool
    application/json                     -> dict/list
"""

from __future__ import annotations

# =============================================================================
# EARLY SILENT MODE DETECTION
# =============================================================================
# This section MUST come before any other imports. When --silent or -s is passed,
# we need to suppress all output including warnings from libraries like urllib3,
# transformers, etc. that print during import. By redirecting stdout/stderr to
# /dev/null early, we prevent any output from appearing.

import sys
import os

# Check if silent mode is requested by looking at command line arguments
# We do this before argparse because we need to suppress output during imports
_silent = "--silent" in sys.argv or "-s" in sys.argv

if _silent:
    # Suppress Python warnings (e.g., DeprecationWarning, FutureWarning)
    import warnings
    warnings.filterwarnings("ignore")

    # Set environment variables to suppress library-specific output
    # These must be set BEFORE importing the libraries
    os.environ["PYTHONWARNINGS"] = "ignore"              # General Python warnings
    os.environ["TRANSFORMERS_VERBOSITY"] = "error"       # HuggingFace transformers logging
    os.environ["DATASETS_VERBOSITY"] = "error"           # HuggingFace datasets logging
    os.environ["TOKENIZERS_PARALLELISM"] = "false"       # Suppress tokenizer warnings
    os.environ["TQDM_DISABLE"] = "1"                     # Disable progress bars
    os.environ["HF_DATASETS_DISABLE_PROGRESS_BARS"] = "1"  # Disable dataset progress bars

    # Redirect stdout and stderr to /dev/null (null device)
    # This catches any remaining output that libraries might produce
    _devnull = open(os.devnull, "w")
    sys.stdout = _devnull
    sys.stderr = _devnull

# =============================================================================
# STANDARD IMPORTS
# =============================================================================
# Now we can safely import other modules. Any warnings they produce will be
# suppressed if silent mode is enabled.

import argparse    # For parsing command-line arguments
import json        # For parsing JSON content types
import logging     # For controlling log output
from pathlib import Path           # For file path handling
from typing import Any, Optional   # For type hints

# =============================================================================
# CONSTANTS
# =============================================================================

# Version number - update this when making changes
VERSION = "1.0.0"

# Content type converters for Continuum DataRow format
# The Continuum workflow system stores data in a generic format where each cell
# has a name, a byte value, and a content type. This dictionary maps content
# types to functions that convert the string value to the appropriate Python type.
#
# For example:
#   - "application/vnd.continuum.x-int" means the bytes should be parsed as an integer
#   - "application/json" means the bytes contain JSON that should be parsed
CONTENT_TYPE_CONVERTERS = {
    "application/vnd.continuum.x-string": lambda v: v,                    # Keep as string
    "application/vnd.continuum.x-int": lambda v: int(v),                  # Parse as integer
    "application/vnd.continuum.x-long": lambda v: int(v),                 # Parse as long (Python uses int)
    "application/vnd.continuum.x-float": lambda v: float(v),              # Parse as float
    "application/vnd.continuum.x-double": lambda v: float(v),             # Parse as double (Python uses float)
    "application/vnd.continuum.x-boolean": lambda v: v.lower() == "true", # Parse as boolean
    "application/json": lambda v: json.loads(v),                          # Parse as JSON object/array
}


# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

def log(message: str) -> None:
    """
    Print a message to the console, but only if not in silent mode.

    This is a wrapper around print() that respects the --silent flag.
    All user-facing output in this script should use log() instead of print()
    so that silent mode works correctly.

    Args:
        message: The message to print
    """
    if not _silent:
        print(message)


def check_dependencies() -> None:
    """
    Verify that all required Python packages are installed.

    This function attempts to import each required package and collects
    any that are missing. If any packages are missing, it prints an error
    message with installation instructions and exits.

    Required packages:
        - pyarrow: For reading parquet files
        - pandas: For data manipulation (used by pyarrow)
        - datasets: HuggingFace datasets library for formatting training data
        - torch: PyTorch deep learning framework
        - transformers: HuggingFace transformers for loading pre-trained models
        - peft: Parameter-Efficient Fine-Tuning library for LoRA
        - trl: Transformer Reinforcement Learning library for SFTTrainer
    """
    missing = []

    # Try importing each package and track which ones fail
    try:
        import pyarrow
    except ImportError:
        missing.append("pyarrow")
    try:
        import pandas
    except ImportError:
        missing.append("pandas")
    try:
        import datasets
    except ImportError:
        missing.append("datasets")
    try:
        import torch
    except ImportError:
        missing.append("torch")
    try:
        import transformers
    except ImportError:
        missing.append("transformers")
    try:
        import peft
    except ImportError:
        missing.append("peft")
    try:
        import trl
    except ImportError:
        missing.append("trl")

    # If any packages are missing, show error and exit
    if missing:
        log(f"Error: Missing dependencies: {', '.join(missing)}")
        log(f"Install with: pip install {' '.join(missing)}")
        sys.exit(1)


# =============================================================================
# DATA LOADING FUNCTIONS
# =============================================================================
# These functions handle loading data from parquet files. The script supports
# two formats:
#
# 1. Continuum DataRow format: Used by the Continuum workflow system
#    - Each row has a "rowNumber" and a "cells" array
#    - Each cell has "name", "value" (bytes), and "contentType"
#    - Example: {"rowNumber": 0, "cells": [{"name": "text", "value": b"hello", "contentType": "...x-string"}]}
#
# 2. Standard columnar parquet: Regular parquet files with named columns
#    - Columns are directly accessible by name
#    - Example: {"instruction": "What is 2+2?", "response": "4"}

def decode_cell_value(value_bytes: bytes, content_type: str) -> Any:
    """
    Convert a byte value to the appropriate Python type based on content type.

    In Continuum DataRow format, all values are stored as bytes. The content type
    tells us how to interpret those bytes. For example:
        - "x-string" content type: bytes are UTF-8 text, keep as string
        - "x-int" content type: bytes are a number string like "42", convert to int
        - "json" content type: bytes are JSON, parse into dict/list

    Args:
        value_bytes: The raw byte value from the parquet file
        content_type: The MIME-like content type string (e.g., "application/vnd.continuum.x-int")

    Returns:
        The value converted to the appropriate Python type

    Raises:
        ValueError: If the content type is not recognized

    Example:
        >>> decode_cell_value(b"42", "application/vnd.continuum.x-int")
        42
        >>> decode_cell_value(b"true", "application/vnd.continuum.x-boolean")
        True
    """
    # First, decode the bytes to a UTF-8 string
    value_str = value_bytes.decode("utf-8")

    # Look up the converter function for this content type
    converter = CONTENT_TYPE_CONVERTERS.get(content_type)
    if converter is None:
        raise ValueError(f"Unsupported content type: {content_type}")

    # Apply the converter to get the final value
    return converter(value_str)


def parse_continuum_row(row: dict) -> dict:
    """
    Parse a single Continuum DataRow record into a flat Python dictionary.

    A Continuum DataRow looks like:
    {
        "rowNumber": 0,
        "cells": [
            {"name": "instruction", "value": b"What is 2+2?", "contentType": "...x-string"},
            {"name": "response", "value": b"4", "contentType": "...x-string"}
        ]
    }

    This function converts it to:
    {"instruction": "What is 2+2?", "response": "4"}

    Args:
        row: A dictionary containing "cells" array with name/value/contentType entries

    Returns:
        A flat dictionary mapping column names to their converted values
    """
    result = {}

    # Iterate through each cell in the row
    for cell in row["cells"]:
        name = cell["name"]              # Column name (e.g., "instruction")
        value_bytes = cell["value"]      # Raw byte value
        content_type = cell["contentType"]  # Type indicator for conversion

        # Convert the bytes to the appropriate type and store with the column name
        result[name] = decode_cell_value(value_bytes, content_type)

    return result


def load_parquet_dataset(parquet_path: str) -> list:
    """
    Load a parquet file and convert its contents to a list of dictionaries.

    This function automatically detects whether the parquet file is in
    Continuum DataRow format or standard columnar format and handles
    each appropriately.

    Continuum DataRow format detection:
        - File has "rowNumber" and "cells" columns
        - Each row's "cells" is an array of {name, value, contentType} objects

    Standard columnar format:
        - Columns are directly named (e.g., "instruction", "response")
        - Values are directly readable without conversion

    Args:
        parquet_path: Path to the parquet file

    Returns:
        A list of dictionaries, where each dictionary represents one row
        with column names as keys and converted values as values.

    Example output:
        [
            {"instruction": "What is 2+2?", "response": "4"},
            {"instruction": "What is the capital of France?", "response": "Paris"}
        ]
    """
    import pyarrow.parquet as pq

    # Read the parquet file into a PyArrow table
    table = pq.read_table(parquet_path)

    # Convert to a dictionary of columns {column_name: [values]}
    records = table.to_pydict()

    # Check if this is Continuum DataRow format by looking for the characteristic columns
    if "rowNumber" in records and "cells" in records:
        # This is Continuum DataRow format
        # We need to parse each row's cells array
        num_rows = len(records["rowNumber"])
        rows = []

        for i in range(num_rows):
            # Reconstruct the row structure that parse_continuum_row expects
            row = {
                "rowNumber": records["rowNumber"][i],
                "cells": records["cells"][i],
            }
            # Parse the Continuum format row into a flat dictionary
            rows.append(parse_continuum_row(row))

        return rows
    else:
        # This is standard columnar parquet format
        # Convert to pandas DataFrame and then to list of dicts
        # This is simpler because columns are already named and typed
        df = table.to_pandas()
        return df.to_dict("records")


def format_dataset(
    data: list,
    input_column: str,
    output_column: str,
    system_prompt: Optional[str] = None,
):
    """
    Format raw data into a HuggingFace Dataset ready for instruction fine-tuning.

    This function takes the loaded data and formats it into a specific prompt
    template that teaches the model to follow instructions. The format is:

        ### Instruction:
        {user's instruction}

        ### Response:
        {expected response}

    Optionally, a system prompt can be prepended:

        ### System:
        {system prompt}

        ### Instruction:
        {user's instruction}

        ### Response:
        {expected response}

    Args:
        data: List of dictionaries, each containing at least input_column and output_column
        input_column: Name of the column containing instructions (default: "instruction")
        output_column: Name of the column containing responses (default: "response")
        system_prompt: Optional system prompt to prepend to all examples

    Returns:
        A HuggingFace Dataset with a single "text" column containing formatted examples

    Raises:
        ValueError: If input_column or output_column is not found in the data
    """
    from datasets import Dataset

    formatted_data = []

    for row in data:
        # Validate that required columns exist
        if input_column not in row or output_column not in row:
            available_cols = list(row.keys())
            raise ValueError(
                f"Columns '{input_column}' or '{output_column}' not found. "
                f"Available columns: {available_cols}"
            )

        # Build the instruction-response format
        # This format is commonly used for instruction fine-tuning
        text = f"### Instruction:\n{row[input_column]}\n\n### Response:\n{row[output_column]}"

        # Optionally prepend system prompt
        if system_prompt:
            text = f"### System:\n{system_prompt}\n\n{text}"

        # Add to our formatted dataset
        formatted_data.append({"text": text})

    # Convert to HuggingFace Dataset format
    return Dataset.from_list(formatted_data)


# =============================================================================
# TRAINING FUNCTION
# =============================================================================
# This is the main training logic. It handles:
# 1. Loading the data from parquet
# 2. Formatting it for instruction tuning
# 3. Loading the base model (with Unsloth acceleration if available)
# 4. Configuring LoRA adapters for efficient fine-tuning
# 5. Running the training loop
# 6. Saving the fine-tuned model

def train(
    data_path: str,
    model_name: str,
    output_path: str,
    input_column: str = "instruction",
    output_column: str = "response",
    system_prompt: Optional[str] = None,
    max_seq_length: int = 2048,
    epochs: int = 1,
    batch_size: int = 2,
    gradient_accumulation_steps: int = 4,
    learning_rate: float = 2e-4,
    lora_r: int = 16,
    lora_alpha: int = 16,
    lora_dropout: float = 0.0,
    warmup_steps: int = 5,
    weight_decay: float = 0.01,
    seed: int = 42,
    save_steps: int = 100,
    logging_steps: int = 10,
    use_4bit: bool = True,
    push_to_hub: bool = False,
    hub_model_id: Optional[str] = None,
) -> None:
    """
    Run the complete fine-tuning pipeline.

    This function orchestrates the entire training process:

    1. PLATFORM DETECTION: Checks if Unsloth can be used (requires Linux + CUDA GPU)
       - Unsloth provides 2x faster training and 60% less memory usage
       - Falls back to standard HuggingFace on macOS, Windows, or CPU-only systems

    2. DATA LOADING: Reads the parquet file and converts to training format
       - Supports both Continuum DataRow format and standard parquet
       - Formats data into instruction-response pairs

    3. MODEL LOADING: Loads the base language model
       - Uses 4-bit quantization by default to reduce memory usage
       - Automatically selects the best available device (CUDA > MPS > CPU)

    4. LORA CONFIGURATION: Sets up Low-Rank Adaptation for efficient training
       - LoRA only trains a small number of adapter parameters (~0.3% of model)
       - This makes fine-tuning possible on consumer GPUs
       - Key parameters: r (rank), alpha (scaling), dropout

    5. TRAINING: Runs the supervised fine-tuning loop
       - Uses HuggingFace's SFTTrainer for instruction tuning
       - Saves checkpoints periodically

    6. SAVING: Saves the fine-tuned model
       - Saves LoRA adapter weights (small, ~30MB)
       - Optionally saves merged model (full weights, several GB)

    Args:
        data_path: Path to the input parquet file
        model_name: HuggingFace model identifier (e.g., "microsoft/phi-2")
        output_path: Directory to save the fine-tuned model
        input_column: Column name for instruction text
        output_column: Column name for response text
        system_prompt: Optional system prompt for all examples
        max_seq_length: Maximum token sequence length (longer = more memory)
        epochs: Number of training epochs (full passes through data)
        batch_size: Samples per training step (higher = more memory, faster)
        gradient_accumulation_steps: Accumulate gradients over N steps (simulates larger batch)
        learning_rate: How fast the model learns (too high = unstable, too low = slow)
        lora_r: LoRA rank - higher = more parameters = more capacity but slower
        lora_alpha: LoRA scaling factor - typically set equal to lora_r
        lora_dropout: Dropout for LoRA layers (0.0 = no dropout)
        warmup_steps: Gradually increase learning rate for N steps at start
        weight_decay: L2 regularization to prevent overfitting
        seed: Random seed for reproducibility
        save_steps: Save checkpoint every N steps
        logging_steps: Log metrics every N steps
        use_4bit: Use 4-bit quantization to reduce memory (recommended)
        push_to_hub: Upload model to HuggingFace Hub after training
        hub_model_id: HuggingFace Hub model ID for uploading
    """
    # Import heavy dependencies inside the function to speed up CLI parsing
    # This way, --help and --list-columns don't need to load PyTorch
    import platform
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments
    from peft import LoraConfig, get_peft_model, TaskType
    from trl import SFTTrainer, SFTConfig

    # =========================================================================
    # STEP 1: Platform Detection and Unsloth Availability
    # =========================================================================
    # Unsloth is a library that provides significant speedups for LLM training,
    # but it only works on Linux with NVIDIA GPUs (CUDA). On other platforms,
    # we fall back to standard HuggingFace transformers.

    use_unsloth = False

    if platform.system() == "Linux" and torch.cuda.is_available():
        # We're on Linux with a CUDA GPU - try to use Unsloth
        try:
            from unsloth import FastLanguageModel
            use_unsloth = True
            log("Using Unsloth for accelerated training")
        except ImportError:
            # Unsloth not installed, fall back to standard training
            log("Unsloth not available, using standard HuggingFace transformers")
    else:
        # Not on Linux+CUDA, explain why we're using standard training
        log(f"Platform: {platform.system()}, CUDA: {torch.cuda.is_available()}")
        log("Using standard HuggingFace transformers (Unsloth requires Linux + CUDA)")

    # =========================================================================
    # STEP 2: Load and Format Training Data
    # =========================================================================

    log(f"\nLoading data from {data_path}...")
    raw_data = load_parquet_dataset(data_path)
    log(f"Loaded {len(raw_data)} records")

    # Show available columns to help users identify the right column names
    if raw_data:
        log(f"Available columns: {list(raw_data[0].keys())}")

    # Format the data into instruction-response pairs
    log(f"\nFormatting dataset with input='{input_column}', output='{output_column}'...")
    dataset = format_dataset(raw_data, input_column, output_column, system_prompt)
    log(f"Dataset prepared with {len(dataset)} examples")

    # Show a preview of the first training example so users can verify formatting
    log("\n--- First training example ---")
    log(dataset[0]["text"][:500])
    log("..." if len(dataset[0]["text"]) > 500 else "")
    log("---\n")

    # Create output directory if it doesn't exist
    output_dir = Path(output_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    # =========================================================================
    # STEP 3: Load Model and Tokenizer
    # =========================================================================
    # The approach differs based on whether Unsloth is available:
    # - With Unsloth: Use FastLanguageModel for optimized loading
    # - Without Unsloth: Use standard AutoModelForCausalLM

    if use_unsloth:
        # ----- UNSLOTH PATH (Linux + CUDA) -----
        # Unsloth provides optimized model loading and training
        from unsloth import FastLanguageModel

        log(f"Loading model: {model_name}...")

        # Load model with Unsloth's optimizations
        # - max_seq_length: Maximum tokens the model can process
        # - load_in_4bit: Use 4-bit quantization to reduce VRAM usage
        # - dtype: Let Unsloth auto-detect the best dtype
        model, tokenizer = FastLanguageModel.from_pretrained(
            model_name=model_name,
            max_seq_length=max_seq_length,
            load_in_4bit=use_4bit,
            dtype=None,  # Auto-detect best dtype
        )

        log("Configuring LoRA adapters...")

        # Configure LoRA (Low-Rank Adaptation) for efficient fine-tuning
        # Instead of training all model weights, LoRA adds small adapter layers
        # that capture task-specific knowledge with much fewer parameters
        model = FastLanguageModel.get_peft_model(
            model,
            r=lora_r,           # Rank of the low-rank matrices
            lora_alpha=lora_alpha,  # Scaling factor for LoRA weights
            lora_dropout=lora_dropout,
            # Target modules: These are the attention layers we'll adapt
            # Different models may have different layer names
            target_modules=[
                "q_proj", "k_proj", "v_proj", "o_proj",  # Attention projections
                "gate_proj", "up_proj", "down_proj",     # MLP projections
            ],
            bias="none",  # Don't train bias terms
            use_gradient_checkpointing="unsloth",  # Memory optimization
            random_state=seed,
        )
    else:
        # ----- STANDARD HUGGINGFACE PATH (macOS, Windows, CPU) -----
        log(f"Loading model: {model_name}...")

        # Determine the best available device and data type
        # Priority: CUDA GPU > Apple MPS > CPU
        if torch.backends.mps.is_available():
            # Apple Silicon Mac with Metal Performance Shaders
            device = "mps"
            dtype = torch.float16  # MPS supports float16
            log("Using Apple Metal (MPS) acceleration")
        elif torch.cuda.is_available():
            # NVIDIA GPU (but Unsloth not available for some reason)
            device = "cuda"
            dtype = torch.float16
            log("Using CUDA acceleration")
        else:
            # CPU only - training will be slow
            device = "cpu"
            dtype = torch.float32  # CPU typically uses float32
            log("Using CPU (training will be slow)")

        # Load the tokenizer (converts text to token IDs)
        tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)

        # Ensure tokenizer has a padding token (needed for batching)
        # If not set, use the end-of-sequence token
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token

        # Load the model with appropriate settings
        model = AutoModelForCausalLM.from_pretrained(
            model_name,
            torch_dtype=dtype,
            trust_remote_code=True,  # Allow custom model code
            # device_map="auto" automatically distributes model across devices
            # But MPS doesn't support this, so we handle it manually
            device_map="auto" if device != "mps" else None,
        )

        # For MPS, manually move model to the device
        if device == "mps":
            model = model.to(device)

        log("Configuring LoRA adapters...")

        # Configure LoRA using the PEFT library
        lora_config = LoraConfig(
            r=lora_r,               # Rank of LoRA matrices
            lora_alpha=lora_alpha,  # Scaling factor
            lora_dropout=lora_dropout,
            # Target only the attention projection layers for standard path
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
            bias="none",
            task_type=TaskType.CAUSAL_LM,  # We're doing causal language modeling
        )

        # Apply LoRA configuration to the model
        model = get_peft_model(model, lora_config)

        # Print how many parameters are trainable vs total
        # LoRA typically trains only ~0.3% of parameters
        if not _silent:
            model.print_trainable_parameters()

    # =========================================================================
    # STEP 4: Configure Training
    # =========================================================================

    log("Initializing trainer...")

    # SFTConfig (Supervised Fine-Tuning Config) contains all training settings
    # This is the TRL library's way of configuring the SFTTrainer
    sft_config = SFTConfig(
        output_dir=str(output_dir),           # Where to save checkpoints
        num_train_epochs=epochs,              # How many times to go through all data
        per_device_train_batch_size=batch_size,  # Samples per training step
        gradient_accumulation_steps=gradient_accumulation_steps,  # Simulate larger batches
        learning_rate=learning_rate,          # Step size for optimizer
        warmup_steps=warmup_steps,            # Gradually increase LR at start
        weight_decay=weight_decay,            # L2 regularization
        logging_strategy="no" if _silent else "steps",  # When to log metrics
        logging_steps=logging_steps,          # Log every N steps
        save_steps=save_steps,                # Save checkpoint every N steps
        save_total_limit=3,                   # Keep only last 3 checkpoints
        seed=seed,                            # Random seed for reproducibility
        max_length=max_seq_length,            # Maximum sequence length
        dataset_text_field="text",            # Column name in our formatted dataset
        report_to="none",                     # Don't report to wandb/tensorboard
        dataloader_pin_memory=False,          # Compatibility setting
        gradient_checkpointing=False,         # Trade compute for memory
        disable_tqdm=_silent,                 # Disable progress bars in silent mode
    )

    # Create the trainer
    # SFTTrainer is specialized for supervised fine-tuning of language models
    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,  # Tokenizer for text processing
        train_dataset=dataset,       # Our formatted dataset
        args=sft_config,             # Training configuration
    )

    # =========================================================================
    # STEP 5: Run Training
    # =========================================================================

    log("\n" + "=" * 50)
    log("Starting training...")
    log("=" * 50 + "\n")

    # This is where the actual training happens!
    # The trainer will:
    # 1. Iterate through the dataset for the specified number of epochs
    # 2. Compute loss on each batch
    # 3. Backpropagate gradients
    # 4. Update model weights
    # 5. Save checkpoints periodically
    trainer.train()

    # =========================================================================
    # STEP 6: Save the Fine-tuned Model
    # =========================================================================

    log("\nSaving model...")

    # Save the LoRA adapter weights
    # This is a small file (~30MB) containing only the trained adapter parameters
    # To use it later, you load the base model and then load these adapters
    final_path = output_dir / "final"
    model.save_pretrained(str(final_path))
    tokenizer.save_pretrained(str(final_path))
    log(f"Model saved to {final_path}")

    # If using Unsloth, also save a merged model
    # This combines the base model with LoRA adapters into a single model
    # It's larger but easier to use (no need to load adapters separately)
    if use_unsloth:
        merged_path = output_dir / "merged"
        log(f"\nSaving merged model to {merged_path}...")
        model.save_pretrained_merged(str(merged_path), tokenizer, save_method="merged_16bit")
        log(f"  Merged model: {merged_path}")

    # Optionally push to HuggingFace Hub for sharing
    if push_to_hub and hub_model_id:
        log(f"\nPushing to Hub: {hub_model_id}...")
        model.push_to_hub(hub_model_id)
        tokenizer.push_to_hub(hub_model_id)

    # Print summary
    log("\n" + "=" * 50)
    log("Training complete!")
    log("=" * 50)
    log(f"\nOutputs:")
    log(f"  LoRA adapter: {final_path}")


# =============================================================================
# COMMAND-LINE INTERFACE
# =============================================================================
# This section handles parsing command-line arguments and dispatching to the
# appropriate functions. It's the entry point when running the script directly.

def main() -> None:
    """
    Main entry point for the CLI.

    Parses command-line arguments and either:
    - Lists columns in the parquet file (--list-columns)
    - Runs the training pipeline
    """
    # Create argument parser with description and examples
    parser = argparse.ArgumentParser(
        description="Fine-tune LLM models with LoRA using Continuum parquet data",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,  # Use the module docstring as epilog
    )

    # -------------------------------------------------------------------------
    # Required Arguments
    # -------------------------------------------------------------------------
    parser.add_argument(
        "--data", "-d",
        required=True,
        help="Path to input parquet file",
    )
    parser.add_argument(
        "--model", "-m",
        required=True,
        help="Model name/path (e.g., microsoft/phi-2, mistralai/Mistral-7B-v0.3)",
    )
    parser.add_argument(
        "--output", "-o",
        required=True,
        help="Output directory for the fine-tuned model",
    )

    # -------------------------------------------------------------------------
    # Data Formatting Options
    # -------------------------------------------------------------------------
    parser.add_argument(
        "--input-column",
        default="instruction",
        help="Column name for input/instruction text (default: instruction)",
    )
    parser.add_argument(
        "--output-column",
        default="response",
        help="Column name for output/response text (default: response)",
    )
    parser.add_argument(
        "--system-prompt",
        help="Optional system prompt to prepend to all examples",
    )

    # -------------------------------------------------------------------------
    # Training Hyperparameters
    # These control how the model learns
    # -------------------------------------------------------------------------
    parser.add_argument("--epochs", type=int, default=1,
        help="Number of training epochs (default: 1)")
    parser.add_argument("--batch-size", type=int, default=2,
        help="Per-device batch size (default: 2)")
    parser.add_argument("--gradient-accumulation", type=int, default=4,
        help="Gradient accumulation steps (default: 4)")
    parser.add_argument("--learning-rate", type=float, default=2e-4,
        help="Learning rate (default: 2e-4)")
    parser.add_argument("--max-seq-length", type=int, default=2048,
        help="Maximum sequence length (default: 2048)")
    parser.add_argument("--warmup-steps", type=int, default=5,
        help="Warmup steps (default: 5)")
    parser.add_argument("--weight-decay", type=float, default=0.01,
        help="Weight decay (default: 0.01)")

    # -------------------------------------------------------------------------
    # LoRA Configuration
    # These control the adapter architecture
    # -------------------------------------------------------------------------
    parser.add_argument("--lora-r", type=int, default=16,
        help="LoRA rank - higher = more capacity (default: 16)")
    parser.add_argument("--lora-alpha", type=int, default=16,
        help="LoRA alpha scaling factor (default: 16)")
    parser.add_argument("--lora-dropout", type=float, default=0.0,
        help="LoRA dropout rate (default: 0.0)")

    # -------------------------------------------------------------------------
    # Other Options
    # -------------------------------------------------------------------------
    parser.add_argument("--seed", type=int, default=42,
        help="Random seed for reproducibility (default: 42)")
    parser.add_argument("--save-steps", type=int, default=100,
        help="Save checkpoint every N steps (default: 100)")
    parser.add_argument("--logging-steps", type=int, default=10,
        help="Log metrics every N steps (default: 10)")
    parser.add_argument("--no-4bit", action="store_true",
        help="Disable 4-bit quantization (uses more memory)")
    parser.add_argument("--push-to-hub", action="store_true",
        help="Push model to HuggingFace Hub after training")
    parser.add_argument("--hub-model-id",
        help="HuggingFace Hub model ID for uploading")
    parser.add_argument("--list-columns", action="store_true",
        help="List available columns in the parquet file and exit")
    parser.add_argument("--silent", "-s", action="store_true",
        help="Silent mode - suppress all output")
    parser.add_argument("--version", "-v", action="version",
        version=f"%(prog)s {VERSION}")

    # Parse the command-line arguments
    args = parser.parse_args()

    # Ensure logging is fully disabled in silent mode
    if _silent:
        logging.disable(logging.CRITICAL)

    # Validate that the input file exists
    data_path = Path(args.data)
    if not data_path.exists():
        if not _silent:
            print(f"Error: Input file not found: {data_path}")
        sys.exit(1)

    # Handle --list-columns mode: just show columns and exit
    if args.list_columns:
        check_dependencies()
        raw_data = load_parquet_dataset(str(data_path))
        if raw_data:
            print("Available columns:")
            for col in raw_data[0].keys():
                # Show column name and a preview of the first value
                sample_value = str(raw_data[0][col])[:50]
                print(f"  - {col}: {sample_value}...")
        sys.exit(0)

    # Check that all required packages are installed before training
    check_dependencies()

    # Run the training pipeline with all the parsed arguments
    train(
        data_path=str(data_path),
        model_name=args.model,
        output_path=args.output,
        input_column=args.input_column,
        output_column=args.output_column,
        system_prompt=args.system_prompt,
        max_seq_length=args.max_seq_length,
        epochs=args.epochs,
        batch_size=args.batch_size,
        gradient_accumulation_steps=args.gradient_accumulation,
        learning_rate=args.learning_rate,
        lora_r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        warmup_steps=args.warmup_steps,
        weight_decay=args.weight_decay,
        seed=args.seed,
        save_steps=args.save_steps,
        logging_steps=args.logging_steps,
        use_4bit=not args.no_4bit,
        push_to_hub=args.push_to_hub,
        hub_model_id=args.hub_model_id,
    )


# =============================================================================
# SCRIPT ENTRY POINT
# =============================================================================
# This is the standard Python idiom for making a file both importable and runnable.
# When run directly (python train.py), __name__ is "__main__" and main() is called.
# When imported (import train), __name__ is "train" and main() is not called.

if __name__ == "__main__":
    main()
