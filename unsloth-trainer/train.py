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
# Early silent mode detection - must be before any other imports
# =============================================================================
import sys
import os

_silent = "--silent" in sys.argv or "-s" in sys.argv

if _silent:
    import warnings
    warnings.filterwarnings("ignore")
    os.environ["PYTHONWARNINGS"] = "ignore"
    os.environ["TRANSFORMERS_VERBOSITY"] = "error"
    os.environ["DATASETS_VERBOSITY"] = "error"
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    os.environ["TQDM_DISABLE"] = "1"
    os.environ["HF_DATASETS_DISABLE_PROGRESS_BARS"] = "1"
    _devnull = open(os.devnull, "w")
    sys.stdout = _devnull
    sys.stderr = _devnull

# =============================================================================
# Standard imports
# =============================================================================
import argparse
import json
import logging
from pathlib import Path
from typing import Any, Optional

# =============================================================================
# Constants
# =============================================================================
VERSION = "1.0.0"

CONTENT_TYPE_CONVERTERS = {
    "application/vnd.continuum.x-string": lambda v: v,
    "application/vnd.continuum.x-int": lambda v: int(v),
    "application/vnd.continuum.x-long": lambda v: int(v),
    "application/vnd.continuum.x-float": lambda v: float(v),
    "application/vnd.continuum.x-double": lambda v: float(v),
    "application/vnd.continuum.x-boolean": lambda v: v.lower() == "true",
    "application/json": lambda v: json.loads(v),
}


# =============================================================================
# Utility Functions
# =============================================================================
def log(message: str) -> None:
    """Print message only if not in silent mode."""
    if not _silent:
        print(message)


def check_dependencies() -> None:
    """Check if required dependencies are installed."""
    missing = []
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

    if missing:
        log(f"Error: Missing dependencies: {', '.join(missing)}")
        log(f"Install with: pip install {' '.join(missing)}")
        sys.exit(1)


# =============================================================================
# Data Loading Functions
# =============================================================================
def decode_cell_value(value_bytes: bytes, content_type: str) -> Any:
    """Convert bytes value to native Python type based on content type."""
    value_str = value_bytes.decode("utf-8")
    converter = CONTENT_TYPE_CONVERTERS.get(content_type)
    if converter is None:
        raise ValueError(f"Unsupported content type: {content_type}")
    return converter(value_str)


def parse_continuum_row(row: dict) -> dict:
    """Parse a Continuum DataRow record into a native Python dictionary."""
    result = {}
    for cell in row["cells"]:
        name = cell["name"]
        value_bytes = cell["value"]
        content_type = cell["contentType"]
        result[name] = decode_cell_value(value_bytes, content_type)
    return result


def load_parquet_dataset(parquet_path: str) -> list:
    """Load parquet file and convert records to native dicts.

    Supports both Continuum DataRow format and standard columnar parquet.
    """
    import pyarrow.parquet as pq

    table = pq.read_table(parquet_path)
    records = table.to_pydict()

    # Check if this is Continuum DataRow format
    if "rowNumber" in records and "cells" in records:
        num_rows = len(records["rowNumber"])
        rows = []
        for i in range(num_rows):
            row = {
                "rowNumber": records["rowNumber"][i],
                "cells": records["cells"][i],
            }
            rows.append(parse_continuum_row(row))
        return rows
    else:
        # Standard columnar parquet - convert to list of dicts
        df = table.to_pandas()
        return df.to_dict("records")


def format_dataset(
    data: list,
    input_column: str,
    output_column: str,
    system_prompt: Optional[str] = None,
):
    """Format dataset for instruction tuning."""
    from datasets import Dataset

    formatted_data = []

    for row in data:
        if input_column not in row or output_column not in row:
            available_cols = list(row.keys())
            raise ValueError(
                f"Columns '{input_column}' or '{output_column}' not found. "
                f"Available columns: {available_cols}"
            )

        text = f"### Instruction:\n{row[input_column]}\n\n### Response:\n{row[output_column]}"

        if system_prompt:
            text = f"### System:\n{system_prompt}\n\n{text}"

        formatted_data.append({"text": text})

    return Dataset.from_list(formatted_data)


