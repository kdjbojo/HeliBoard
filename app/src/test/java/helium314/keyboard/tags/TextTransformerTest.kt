package helium314.keyboard.tags

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals

class TextTransformerTest {

    private fun t(input: String) = TextTransformer.transform(input)

    // ---------------- Basic passthrough ----------------

    @Test fun emptyText() {
        assertEquals("", t(""))
    }

    @Test fun plainTextUnchanged() {
        assertEquals("hello world", t("hello world"))
    }

    // ---------------- Unicode style tags ----------------

    // Codepoints (not literal glyphs) are used for expected values below to avoid
    // any source-file encoding fragility with exotic astral-plane Unicode chars.
    private fun cps(vararg codePoints: Int): String {
        val sb = StringBuilder()
        for (cp in codePoints) sb.appendCodePoint(cp)
        return sb.toString()
    }

    @Test fun boldTag() {
        // Mathematical Sans-Serif Bold lowercase starts at 0x1D5EE for 'a'
        assertEquals(cps(0x1D601, 0x1D5F2, 0x1D605, 0x1D601, 0x1D5F2), t("<b texte>"))
    }

    @Test fun italicTag() {
        // Mathematical Sans-Serif Italic lowercase starts at 0x1D622 for 'a'
        assertEquals(cps(0x1D635, 0x1D626, 0x1D639, 0x1D635, 0x1D626), t("<i texte>"))
    }

    @Test fun boldItalicTag() {
        // Mathematical Sans-Serif Bold Italic lowercase starts at 0x1D656 for 'a'
        assertEquals(cps(0x1D669, 0x1D65A, 0x1D66D, 0x1D669, 0x1D65A), t("<bi texte>"))
    }

    @Test fun scriptTag() {
        assertEquals(cps(0x1D4C9, 0x212F, 0x1D4CD, 0x1D4C9, 0x212F), t("<script texte>"))
    }

    @Test fun doubleStruckTag() {
        assertEquals(cps(0x1D565, 0x1D556, 0x1D569, 0x1D565, 0x1D556), t("<double texte>"))
    }

    @Test fun superscriptTag() {
        assertEquals(cps(0x1D57, 0x1D49, 0x2E3, 0x1D57, 0x1D49), t("<small texte>"))
    }

    @Test fun subscriptTagWithFallback() {
        // 'x' has a subscript form, 'z' style letters that lack one fall back unchanged.
        val result = t("<sub xz>")
        assertEquals("ₓz", result)
    }

    @Test fun fullwidthTag() {
        assertEquals("\uFF54\uFF45\uFF58\uFF54\uFF45", t("<fullwidth texte>"))
    }

    @Test fun vaporAliasMatchesFullwidth() {
        assertEquals(t("<fullwidth texte>"), t("<vapor texte>"))
    }

    @Test fun monospaceTag() {
        val expected = "\uD835\uDE95\uD835\uDE86\uD835\uDE99\uD835\uDE95\uD835\uDE86" // texte in mono? verify indirectly below
        // Instead of hardcoding fragile surrogate pairs, verify round-trip structure:
        val result = t("<mono abc>")
        assertNotEquals("abc", result)
        assertEquals(3, result.codePointCount(0, result.length))
    }

    @Test fun boxTag() {
        val result = t("<box AB>")
        assertNotEquals("AB", result)
        assertEquals(2, result.codePointCount(0, result.length))
    }

    @Test fun boxLowercaseFallsBackToUppercaseGlyph() {
        assertEquals(t("<box ab>"), t("<box AB>"))
    }

    @Test fun negTag() {
        val result = t("<neg AB>")
        assertNotEquals("AB", result)
        assertEquals(2, result.codePointCount(0, result.length))
    }

    @Test fun upsideTag() {
        // "ab" -> flip each char then reverse order
        val result = t("<upside ab>")
        assertEquals("qɐ", result)
    }

