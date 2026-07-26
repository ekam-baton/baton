import json
import os

def format_telemetry_for_training(input_file="gateway-engine/telemetry_dataset.jsonl", output_file="baton-ml/baton_pytorch_dataset.json"):
    """
    Ingests recorded telemetry dataset entries and formats them into an Alpaca/ShareGPT
    instruction-tuning dataset for PyTorch / HuggingFace fine-tuning.
    """
    if not os.path.exists(input_file):
        print(f"[DATASET-FORMATTER] No telemetry file found at {input_file}. Creating sample entry.")
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
    with open(input_file, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
                level = entry.get("level", "WARN")
                msg = entry.get("msg", "")
                
                instruction = "Analyze the following gateway security telemetry log and classify the threat level and recommended patch."
                output = f"Threat Level: {level}. Recommendation: Analyze source IP behavior, update crate dependencies if CVE, or block offending IP."
                
                formatted_entries.append({
                    "instruction": instruction,
                    "input": f"[{level}] {msg}",
                    "output": output
                })
            except Exception as e:
                continue

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(formatted_entries, f, indent=2)
    print(f"[DATASET-FORMATTER] Successfully formatted {len(formatted_entries)} entries into {output_file}")

if __name__ == "__main__":
    format_telemetry_for_training()
