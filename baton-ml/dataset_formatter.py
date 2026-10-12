import json
import os

def format_telemetry_for_training(
    input_file="baton-ml/baton_vulnerability_dataset.jsonl",
    output_file="baton-ml/baton_pytorch_dataset.json"
):
    """
    Ingests recorded telemetry and vulnerability datasets and formats them into an
    Alpaca/ShareGPT instruction-tuning dataset for PyTorch / HuggingFace fine-tuning.
    """
    candidates = [
        input_file,
        "baton-ml/baton_vulnerability_dataset.jsonl",
        "gateway-engine/telemetry_dataset.jsonl"
    ]
    resolved_input = None
    for path in candidates:
        if os.path.exists(path):
            resolved_input = path
            break

    if not resolved_input:
        print(f"[DATASET-FORMATTER] No dataset found. Creating sample entry.")
        sample_data = [
            {
                "instruction": "Analyze the following security event log and recommend defensive remediation.",
                "input": "[CRITICAL] SBOM Scanner found vulnerabilities in crate 'openssl'",
                "output": "Action: Upgrade openssl dependency to version 0.10.60 to patch known memory corruption vulnerability."
            }
        ]
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(sample_data, f, indent=2)
        return

    formatted_entries = []
    with open(resolved_input, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
                if "diff" in entry:
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
                        "instruction": "Analyze the following gateway security telemetry log and classify the threat level and recommended patch.",
                        "input": f"[{level}] {msg}",
                        "output": f"Threat Level: {level}. Recommendation: Analyze source IP behavior, update crate dependencies if CVE, or block offending IP."
                    })
            except Exception as e:
                continue

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(formatted_entries, f, indent=2)
    print(f"[DATASET-FORMATTER] Successfully formatted {len(formatted_entries)} entries into {output_file}")

if __name__ == "__main__":
    format_telemetry_for_training()
