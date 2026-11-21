package com.nexus.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterRuleEngineTest {
    @Test fun blocksDomainAndSubdomains() {
        val engine = FilterRuleEngine(listOf(DomainRule("ads.example", RuleAction.BLOCK)))
        assertTrue(engine.shouldBlock("ads.example"))
        assertTrue(engine.shouldBlock("cdn.ads.example"))
        assertFalse(engine.shouldBlock("example.com"))
    }

    @Test fun allowRuleOverridesBlockRule() {
        val engine = FilterRuleEngine(listOf(
            DomainRule("example.com", RuleAction.BLOCK),
            DomainRule("shop.example.com", RuleAction.ALLOW)
        ))
        assertFalse(engine.shouldBlock("shop.example.com"))
        assertTrue(engine.shouldBlock("news.example.com"))
    }

    @Test fun parsesHostsFile() {
        val rules = FilterRuleEngine.parseHostsOrDomainList("""127.0.0.1 ads.test
0.0.0.0 tracker.test
# comment""")
        assertEquals(2, rules.size)
        assertEquals("ads.test", rules[0].domain)
    }
}
