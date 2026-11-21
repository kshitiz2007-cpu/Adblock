package com.nexus.adblock

/**
 * Small, deterministic domain-rule engine used by the NEXUS prototype.
 * It evaluates host/domain rules only; it is not a browser network interceptor.
 */
enum class RuleAction { BLOCK, ALLOW }

data class DomainRule(val domain: String, val action: RuleAction)

class FilterRuleEngine(rules: Collection<DomainRule> = emptyList()) {
    private val normalizedRules = rules.mapNotNull { rule ->
        val host = normalizeHost(rule.domain)
        if (host.isBlank()) null else DomainRule(host, rule.action)
    }

    fun shouldBlock(hostOrUrl: String): Boolean {
        val host = normalizeHost(hostOrUrl)
        if (host.isBlank()) return false

        // Allow rules take precedence, including parent-domain allow rules.
        if (normalizedRules.any { it.action == RuleAction.ALLOW && matches(host, it.domain) }) return false
        return normalizedRules.any { it.action == RuleAction.BLOCK && matches(host, it.domain) }
    }

    fun rulesCount(): Int = normalizedRules.size

    companion object {
        /** Supports hosts-file rows and simple domain lists; ignores comments and localhost entries. */
        fun parseHostsOrDomainList(text: String): List<DomainRule> = text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { row ->
                val tokens = row.split(Regex("\\s+")).filter { it.isNotBlank() }
                val candidate = if (tokens.size > 1 && isIp(tokens.first())) tokens.drop(1).firstOrNull() else tokens.firstOrNull()
                candidate?.let { normalizeHost(it) }?.takeIf { it.isNotBlank() && !isIp(it) && it != "localhost" }
                    ?.let { DomainRule(it, RuleAction.BLOCK) }
            }.distinctBy { it.domain }

        fun normalizeHost(hostOrUrl: String): String {
            val trimmed = hostOrUrl.trim().lowercase()
            val host = runCatching {
                if (trimmed.contains("://")) java.net.URI(trimmed).host ?: trimmed
                else trimmed.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            }.getOrDefault(trimmed)
            return host.trim().trimEnd('.').removePrefix("www.")
        }

        private fun matches(host: String, ruleDomain: String): Boolean =
            host == ruleDomain || host.endsWith(".$ruleDomain")

        private fun isIp(value: String): Boolean =
            value.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) || value.contains(':')
    }
}
