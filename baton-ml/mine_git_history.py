import subprocess
import json
import re

# ---------------------------------------------------------------------------
# Data Mining Pipeline Scaffold
# Iterates through the git history of the repository to extract commits
# associated with vulnerability fixes, along with their diffs, to build
# RAG context and synthetic datasets for BATON agent training.
# ---------------------------------------------------------------------------

def run_git_command(args):
    result = subprocess.run(['git'] + args, capture_output=True, text=True, check=True, encoding='utf-8', errors='replace')
    return result.stdout.strip()

def get_commits():
    # Format: %H (hash), %an (author), %s (subject), %b (body)
    log_format = "%H%n%an%n%s%n%b%n---END_COMMIT---"
    output = run_git_command(['log', f'--format={log_format}'])
    
    commits = []
    current_commit = []
    for line in output.split('\n'):
        if line == '---END_COMMIT---':
            if len(current_commit) >= 3:
                commit_hash = current_commit[0]
                author = current_commit[1]
                subject = current_commit[2]
                body = '\n'.join(current_commit[3:])
                commits.append({
                    "hash": commit_hash,
                    "author": author,
                    "subject": subject,
                    "body": body
                })
            current_commit = []
        else:
            current_commit.append(line)
    return commits

def get_diff(commit_hash):
    # Get unified diff for the specific commit
    return run_git_command(['show', '--format=', commit_hash])

def is_vulnerability_fix(commit):
    # Regex to match security-related keywords in commit messages
    keywords = r'(security|cve|vulnerability|ssrf|injection|bypass|hmac|crypto|salt|hardcode|leak)'
    text_to_search = (commit['subject'] + " " + commit['body']).lower()
    return re.search(keywords, text_to_search) is not None

def mine_history():
    print("Mining git history for vulnerability fixes...")
    commits = get_commits()
    security_commits = []
    
    for commit in commits:
        if is_vulnerability_fix(commit):
            print(f"Found match: {commit['hash'][:7]} - {commit['subject']}")
            diff = get_diff(commit['hash'])
            security_commits.append({
                "hash": commit['hash'],
                "subject": commit['subject'],
                "diff": diff
            })
            
    # Save to a JSON lines file for model training / RAG
    output_file = "baton_vulnerability_dataset.jsonl"
    with open(output_file, 'w', encoding='utf-8') as f:
        for sc in security_commits:
            f.write(json.dumps(sc) + '\n')
            
    print(f"Successfully extracted {len(security_commits)} security commits to {output_file}.")

if __name__ == '__main__':
    try:
        mine_history()
    except subprocess.CalledProcessError as e:
        print(f"Git command failed. Are you in a git repository? Error: {e}")
