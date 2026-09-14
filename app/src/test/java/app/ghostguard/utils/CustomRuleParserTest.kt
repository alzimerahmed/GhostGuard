package app.ghostguard.utils

import app.ghostguard.data.entities.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRuleParserTest {
    // ── Block rules ──────────────────────────────────────────────────────

    @Test
    fun `parses adblock-style block rule`() {
        val rule = CustomRuleParser.parseRule("||example.com^")
        assertNotNull(rule)
        assertEquals(RuleType.BLOCK, rule!!.ruleType)
        assertEquals("example.com", rule.domain)
        assertEquals("||example.com^", rule.rule)
    }

    @Test
    fun `parses simple domain block rule`() {
        val rule = CustomRuleParser.parseRule("example.com")
        assertNotNull(rule)
        assertEquals(RuleType.BLOCK, rule!!.ruleType)
        assertEquals("example.com", rule.domain)
    }

    @Test
    fun `parses wildcard block rule`() {
        val rule = CustomRuleParser.parseRule("*.ads.example.com")
        assertNotNull(rule)
        assertEquals(RuleType.BLOCK, rule!!.ruleType)
        assertEquals("*.ads.example.com", rule.domain)
    }

    @Test
    fun `block rule domain is lowercased`() {
        val rule = CustomRuleParser.parseRule("||EXAMPLE.COM^")
        assertEquals("example.com", rule!!.domain)
    }

    // ── Allow rules ──────────────────────────────────────────────────────

    @Test
    fun `parses adblock-style allow rule`() {
        val rule = CustomRuleParser.parseRule("@@||example.com^")
        assertNotNull(rule)
        assertEquals(RuleType.ALLOW, rule!!.ruleType)
        assertEquals("example.com", rule.domain)
    }

    @Test
    fun `parses simple allow rule`() {
        val rule = CustomRuleParser.parseRule("@@example.com")
        assertNotNull(rule)
        assertEquals(RuleType.ALLOW, rule!!.ruleType)
    }

    @Test
    fun `parses wildcard allow rule`() {
        val rule = CustomRuleParser.parseRule("@@||*.example.com^")
        assertNotNull(rule)
        assertEquals(RuleType.ALLOW, rule!!.ruleType)
        assertEquals("*.example.com", rule.domain)
    }

    // ── Comments ─────────────────────────────────────────────────────────

    @Test
    fun `parses comment line`() {
        val rule = CustomRuleParser.parseRule("! This is a comment")
        assertNotNull(rule)
        assertEquals(RuleType.COMMENT, rule!!.ruleType)
        assertEquals("", rule.domain)
        assertEquals("! This is a comment", rule.rule)
    }

    // ── Invalid input ────────────────────────────────────────────────────

    @Test
    fun `rejects empty rule`() {
        assertNull(CustomRuleParser.parseRule(""))
        assertNull(CustomRuleParser.parseRule("   "))
    }

    @Test
    fun `rejects invalid domain`() {
        assertNull(CustomRuleParser.parseRule("a*b.com"))
        assertNull(CustomRuleParser.parseRule(".example.com"))
        assertNull(CustomRuleParser.parseRule("example.com."))
        assertNull(CustomRuleParser.parseRule("not a domain"))
        assertNull(CustomRuleParser.parseRule("||^"))
        assertNull(CustomRuleParser.parseRule("@@"))
    }

    // ── Multi-line parsing ───────────────────────────────────────────────

    @Test
    fun `parses multiple rules and skips invalid lines`() {
        val text =
            """
            ||ads.example.com^
            ! comment
            @@||safe.example.com^
            invalid domain here
            tracker.net
            """.trimIndent()
        val rules = CustomRuleParser.parseRules(text)
        assertEquals(4, rules.size)
        assertEquals(RuleType.BLOCK, rules[0].ruleType)
        assertEquals(RuleType.COMMENT, rules[1].ruleType)
        assertEquals(RuleType.ALLOW, rules[2].ruleType)
        assertEquals(RuleType.BLOCK, rules[3].ruleType)
    }

    @Test
    fun `parses empty text to empty list`() {
        assertTrue(CustomRuleParser.parseRules("").isEmpty())
    }

    // ── Formatting ───────────────────────────────────────────────────────

    @Test
    fun `formats block rule in adblock format`() {
        assertEquals("||example.com^", CustomRuleParser.formatBlockRule("example.com"))
        assertEquals("||example.com^", CustomRuleParser.formatBlockRule("EXAMPLE.COM", useAdblockFormat = true))
    }

    @Test
    fun `formats block rule in plain format`() {
        assertEquals("example.com", CustomRuleParser.formatBlockRule("example.com", useAdblockFormat = false))
    }
}
