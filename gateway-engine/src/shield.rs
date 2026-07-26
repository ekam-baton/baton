use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// Calculates Shannon Entropy on a byte slice: H(X) = - sum(P(x) * log2(P(x)))
/// High entropy (> 7.2) on unauthenticated raw payloads indicates binary shellcode or encrypted exploit strings.
pub fn calculate_shannon_entropy(data: &[u8]) -> f64 {
    if data.is_empty() {
        return 0.0;
    }
    let mut byte_counts = [0usize; 256];
    for &byte in data {
        byte_counts[byte as usize] += 1;
    }
    let len_f = data.len() as f64;
    let mut entropy = 0.0;
    for &count in byte_counts.iter() {
        if count > 0 {
            let p = count as f64 / len_f;
            entropy -= p * p.log2();
        }
    }
    entropy
}

#[derive(Debug, Clone)]
pub struct IpReputation {
    pub violations: usize,
    pub last_violation: Instant,
    pub is_blacklisted: bool,
}

pub struct ShieldFirewall {
    reputation_matrix: Mutex<HashMap<IpAddr, IpReputation>>,
    max_violations: usize,
    blacklist_duration: Duration,
}

impl ShieldFirewall {
    pub fn new(max_violations: usize, blacklist_secs: u64) -> Self {
        Self {
            reputation_matrix: Mutex::new(HashMap::new()),
            max_violations,
            blacklist_duration: Duration::from_secs(blacklist_secs),
        }
    }

    /// Checks if an IP is currently blacklisted by BATON-Shield.
    pub fn is_blacklisted(&self, ip: &IpAddr) -> bool {
        let mut map = self.reputation_matrix.lock().unwrap();
        if let Some(rep) = map.get_mut(ip) {
            if rep.is_blacklisted {
                if rep.last_violation.elapsed() > self.blacklist_duration {
                    // Blacklist expired - rehabilitate IP
                    rep.is_blacklisted = false;
                    rep.violations = 0;
                    eprintln!("[BATON-SHIELD] IP {} blacklist expired. Rehabilitated.", ip);
                    return false;
                }
                return true;
            }
        }
        false
    }

    /// Records a security violation against an IP address.
    pub fn record_violation(&self, ip: IpAddr, reason: &str) {
        let mut map = self.reputation_matrix.lock().unwrap();
        let rep = map.entry(ip).or_insert(IpReputation {
            violations: 0,
            last_violation: Instant::now(),
            is_blacklisted: false,
        });

        rep.violations += 1;
        rep.last_violation = Instant::now();

        eprintln!("[BATON-SHIELD] Security violation logged for IP {}: {} (Total: {})", ip, reason, rep.violations);

        if rep.violations >= self.max_violations {
            rep.is_blacklisted = true;
            eprintln!("[BATON-SHIELD] 🚨 IP {} BLACKLISTED for breach of threshold ({}/{} violations)", ip, rep.violations, self.max_violations);
        }
    }

    /// Deep Packet & Entropy Inspection
    pub fn inspect_payload(&self, ip: IpAddr, raw_bytes: &[u8]) -> Result<(), String> {
        if self.is_blacklisted(&ip) {
            return Err(format!("IP {} is blacklisted by BATON-Shield Firewall", ip));
        }

        // Entropy Inspection for payload bytes
        if raw_bytes.len() > 128 {
            let entropy = calculate_shannon_entropy(raw_bytes);
            if entropy > 7.5 {
                self.record_violation(ip, &format!("High payload entropy detected (H = {:.2})", entropy));
                return Err(format!("BATON-Shield: High entropy payload dropped (H = {:.2})", entropy));
            }
        }

        Ok(())
    }
}