# =============================================================================
# Training Function
# =============================================================================
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
    """Run the fine-tuning process."""
    import platform
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments
    from peft import LoraConfig, get_peft_model, TaskType
    from trl import SFTTrainer, SFTConfig

    # Check if we can use Unsloth (Linux + CUDA)
    use_unsloth = False
    if platform.system() == "Linux" and torch.cuda.is_available():
        try:
            from unsloth import FastLanguageModel
            use_unsloth = True
            log("Using Unsloth for accelerated training")
        except ImportError:
            log("Unsloth not available, using standard HuggingFace transformers")
    else:
        log(f"Platform: {platform.system()}, CUDA: {torch.cuda.is_available()}")
        log("Using standard HuggingFace transformers (Unsloth requires Linux + CUDA)")

    # Load data
    log(f"\nLoading data from {data_path}...")
    raw_data = load_parquet_dataset(data_path)
    log(f"Loaded {len(raw_data)} records")

    if raw_data:
        log(f"Available columns: {list(raw_data[0].keys())}")

    log(f"\nFormatting dataset with input='{input_column}', output='{output_column}'...")
    dataset = format_dataset(raw_data, input_column, output_column, system_prompt)
    log(f"Dataset prepared with {len(dataset)} examples")

    # Preview first example
    log("\n--- First training example ---")
    log(dataset[0]["text"][:500])
    log("..." if len(dataset[0]["text"]) > 500 else "")
    log("---\n")

    output_dir = Path(output_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load model and tokenizer
    if use_unsloth:
        from unsloth import FastLanguageModel

        log(f"Loading model: {model_name}...")
        model, tokenizer = FastLanguageModel.from_pretrained(
            model_name=model_name,
            max_seq_length=max_seq_length,
            load_in_4bit=use_4bit,
            dtype=None,
        )

        log("Configuring LoRA adapters...")
        model = FastLanguageModel.get_peft_model(
            model,
            r=lora_r,
            lora_alpha=lora_alpha,
            lora_dropout=lora_dropout,
            target_modules=[
                "q_proj", "k_proj", "v_proj", "o_proj",
                "gate_proj", "up_proj", "down_proj",
            ],
            bias="none",
            use_gradient_checkpointing="unsloth",
            random_state=seed,
        )
    else:
        log(f"Loading model: {model_name}...")

        # Determine device and dtype
        if torch.backends.mps.is_available():
            device = "mps"
            dtype = torch.float16
            log("Using Apple Metal (MPS) acceleration")
        elif torch.cuda.is_available():
            device = "cuda"
            dtype = torch.float16
            log("Using CUDA acceleration")
        else:
            device = "cpu"
            dtype = torch.float32
            log("Using CPU (training will be slow)")

        tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token

        model = AutoModelForCausalLM.from_pretrained(
            model_name,
            torch_dtype=dtype,
            trust_remote_code=True,
            device_map="auto" if device != "mps" else None,
        )

        if device == "mps":
            model = model.to(device)

        log("Configuring LoRA adapters...")
        lora_config = LoraConfig(
            r=lora_r,
            lora_alpha=lora_alpha,
            lora_dropout=lora_dropout,
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
            bias="none",
            task_type=TaskType.CAUSAL_LM,
        )
        model = get_peft_model(model, lora_config)
        if not _silent:
            model.print_trainable_parameters()

    # Configure training
    log("Initializing trainer...")

    sft_config = SFTConfig(
        output_dir=str(output_dir),
        num_train_epochs=epochs,
        per_device_train_batch_size=batch_size,
        gradient_accumulation_steps=gradient_accumulation_steps,
        learning_rate=learning_rate,
        warmup_steps=warmup_steps,
        weight_decay=weight_decay,
        logging_strategy="no" if _silent else "steps",
        logging_steps=logging_steps,
        save_steps=save_steps,
        save_total_limit=3,
        seed=seed,
        max_length=max_seq_length,
        dataset_text_field="text",
        report_to="none",
        dataloader_pin_memory=False,
        gradient_checkpointing=False,
        disable_tqdm=_silent,
    )

    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        train_dataset=dataset,
        args=sft_config,
    )

    # Train
    log("\n" + "=" * 50)
    log("Starting training...")
    log("=" * 50 + "\n")

    trainer.train()

    # Save model
    log("\nSaving model...")
    final_path = output_dir / "final"
    model.save_pretrained(str(final_path))
    tokenizer.save_pretrained(str(final_path))
    log(f"Model saved to {final_path}")

    # Save merged model if using Unsloth
    if use_unsloth:
        merged_path = output_dir / "merged"
        log(f"\nSaving merged model to {merged_path}...")
        model.save_pretrained_merged(str(merged_path), tokenizer, save_method="merged_16bit")
        log(f"  Merged model: {merged_path}")

    # Push to hub if requested
    if push_to_hub and hub_model_id:
        log(f"\nPushing to Hub: {hub_model_id}...")
        model.push_to_hub(hub_model_id)
        tokenizer.push_to_hub(hub_model_id)

    log("\n" + "=" * 50)
    log("Training complete!")
    log("=" * 50)
    log(f"\nOutputs:")
    log(f"  LoRA adapter: {final_path}")


