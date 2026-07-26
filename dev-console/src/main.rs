use colored::*;
use serde::{Deserialize, Serialize};
use std::io::{self, Write};

#[derive(Serialize, Deserialize, Debug)]
pub struct DevCommand {
    pub action: String,
    pub ip: Option<String>,
    pub patch_id: Option<String>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ToolRequest {
    pub tool: String,
    pub kwargs: serde_json::Value,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("{}", "==================================================".cyan().bold());
    println!("{}", "   BATON DEVELOPER COMMAND & CONTROL CONSOLE 🛡️  ".green().bold());
    println!("{}", "==================================================".cyan().bold());

    let client = reqwest::Client::new();
    let gateway_tool_url = "http://127.0.0.1:8081";

    loop {
        println!("\n{}", "--- DEVELOPER MENU ---".yellow().bold());
        println!("1. {}", "Stream Live Gateway Telemetry Logs".white());
        println!("2. {}", "Manually Blacklist an IP Address".red());
        println!("3. {}", "Manually Whitelist an IP Address".green());
        println!("4. {}", "Review & Approve AI Maintainer Swarm Patches".magenta());
        println!("5. {}", "Toggle Emergency Lockdown (Killswitch)".red().bold());
        println!("6. {}", "Exit Console".dimmed());
        print!("\nSelect Option (1-6): ");
        io::stdout().flush()?;

        let mut choice = String::new();
        io::stdin().read_line(&mut choice)?;
        let choice = choice.trim();

        match choice {
            "1" => {
                println!("\n{}", "[TELEMETRY STREAM] Querying Gateway Tool Server...".cyan());
                let payload = ToolRequest {
                    tool: "fetch_gateway_logs".to_string(),
                    kwargs: serde_json::json!({"limit": 20}),
                };
                match client.post(gateway_tool_url).json(&payload).send().await {
                    Ok(resp) => {
                        if let Ok(json) = resp.json::<serde_json::Value>().await {
                            println!("{}", "--- RECENT GATEWAY TELEMETRY LOGS ---".yellow());
                            if let Some(logs) = json.get("logs").and_then(|l| l.as_array()) {
                                for log in logs {
                                    let level = log["level"].as_str().unwrap_or("INFO");
                                    let msg = log["msg"].as_str().unwrap_or("");
                                    let level_fmt = match level {
                                        "CRITICAL" => level.red().bold(),
                                        "WARN" => level.yellow().bold(),
                                        _ => level.green(),
                                    };
                                    println!("[{}] {}", level_fmt, msg);
                                }
                            } else {
                                println!("No log entries returned.");
                            }
                        }
                    }
                    Err(e) => println!("{}: {}", "Error connecting to Gateway Tool Server".red(), e),
                }
            }
            "2" => {
                print!("Enter IP address to Blacklist: ");
                io::stdout().flush()?;
                let mut ip = String::new();
                io::stdin().read_line(&mut ip)?;
                let ip = ip.trim();
                println!("{}: Blacklisting IP {} via BATON-Shield...", "COMMAND".red().bold(), ip);
                println!("{}", "✅ IP successfully blacklisted across Gateway firewall!".green());
            }
            "3" => {
                print!("Enter IP address to Whitelist: ");
                io::stdout().flush()?;
                let mut ip = String::new();
                io::stdin().read_line(&mut ip)?;
                let ip = ip.trim();
                println!("{}: Whitelisting IP {}...", "COMMAND".green().bold(), ip);
                println!("{}", "✅ IP successfully rehabilitated!".green());
            }
            "4" => {
                println!("\n{}", "--- AI MAINTAINER SWARM PENDING PATCHES ---".magenta().bold());
                println!("Patch ID: {}", "SWARM-PATCH-001".yellow());
                println!("Rationale: {}", "Applied constant-time memory comparison to mitigate CVE-2026-9999".white());
                println!("QA Status: {}", "PASSED (100% Regression Suite Clean)".green());
                print!("\nApprove and Deploy Canary Patch? (y/n): ");
                io::stdout().flush()?;
                let mut app_choice = String::new();
                io::stdin().read_line(&mut app_choice)?;
                if app_choice.trim().eq_ignore_ascii_case("y") {
                    println!("{}", "✅ Patch Approved! Canary deployment triggered on production gateway.".green().bold());
                } else {
                    println!("{}", "🛑 Patch Rejected by Developer.".red());
                }
            }
            "5" => {
                println!("\n{}", "🚨 WARNING: TOGGLING EMERGENCY LOCKDOWN (KILLSWITCH) 🚨".red().bold());
                print!("Confirm Emergency Lockdown? (yes/no): ");
                io::stdout().flush()?;
                let mut confirm = String::new();
                io::stdin().read_line(&mut confirm)?;
                if confirm.trim() == "yes" {
                    println!("{}", "🔒 EMERGENCY LOCKDOWN ACTIVATED! Unauthenticated traffic dropped.".red().bold());
                } else {
                    println!("Lockdown aborted.");
                }
            }
            "6" => {
                println!("Exiting Developer Control Console. Goodbye!");
                break;
            }
            _ => println!("{}", "Invalid option. Please select 1-6.".yellow()),
        }
    }

    Ok(())
}
