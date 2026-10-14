# BATON Machine Learning & LoRA Fine-Tuning Hub (`baton-ml`)

Welcome to the **Baton ML & Model Optimization Hub**. This directory contains the dataset generators, telemetry ingestion engines, LoRA/QLoRA fine-tuning harnesses, and evaluation benchmarks used to train specialized on-device and desktop models for Baton.

---

## 🎯 Purpose & Capabilities

1. **Sovereign Persona & Guardrails**: Enforces that the model understands Baton's zero-cloud architecture, local SQLCipher storage, and hardware security enclaves.
2. **UGC Safety & Store Compliance**: Teaches the model to politely and firmly refuse malicious requests (ransomware, DDoS, hate speech, credential harvesting) in compliance with Apple App Store Guideline 1.2 and Google Play policies.
3. **Model Context Protocol (MCP) Tool Calling**: Trains compact foundation models (Qwen 2.5 1.5B/7B, Llama 3.2 1B/3B, SmolLM2 1.7B) to output precise, deterministic JSON tool calls for desktop bridge automation.
4. **Autonomous Threat Analysis**: Equips models to inspect security telemetry, classify threat events (SSRF, Path Traversal, Replay Attacks), and enforce mitigations.

---

## 🚀 Quick Start: Running the Complete Pipeline

Run all 4 steps (dataset generation, formatting, blueprint verification, and benchmark evaluation) in one command:

```bash
python run_pipeline.py
```

---

## 🛠️ Individual Tools & Scripts

### 1. Synthetic Dataset Generator
Generates high-diversity synthetic multi-turn conversations and single-turn instructions covering sovereign privacy, UGC moderation, tool calling, and incident response.

```bash
python synthetic_dataset_generator.py
```
- Outputs: `baton_synthetic_alpaca.jsonl` and `baton_synthetic_conversations.jsonl`.

### 2. Unified Dataset Formatter & Splitter
Ingests all `.jsonl` files (live host bridge telemetry, security diffs, and synthetic conversations), deduplicates them, and exports standardized datasets with a deterministic 90/10 train/validation split.

```bash
python dataset_formatter.py --val-ratio 0.1
```
- Outputs:
  - `baton_train.json` (Alpaca format for training)
  - `baton_val.json` (Alpaca format for evaluation)
  - `baton_sharegpt.json` (Multi-turn format)
  - `baton_pytorch_dataset.json` (Master dataset)

### 3. LoRA & QLoRA Fine-Tuning Pipeline
Supports 4-bit QLoRA (via bitsandbytes) and 16-bit LoRA with Hugging Face `transformers` and `peft`.

**Dry-Run / Verification Mode (no GPU required)**:
```bash
python train_lora.py --dry-run
```

**Full GPU Fine-Tuning (on NVIDIA workstation or cloud instance)**:
```bash
python train_lora.py --model Qwen/Qwen2.5-1.5B-Instruct --epochs 3 --batch-size 2 --rank 16
```

### 4. Benchmark & Safety Evaluation Harness
Evaluates model outputs against 8 rigorous behavioral test assertions spanning privacy, safety, JSON tool parsing, and threat classification.

```bash
# Run self-verification simulation
python eval_harness.py --simulate

# Run against a live local Ollama or LiteRT endpoint
python eval_harness.py --url http://localhost:11434/api/chat --model qwen2.5-coder:3b
```
- Outputs: `evaluation_report.md` with complete pass/fail scores and rationale.

---

## 📦 Exporting to GGUF for On-Device Deployment

Once fine-tuning completes, convert the merged model into GGUF format for use with `llama.cpp` or on-device mobile runtimes:

```bash
# 1. Convert to GGUF
python llama.cpp/convert_hf_to_gguf.py output/baton-sovereign-lora/ --outfile baton-1.5b-q4_k_m.gguf --outtype q4_k_m

# 2. Deploy to Baton Desktop Bridge or Mobile Asset directory
cp baton-1.5b-q4_k_m.gguf ../tools/baton-bridge/models/
```
