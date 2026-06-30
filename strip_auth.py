import re
import sys

file_path = r'C:\Users\yrish\.gemini\antigravity\scratch\baton\feature\agents\src\main\kotlin\com\ekam\baton\feature\agents\AddEditAgentScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove auth variables
content = re.sub(r'val authOptions = listOf.*?\\n', '', content)
content = re.sub(r'var selectedAuthIndex by remember \{[^\}]+\}\\n\\s+\}\\n', '', content, flags=re.DOTALL)
content = re.sub(r'var apiKey by remember.*?\\n', '', content)
content = re.sub(r'var oauthClientId by remember.*?\\n', '', content)
content = re.sub(r'var oauthAuthUrl by remember.*?\\n', '', content)
content = re.sub(r'var oauthTokenUrl by remember.*?\\n', '', content)
content = re.sub(r'var oauthScopes by remember.*?\\n', '', content)
content = re.sub(r'var isApiKeyVisible by remember.*?\\n', '', content)

# 2. Update total steps to 3 (since Auth step is gone)
content = re.sub(r'val totalSteps = 4', 'val totalSteps = 3', content)

# 3. Strip JSON Auth loading
json_auth_load = '''                if (existingAgent.authType == "api_key") apiKey = json.optString("api_key", "")
                else if (existingAgent.authType == "oauth") {
                    oauthClientId = json.optString("client_id", "")
                    oauthAuthUrl = json.optString("auth_url", "")
                    oauthTokenUrl = json.optString("token_url", "")
                    oauthScopes = json.optString("scopes", "")
                }'''
content = content.replace(json_auth_load, '')

# 4. Simplify isEnabled logic
is_enabled_old = '''        val isEnabled = if (currentStep < totalSteps - 1) {
            when (currentStep) {
                0 -> name.isNotBlank() && description.isNotBlank()
                1 -> if (providerType == "local_mcp") endpointUrl.isNotBlank() && isUrlValid else apiKey.isNotBlank()
                2 -> if (providerType == "local_mcp") (selectedAuthIndex != 2 || (oauthClientId.isNotBlank() && oauthAuthUrl.isNotBlank() && oauthTokenUrl.isNotBlank())) else true
                else -> true
            }
        } else {
            ((providerType == "local_mcp" && endpointUrl.isNotBlank() && isUrlValid && (selectedAuthIndex != 2 || (oauthClientId.isNotBlank() && oauthAuthUrl.isNotBlank() && oauthTokenUrl.isNotBlank()))) ||
             (providerType == "other_cloud" && endpointUrl.isNotBlank() && isUrlValid && apiKey.isNotBlank()) ||
             (providerType != "local_mcp" && providerType != "other_cloud" && apiKey.isNotBlank()))
             && name.isNotBlank() && description.isNotBlank() && isSecurityValid
        }'''
is_enabled_new = '''        val isEnabled = if (currentStep < totalSteps - 1) {
            when (currentStep) {
                0 -> name.isNotBlank() && description.isNotBlank()
                1 -> endpointUrl.isNotBlank() && isUrlValid
                else -> true
            }
        } else {
            endpointUrl.isNotBlank() && isUrlValid && name.isNotBlank() && description.isNotBlank() && isSecurityValid
        }'''
content = content.replace(is_enabled_old, is_enabled_new)

# 5. Simplify canSave logic
can_save_old = '''    val canSave = name.isNotBlank() && description.isNotBlank() && isSecurityValid && when (providerType) {
        "local_mcp" -> endpointUrl.isNotBlank() && isUrlValid && (selectedAuthIndex != 2 || (oauthClientId.isNotBlank() && oauthAuthUrl.isNotBlank() && oauthTokenUrl.isNotBlank()))
        "other_cloud" -> endpointUrl.isNotBlank() && isUrlValid && apiKey.isNotBlank()
        else -> apiKey.isNotBlank()
    }'''
can_save_new = '''    val canSave = name.isNotBlank() && description.isNotBlank() && endpointUrl.isNotBlank() && isUrlValid && isSecurityValid'''
content = content.replace(can_save_old, can_save_new)

# 6. Simplify agent saving logic
agent_save_old = '''            val authType = when {
                providerType != "local_mcp" -> "api_key"
                selectedAuthIndex == 1 -> "api_key"
                selectedAuthIndex == 2 -> "oauth"
                else -> "none"
            }

            val authConfigJson = org.json.JSONObject().apply {
                if (authType == "api_key") put("api_key", apiKey)
                if (authType == "oauth") {
                    put("client_id", oauthClientId)
                    put("auth_url", oauthAuthUrl)
                    put("token_url", oauthTokenUrl)
                    put("scopes", oauthScopes)
                }
            }.toString()'''
agent_save_new = '''            val authType = "none"
            val authConfigJson = "{}"'''
content = content.replace(agent_save_old, agent_save_new)

# 7. Modify provider type list
provider_list_old = '''val providers = listOf("Local" to "local_mcp", "Anthropic" to "anthropic", "Gemini" to "gemini", "OpenAI" to "openai", "Other" to "other_cloud")'''
provider_list_new = '''val providers = listOf("Local (MCP / Router)" to "local_mcp", "A2A Protocol" to "a2a", "Webhook" to "webhook")'''
content = content.replace(provider_list_old, provider_list_new)

# 8. Remove UI blocks for Step 1 (API key input for cloud)
ui_cloud_api_key_block = '''                        } else {
                            PremiumTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = "API Key (required)",
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle visibility")
                                    }
                                }
                            )
                        }'''
content = content.replace(ui_cloud_api_key_block, '                        }')

# 9. Completely strip out step 2 (Auth step) and shift step 3 -> 2
step_2_block = re.search(r'2 -> \{\s*// STEP 3: ACCESS KEYS.*?3 -> \{', content, re.DOTALL)
if step_2_block:
    content = content.replace(step_2_block.group(0), '2 -> {')
    content = content.replace('// STEP 4: ADVANCED SECURITY', '// STEP 3: ADVANCED SECURITY')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done stripping auth.")
