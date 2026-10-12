import json
import os
import glob

def format_telemetry_for_training(
    ml_dir="baton-ml",
    output_file="baton-ml/baton_pytorch_dataset.json"
):
    """
    Ingests all recorded Day-1 telemetry datasets (live bridge events, mobile telemetry,
    and security commits) and formats them into an Alpaca/ShareGPT instruction-tuning
    dataset for LoRA fine-tuning.
    """
    if not os.path.exists(ml_dir):
        ml_dir = "."

    jsonl_files = glob.glob(os.path.join(ml_dir, "*.jsonl"))
    formatted_entries = []

    print(f"[DATASET-FORMATTER] Scanning for Day-1 telemetry datasets in: {ml_dir}")
    print(f"[DATASET-FORMATTER] Found files: {jsonl_files}")

    for file_path in jsonl_files:
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                for line in f:
                    if not line.strip():
                        continue
                    try:
                        entry = json.loads(line)
                        # Check if sample is already in instruction format (live telemetry)
                        if "instruction" in entry and "input" in entry and "output" in entry:
                            formatted_entries.append({
                                "instruction": entry["instruction"],
                                "input": entry["input"],
                                "output": entry["output"]
                            })
                        # Check if sample is a git diff security patch
                        elif "diff" in entry:
                            subject = entry.get("subject", "Security patch")
                            diff_text = entry.get("diff", "")[:2000] # Cap length per sample
                            formatted_entries.append({
                                "instruction": f"Analyze this security patch commit and summarize the mitigation: {subject}",
                                "input": diff_text,
                                "output": f"Summary: Commit '{subject}' applies critical hardening to resolve vulnerabilities and stabilize architecture."
                            })
                        else:
                            level = entry.get("level", "WARN")
                            msg = entry.get("msg", "")
                            formatted_entries.append({
                                "instruction": "Analyze the following system security telemetry log and classify the threat level and recommended mitigation.",
                                "input": f"[{level}] {msg}",
                                "output": f"Threat Level: {level}. Recommendation: Analyze caller behavior, verify authentication, and isolate process if malicious."
                            })
                    except Exception:
                        continue
        except Exception as e:
            print(f"[DATASET-FORMATTER] Warning: Could not read {file_path}: {e}")

    if not formatted_entries:
        # Fallback baseline sample
        formatted_entries.append({
            "instruction": "Analyze the following security event log and recommend defensive remediation.",
            "input": "[CRITICAL] Detected path traversal attempt outside workspace jail.",
            "output": "Action: BLOCK_AND_ALERT. Terminate connection from untrusted origin and log security incident."
        })

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(formatted_entries, f, indent=2)

    print(f"[DATASET-FORMATTER] Successfully compiled {len(formatted_entries)} training samples into {output_file}")

if __name__ == "__main__":
    format_telemetry_for_training()
