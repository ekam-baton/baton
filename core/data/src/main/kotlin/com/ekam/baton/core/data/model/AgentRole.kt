package com.ekam.baton.core.data.model

enum class AgentRole(val displayName: String, val emoji: String, val defaultColorHex: String) {
    CODER("Coder", "💻", "#FFA040"),
    RESEARCHER("Researcher", "🔬", "#34D399"),
    SECURITY("Security", "🛡", "#FF453A"),
    CREATIVE("Creative", "🎨", "#FF6EC7"),
    MEMORY("Memory", "🧠", "#A78BFA"),
    ANALYST("Analyst", "📊", "#22D3EE"),
    COORDINATOR("Coordinator", "🌐", "#FFD700");

    companion object {
        /** Keyword-match agent name/description to a role. Falls back to COORDINATOR. */
        fun detectFromText(text: String): AgentRole {
            val lower = text.lowercase()
            return when {
                lower.containsAny("code", "dev", "program", "engineer", "git", "compile", "debug") -> CODER
                lower.containsAny("research", "search", "browse", "web", "read", "study", "wiki") -> RESEARCHER
                lower.containsAny("secur", "guard", "firewall", "encrypt", "auth", "protect", "scan") -> SECURITY
                lower.containsAny("creat", "design", "art", "write", "generat", "paint", "draw") -> CREATIVE
                lower.containsAny("memor", "store", "recall", "persist", "cache", "history", "log") -> MEMORY
                lower.containsAny("analy", "data", "report", "metric", "chart", "stat", "insight") -> ANALYST
                else -> COORDINATOR
            }
        }

        private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
    }
}
