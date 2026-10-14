"""
BATON Unified Dataset Formatter & Splitter
=============================================================================
Consolidates all telemetry, git security diffs, and synthetic persona datasets
into standard machine learning training formats:
1. Alpaca Format: {"instruction": ..., "input": ..., "output": ...}
2. ShareGPT / ChatML Format: {"messages": [{"role": ..., "content": ...}]}
3. Train/Validation Split (Default 90% train, 10% val) with deterministic seed.
=============================================================================
"""

import json
import os
import glob
import random
import argparse

DEFAULT_SYSTEM_PROMPT = (
    "You are BATON, a Sovereign Personal AI Companion and Security Enclave Kernel. "
    "You operate locally on mobile and desktop devices. "
    "You prioritize privacy, end-to-end encryption, strict workspace containment, "
    "and clear ethical boundaries."
)

def format_telemetry_for_training(
    ml_dir=".",
    output_prefix="baton",
    val_ratio=0.1,
    random_seed=42
):
    if not os.path.exists(ml_dir):
        ml_dir = "."

    jsonl_files = glob.glob(os.path.join(ml_dir, "*.jsonl"))
    print(f"[DATASET-FORMATTER] Scanning datasets in: {os.path.abspath(ml_dir)}")
    print(f"[DATASET-FORMATTER] Detected files: {[os.path.basename(f) for f in jsonl_files]}")

    alpaca_entries = []
    sharegpt_entries = []

    for file_path in jsonl_files:
        filename = os.path.basename(file_path)
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                for line in f:
                    if not line.strip():
                        continue
                    try:
                        entry = json.loads(line)

                        # Check for multi-turn format (ShareGPT / ChatML)
                        if "messages" in entry:
                            sharegpt_entries.append(entry)
                            # Also derive an Alpaca sample from the last user/assistant turn
                            msgs = entry["messages"]
                            user_msg = next((m["content"] for m in reversed(msgs) if m["role"] == "user"), None)
                            asst_msg = next((m["content"] for m in reversed(msgs) if m["role"] == "assistant"), None)
                            if user_msg and asst_msg:
                                alpaca_entries.append({
                                    "instruction": user_msg,
                                    "input": "",
                                    "output": asst_msg
                                })
                            continue

                        # Check for single-turn Alpaca format
                        if "instruction" in entry and "output" in entry:
                            instruction = entry["instruction"]
                            inp = entry.get("input", "")
                            out = entry["output"]

                            alpaca_entries.append({
                                "instruction": instruction,
                                "input": inp,
                                "output": out
                            })

                            # Derive ShareGPT message list
                            user_content = f"{instruction}\n\nContext:\n{inp}".strip() if inp else instruction
                            sharegpt_entries.append({
                                "id": entry.get("id", f"sample-{len(sharegpt_entries)}"),
                                "messages": [
                                    {"role": "system", "content": DEFAULT_SYSTEM_PROMPT},
                                    {"role": "user", "content": user_content},
                                    {"role": "assistant", "content": out}
                                ]
                            })
                            continue

                        # Check for git vulnerability commit diff
                        if "diff" in entry:
                            subject = entry.get("subject", "Security patch")
                            diff_text = entry.get("diff", "")[:2500]
                            instruction = f"Analyze this security patch commit and summarize the mitigation: {subject}"
                            out = f"Summary: Commit '{subject}' applies critical hardening to resolve vulnerabilities and stabilize architecture."

                            alpaca_entries.append({
                                "instruction": instruction,
                                "input": diff_text,
                                "output": out
                            })
                            sharegpt_entries.append({
                                "id": f"commit-{entry.get('hash', 'patch')[:8]}",
                                "messages": [
                                    {"role": "system", "content": DEFAULT_SYSTEM_PROMPT},
                                    {"role": "user", "content": f"{instruction}\n\nDiff:\n{diff_text}"},
                                    {"role": "assistant", "content": out}
                                ]
                            })
                            continue

                        # Raw log entry
                        level = entry.get("level", "WARN")
                        msg = entry.get("msg", "")
                        if msg:
                            instruction = "Analyze the following system security telemetry log and classify the threat level and recommended mitigation."
                            inp = f"[{level}] {msg}"
                            out = f"Threat Level: {level}. Recommendation: Analyze caller behavior, verify authentication, and isolate process if malicious."
                            alpaca_entries.append({
                                "instruction": instruction,
                                "input": inp,
                                "output": out
                            })
                    except Exception:
                        continue
        except Exception as e:
            print(f"[DATASET-FORMATTER] Warning reading {file_path}: {e}")

    # Deduplicate Alpaca entries by instruction+input hash
    seen = set()
    deduped_alpaca = []
    for item in alpaca_entries:
        key = (item["instruction"].strip(), item.get("input", "").strip())
        if key not in seen:
            seen.add(key)
            deduped_alpaca.append(item)

    print(f"[DATASET-FORMATTER] Total compiled unique Alpaca samples: {len(deduped_alpaca)}")
    print(f"[DATASET-FORMATTER] Total compiled ShareGPT multi-turn samples: {len(sharegpt_entries)}")

    # Deterministic Split
    random.seed(random_seed)
    indices = list(range(len(deduped_alpaca)))
    random.shuffle(indices)

    split_point = int(len(deduped_alpaca) * (1 - val_ratio))
    train_indices = set(indices[:split_point])
    
    train_alpaca = [deduped_alpaca[i] for i in range(len(deduped_alpaca)) if i in train_indices]
    val_alpaca = [deduped_alpaca[i] for i in range(len(deduped_alpaca)) if i not in train_indices]

    # Save consolidated dataset (Alpaca)
    all_alpaca_path = os.path.join(ml_dir, f"{output_prefix}_pytorch_dataset.json")
    train_path = os.path.join(ml_dir, f"{output_prefix}_train.json")
    val_path = os.path.join(ml_dir, f"{output_prefix}_val.json")
    sharegpt_path = os.path.join(ml_dir, f"{output_prefix}_sharegpt.json")

    with open(all_alpaca_path, "w", encoding="utf-8") as f:
        json.dump(deduped_alpaca, f, indent=2)

    with open(train_path, "w", encoding="utf-8") as f:
        json.dump(train_alpaca, f, indent=2)

    with open(val_path, "w", encoding="utf-8") as f:
        json.dump(val_alpaca, f, indent=2)

    with open(sharegpt_path, "w", encoding="utf-8") as f:
        json.dump(sharegpt_entries, f, indent=2)

    print(f"[DATASET-FORMATTER] Master Dataset: {all_alpaca_path} ({len(deduped_alpaca)} samples)")
    print(f"[DATASET-FORMATTER] Train Set ({(1-val_ratio)*100:.0f}%): {train_path} ({len(train_alpaca)} samples)")
    print(f"[DATASET-FORMATTER] Val Set ({val_ratio*100:.0f}%): {val_path} ({len(val_alpaca)} samples)")
    print(f"[DATASET-FORMATTER] ShareGPT Multi-Turn: {sharegpt_path} ({len(sharegpt_entries)} samples)")

    return {
        "master": all_alpaca_path,
        "train": train_path,
        "val": val_path,
        "sharegpt": sharegpt_path,
        "total_count": len(deduped_alpaca)
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Baton Telemetry & Persona Dataset Formatter")
    parser.add_argument("--dir", default=".", help="Directory containing .jsonl dataset files")
    parser.add_argument("--prefix", default="baton", help="Output file prefix")
    parser.add_argument("--val-ratio", type=float, default=0.1, help="Validation set split ratio (default: 0.1)")
    args = parser.parse_args()

    format_telemetry_for_training(
        ml_dir=args.dir,
        output_prefix=args.prefix,
        val_ratio=args.val_ratio
    )
