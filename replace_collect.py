import os
import re

target_files = [
    r"C:\Users\yrish\.gemini\antigravity\scratch\baton\feature\agents\src\main\kotlin\com\ekam\baton\feature\agents\tunnel\TunnelSetupGuideScreen.kt",
    r"C:\Users\yrish\.gemini\antigravity\scratch\baton\feature\chat\src\main\kotlin\com\ekam\baton\feature\chat\ChatScreen.kt",
    r"C:\Users\yrish\.gemini\antigravity\scratch\baton\feature\memory\src\main\kotlin\com\ekam\baton\feature\memory\MemoryScreen.kt",
    r"C:\Users\yrish\.gemini\antigravity\scratch\baton\feature\settings\src\main\kotlin\com\ekam\baton\feature\settings\SettingsScreen.kt"
]

import_statement = "import androidx.lifecycle.compose.collectAsStateWithLifecycle\n"

for filepath in target_files:
    if not os.path.exists(filepath):
        continue
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace collectAsState( with collectAsStateWithLifecycle(
    # Because collectAsState() and collectAsState(initial = ...)
    new_content = re.sub(r'\bcollectAsState\(', 'collectAsStateWithLifecycle(', content)
    
    if new_content != content:
        # Add import if missing
        if "androidx.lifecycle.compose.collectAsStateWithLifecycle" not in new_content:
            # Find last import
            last_import_idx = new_content.rfind("import ")
            if last_import_idx != -1:
                end_of_line = new_content.find('\n', last_import_idx)
                new_content = new_content[:end_of_line+1] + import_statement + new_content[end_of_line+1:]

        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {filepath}")