# =============================================================================
# CLI Interface
# =============================================================================
def main() -> None:
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Fine-tune LLM models with LoRA using Continuum parquet data",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    # Required arguments
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

    # Data formatting
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

    # Training hyperparameters
    parser.add_argument("--epochs", type=int, default=1, help="Number of training epochs (default: 1)")
    parser.add_argument("--batch-size", type=int, default=2, help="Per-device batch size (default: 2)")
    parser.add_argument("--gradient-accumulation", type=int, default=4, help="Gradient accumulation steps (default: 4)")
    parser.add_argument("--learning-rate", type=float, default=2e-4, help="Learning rate (default: 2e-4)")
    parser.add_argument("--max-seq-length", type=int, default=2048, help="Maximum sequence length (default: 2048)")
    parser.add_argument("--warmup-steps", type=int, default=5, help="Warmup steps (default: 5)")
    parser.add_argument("--weight-decay", type=float, default=0.01, help="Weight decay (default: 0.01)")

    # LoRA configuration
    parser.add_argument("--lora-r", type=int, default=16, help="LoRA rank (default: 16)")
    parser.add_argument("--lora-alpha", type=int, default=16, help="LoRA alpha (default: 16)")
    parser.add_argument("--lora-dropout", type=float, default=0.0, help="LoRA dropout (default: 0.0)")

    # Other options
    parser.add_argument("--seed", type=int, default=42, help="Random seed (default: 42)")
    parser.add_argument("--save-steps", type=int, default=100, help="Save checkpoint every N steps (default: 100)")
    parser.add_argument("--logging-steps", type=int, default=10, help="Log every N steps (default: 10)")
    parser.add_argument("--no-4bit", action="store_true", help="Disable 4-bit quantization")
    parser.add_argument("--push-to-hub", action="store_true", help="Push model to HuggingFace Hub")
    parser.add_argument("--hub-model-id", help="HuggingFace Hub model ID for pushing")
    parser.add_argument("--list-columns", action="store_true", help="List available columns in the parquet file and exit")
    parser.add_argument("--silent", "-s", action="store_true", help="Silent mode - suppress all output")
    parser.add_argument("--version", "-v", action="version", version=f"%(prog)s {VERSION}")

    args = parser.parse_args()

    # Ensure logging is disabled in silent mode
    if _silent:
        logging.disable(logging.CRITICAL)

    # Validate input file
    data_path = Path(args.data)
    if not data_path.exists():
        if not _silent:
            print(f"Error: Input file not found: {data_path}")
        sys.exit(1)

    # List columns mode
    if args.list_columns:
        check_dependencies()
        raw_data = load_parquet_dataset(str(data_path))
        if raw_data:
            print("Available columns:")
            for col in raw_data[0].keys():
                sample_value = str(raw_data[0][col])[:50]
                print(f"  - {col}: {sample_value}...")
        sys.exit(0)

    # Check dependencies before training
    check_dependencies()

    # Run training
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


if __name__ == "__main__":
    main()
