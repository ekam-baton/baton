import json
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# Model Harness Setup Scaffold
# Interacts with a local LLM server (e.g., LiteRT-LM, Ollama, vLLM)
# to evaluate Qwen3 or SmolLM3-3B's tool-calling and reasoning capabilities
# against the BATON Tool Server.
# ---------------------------------------------------------------------------

# Adjust the base URL depending on your local serving stack
# Example for Ollama: http://localhost:11434/api/chat
LLM_API_URL = "http://localhost:11434/api/chat"
MODEL_NAME = "qwen2.5-coder:3b" # Using Qwen 2.5 Coder 3B as a proxy for the future Qwen3

def query_model(prompt: str, system_prompt: str = "") -> str:
    print(f"Querying model '{MODEL_NAME}' at {LLM_API_URL}...")
    
    payload = {
        "model": MODEL_NAME,
        "messages": [],
        "stream": False
    }
    
    if system_prompt:
        payload["messages"].append({
            "role": "system",
            "content": system_prompt
        })
        
    payload["messages"].append({
        "role": "user",
        "content": prompt
    })
    
    req = urllib.request.Request(
        LLM_API_URL,
        data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json'}
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            return result.get('message', {}).get('content', "No content in response")
    except urllib.error.URLError as e:
        return f"Error connecting to LLM server: {e}. Is Ollama or LiteRT-LM running?"

def main():
    system_prompt = (
        "You are BATON, an agentic security reasoning engine. "
        "Analyze the following code diff. Identify the security vulnerability it fixes, "
        "and explain how the vulnerable code could have been exploited."
    )
    
    # Read the first vulnerability from the dataset
    diff_text = "No diff found."
    try:
        with open("baton_vulnerability_dataset.jsonl", "r", encoding="utf-8") as f:
            for line in f:
                commit = json.loads(line)
                # Let's find the SSRF commit
                if "ssrf" in commit['subject'].lower():
                    diff_text = commit['diff']
                    break
    except FileNotFoundError:
        diff_text = "Dataset not found. Please run mine_git_history.py first."

    # Keep the prompt concise for the model
    user_prompt = f"Analyze this git diff and explain the vulnerability being fixed:\n\n{diff_text[:3000]}"
    
    print("--- Sending Request to Local LLM ---")
    print(f"System: {system_prompt}")
    print(f"User: Analyze this git diff and explain the vulnerability being fixed: [DIFF TRUNCATED FOR DISPLAY]\n")
    
    response = query_model(user_prompt, system_prompt)
    
    print("--- Model Response ---")
    print(response)

if __name__ == '__main__':
    main()