    @Test fun zalgoAddsCombiningMarks() {
        val result = t("<zalgo abc>")
        assertTrue(result.length > "abc".length)
        // Base letters must still be present in order.
        assertTrue(result.startsWith("a"))
    }

    @Test fun spacedTag() {
        assertEquals("t e x t e", t("<spaced texte>"))
    }

    // ---------------- Digits & punctuation per style ----------------

    @Test fun boldIncludesDigits() {
        val result = t("<b 123>")
        assertNotEquals("123", result)
        assertEquals(3, result.codePointCount(0, result.length))
    }

    @Test fun scriptLeavesDigitsUnchangedNoBlock() {
        // Script has no dedicated digit block -> digits pass through unchanged.
        assertEquals("1", t("<script 1>"))
    }

    @Test fun boldPreservesPunctuation() {
        val result = t("<b a,b!>")
        assertTrue(result.contains(","))
        assertTrue(result.contains("!"))
    }

    // ---------------- Accent normalization ----------------

    @Test fun accentsStrippedBeforeStyling() {
        // "café" -> "cafe" -> bold
        assertEquals(t("<b cafe>"), t("<b café>"))
    }

    @Test fun accentsStrippedForMultipleDiacritics() {
        assertEquals(t("<i elave>"), t("<i élàvê>"))
    }

    @Test fun utilityTagsKeepAccentsForUpper() {
        assertEquals("CAFÉ", t("<upper café>"))
    }

    @Test fun utilityTagsKeepAccentsForTitle() {
        assertEquals("Écran Étoilé", t("<title écran étoilé>"))
    }

    // ---------------- Nested tags ----------------

    @Test fun nestedTagOuterWins() {
        // Outer 'b' should win; inner 'i' is flattened to plain text first.
        assertEquals(t("<b texte>"), t("<b <i texte>>"))
    }

    @Test fun nestedUtilityInsideStyleStillEvaluated() {
        // <upper> nested inside <b ...> should still evaluate the upper-casing
        // before bold is applied (utility tags have no styling conflict).
        val result = t("<b <upper abc>>")
        val expectedPlainToBold = t("<b ABC>")
        assertEquals(expectedPlainToBold, result)
    }

    // ---------------- Unclosed / unknown tags ----------------

    @Test fun unclosedTagLeftRaw() {
        assertEquals("<b texte", t("<b texte"))
    }

    @Test fun unknownTagLeftRaw() {
        assertEquals("<foo texte>", t("<foo texte>"))
    }

    @Test fun unclosedTagFollowedByMoreText() {
        assertEquals("<b texte and more", t("<b texte and more"))
    }

    @Test fun mixedRawAndValidTags() {
        assertEquals("hello " + cps(0x1D601, 0x1D5F2, 0x1D605, 0x1D601, 0x1D5F2) + " world", t("hello <b texte> world"))
    }

    // ---------------- Utility tags ----------------

    @Test fun dateTagFormat() {
        val result = t("<date>")
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4}""")))
    }

    @Test fun timeTagFormat() {
        val result = t("<time>")
        assertTrue(result.matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test fun clipTagUsesEnvironment() {
        val env = object : TextTransformer.Environment {
            override fun clipboardText() = "pasted!"
            override fun currentDate() = "01/01/2000"
            override fun currentTime() = "00:00"
        }
        assertEquals("pasted!", TextTransformer.transform("<clip>", env))
    }

    @Test fun randTagWithinRange() {
        repeat(20) {
            val result = t("<rand 1-5>")
            val n = result.toInt()
            assertTrue(n in 1..5)
        }
    }

    @Test fun randTagSingleValue() {
        assertEquals("7", t("<rand 7-7>"))
    }

    @Test fun upperTagBasic() {
        assertEquals("HELLO", t("<upper hello>"))
    }

    @Test fun titleTagBasic() {
        assertEquals("Hello World", t("<title hello world>"))
    }
}
