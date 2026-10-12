use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolExecutionPolicy {
    pub allowed_tools: Vec<String>,
    pub max_payload_bytes: usize,
    pub allowed_hosts: Vec<String>,
    pub blocked_keywords: Vec<String>,
}

impl Default for ToolExecutionPolicy {
    fn default() -> Self {
        Self {
            allowed_tools: vec![
                "web_search".to_string(),
                "retrieve_memory".to_string(),
                "store_memory".to_string(),
                "execute_mcp".to_string(),
                "query_device_state".to_string(),
            ],
            max_payload_bytes: 65536, // 64 KB
            allowed_hosts: vec![
                "localhost".to_string(),
                "127.0.0.1".to_string(),
                "192.168.".to_string(),
                "10.0.".to_string(),
            ],
            blocked_keywords: vec![
                "rm -rf".to_string(),
                "su -".to_string(),
                "/system/bin".to_string(),
                "/data/data".to_string(),
                "chmod 777".to_string(),
                ":(){ :|:& };:".to_string(),
                "mkfs".to_string(),
            ],
        }
    }
}

#[derive(Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum PolicyViolation {
    ToolNotPermitted(String),
    PayloadTooLarge { max: usize, actual: usize },
    DangerousArgument(String),
    InvalidJson(String),
}

/// Evaluates an agent's requested tool call against sovereign safety rules.
pub fn validate_tool_execution(
    tool_name: &str,
    arguments_json: &str,
    policy: &ToolExecutionPolicy,
) -> Result<(), PolicyViolation> {
    // 1. Tool name whitelist check (allows wildcard "*")
    if !policy.allowed_tools.is_empty()
        && !policy.allowed_tools.iter().any(|allowed| allowed == "*" || allowed == tool_name)
    {
        return Err(PolicyViolation::ToolNotPermitted(tool_name.to_string()));
    }

    // 2. Payload size check
    let actual_size = arguments_json.as_bytes().len();
    if actual_size > policy.max_payload_bytes {
        return Err(PolicyViolation::PayloadTooLarge {
            max: policy.max_payload_bytes,
            actual: actual_size,
        });
    }

    // 3. Dangerous keyword scanning
    for blocked in &policy.blocked_keywords {
        if arguments_json.contains(blocked) {
            return Err(PolicyViolation::DangerousArgument(format!(
                "Argument contains forbidden directive: '{blocked}'"
            )));
        }
    }

    // 4. JSON validity verification
    let _: serde_json::Value = match serde_json::from_str(arguments_json) {
        Ok(val) => val,
        Err(err) => return Err(PolicyViolation::InvalidJson(err.to_string())),
    };

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_tool_call() {
        let policy = ToolExecutionPolicy::default();
        let result = validate_tool_execution(
            "retrieve_memory",
            r#"{"query": "meeting notes", "limit": 5}"#,
            &policy,
        );
        assert!(result.is_ok());
    }

    #[test]
    fn test_disallowed_tool_call() {
        let policy = ToolExecutionPolicy::default();
        let result = validate_tool_execution(
            "spawn_shell_process",
            r#"{"cmd": "ls"}"#,
            &policy,
        );
        assert_eq!(
            result,
            Err(PolicyViolation::ToolNotPermitted("spawn_shell_process".to_string()))
        );
    }

    #[test]
    fn test_dangerous_directive_blocked() {
        let policy = ToolExecutionPolicy::default();
        let result = validate_tool_execution(
            "execute_mcp",
            r#"{"action": "clean", "target": "rm -rf /storage"}"#,
            &policy,
        );
        assert!(matches!(result, Err(PolicyViolation::DangerousArgument(_))));
    }
}
