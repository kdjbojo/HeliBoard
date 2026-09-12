package helium314.keyboard.tags

import java.text.Normalizer
import kotlin.random.Random

/**
 * TextTransformer
 * ----------------
 * Pure Kotlin, Android-free module that parses "style tags" of the form
 * `<tag content>` typed inline in a text field and turns them into either
 * a Unicode-styled version of `content`, or a small utility insertion
 * (date, time, clipboard, random number, case transforms).
 *
 * The module has no Android dependency so it can be unit-tested in
 * isolation and later wired into the IME (see LatinIME integration).
 *
 * NESTING LIMITATION (documented, see spec):
 * Overlapping two different Unicode "styled alphabets" on the very same
 * characters is not always representable (there is only one code point
 * per letter per style in the Mathematical Alphanumeric Symbols block -
 * you can't be simultaneously "bold" and "script" on one glyph). When
 * tags are nested, e.g. `<b <i texte>>`, we therefore give priority to
 * the OUTERMOST tag: nested style tags are resolved down to their plain
 * (unstyled) text first, and only the outermost style is actually
 * applied to the result. Nested *utility* tags (date/time/clip/rand/
 * upper/title) are still evaluated normally since they don't have this
 * conflict (they produce plain text or case-only changes).
 */
object TextTransformer {

    private val STYLE_TAGS = setOf(
        "b", "i", "bi", "script", "double", "small", "sub",
        "fullwidth", "vapor", "mono", "box", "neg", "upside", "zalgo", "spaced"
    )

    private val UTILITY_TAGS = setOf("date", "time", "clip", "rand", "upper", "title")

    /** Injected dependencies so the module stays Android-free and testable. */
    interface Environment {
        fun clipboardText(): String
        fun currentDate(): String // dd/MM/yyyy
        fun currentTime(): String // HH:mm
    }

    /** Default environment used when no Environment is supplied (mainly for tests). */
    class DefaultEnvironment(
        private val clipboard: () -> String = { "" }
    ) : Environment {
        override fun clipboardText(): String = clipboard()
        override fun currentDate(): String {
            val c = java.util.Calendar.getInstance()
            return "%02d/%02d/%04d".format(
                c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.YEAR)
            )
        }
        override fun currentTime(): String {
            val c = java.util.Calendar.getInstance()
            return "%02d:%02d".format(
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE)
            )
        }
    }

    // -----------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------

    /**
     * Transforms every top-level `<tag content>` occurrence in [text].
     * Unknown tags, or tags that are never closed by `>`, are left
     * completely untouched (raw text passthrough).
     */
    fun transform(text: String, env: Environment = DefaultEnvironment()): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val end = findMatchingClose(text, i)
                if (end != -1) {
                    val content = text.substring(i + 1, end)
                    val handled = transformTag(content, env)
                    if (handled != null) {
                        sb.append(handled)
                        i = end + 1
                        continue
                    }
                }
                // Unclosed or unknown tag: leave the '<' as literal text and
                // keep scanning normally from the next character.
                sb.append(c)
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Convenience for the IME: call this every time the user types `>`.
     * [textBeforeCursor] is everything currently in the input field up to
     * (and NOT including) the caret. If the text right before the caret
     * ends with a fully-formed, recognized `<tag content>` sequence, this
     * returns a [Replacement] describing how many characters to delete
     * (via deleteSurroundingText) and what to commit instead. Returns
     * null if nothing should change (unknown tag / no tag present).
     */
    data class Replacement(val charsToDelete: Int, val textToInsert: String)

    fun onClosingBracketTyped(textBeforeCursor: String, env: Environment = DefaultEnvironment()): Replacement? {
        // The '>' has already been typed and appended by the caller before
        // invoking this, OR textBeforeCursor already ends with '>' — handle both
        // by making sure it ends with '>'.
        val text = if (textBeforeCursor.endsWith(">")) textBeforeCursor else "$textBeforeCursor>"
        val openIdx = findMatchingOpenFromEnd(text) ?: return null
        val content = text.substring(openIdx + 1, text.length - 1)
        val result = transformTag(content, env) ?: return null
        val rawLength = text.length - openIdx // "<tag content>" length
        return Replacement(charsToDelete = rawLength, textToInsert = result)
    }

    /** Scans backwards from the end of [text] (which ends with '>') to find the matching '<'. */
    private fun findMatchingOpenFromEnd(text: String): Int? {
        if (!text.endsWith(">")) return null
        var depth = 0
        for (j in text.length - 1 downTo 0) {
            when (text[j]) {
                '>' -> depth++
                '<' -> {
                    depth--
                    if (depth == 0) return j
                }
            }
        }
        return null
    }

    // -----------------------------------------------------------------
    // Tag dispatch
    // -----------------------------------------------------------------

    /** [content] is the tag body without the surrounding `<` `>`. Returns null if unknown/invalid. */
    private fun transformTag(content: String, env: Environment): String? {
        val spaceIdx = content.indexOf(' ')
        @Suppress("DEPRECATION")
        val tagName = (if (spaceIdx == -1) content else content.substring(0, spaceIdx)).toLowerCase()
        val arg = if (spaceIdx == -1) "" else content.substring(spaceIdx + 1)

        return when {
            tagName in UTILITY_TAGS -> applyUtilityTag(tagName, arg, env)
            tagName in STYLE_TAGS -> applyStyleTag(tagName, arg)
            else -> null
        }
    }

    private fun applyUtilityTag(tagName: String, arg: String, env: Environment): String? {
        return when (tagName) {
            "date" -> env.currentDate()
            "time" -> env.currentTime()
            "clip" -> env.clipboardText()
            "rand" -> {
                val m = Regex("""^(-?\d+)\s*-\s*(-?\d+)$""").find(arg.trim()) ?: return null
                val a = m.groupValues[1].toLongOrNull() ?: return null
                val b = m.groupValues[2].toLongOrNull() ?: return null
                val lo = minOf(a, b)
                val hi = maxOf(a, b)
                (Random.nextLong(lo, hi + 1)).toString()
            }
            "upper" -> {
                @Suppress("DEPRECATION")
                resolveArgKeepingAccents(arg).toUpperCase()
            }
            "title" -> resolveArgKeepingAccents(arg)
                .split(Regex("(?<=\\s)|(?=\\s)")) // keep whitespace tokens, split on word boundaries
                .joinToString("") { token ->
                    if (token.isBlank() || token.isEmpty()) token
                    else {
                        @Suppress("DEPRECATION")
                        token.substring(0, 1).toUpperCase() + token.substring(1)
                    }
                }
            else -> null
        }
    }

    private fun applyStyleTag(tagName: String, arg: String): String {
        // Resolve nested tags first. Nested *style* tags get flattened to
        // plain text (outer tag wins, see class doc); nested utility tags
        // are evaluated normally since there's no conflict.
        val plain = resolveArgFlatteningStyles(arg)
        // Accents are normalized away before any Unicode-style transform.
        val normalized = stripDiacritics(plain)
        return when (tagName) {
            "b" -> mapChars(normalized, boldSansMap)
            "i" -> mapChars(normalized, italicSansMap)
            "bi" -> mapChars(normalized, boldItalicSansMap)
            "script" -> mapChars(normalized, scriptMap)
            "double" -> mapChars(normalized, doubleStruckMap)
            "small" -> mapChars(normalized, superscriptMap)
            "sub" -> mapChars(normalized, subscriptMap)
            "fullwidth", "vapor" -> mapChars(normalized, fullwidthMap)
            "mono" -> mapChars(normalized, monospaceMap)
            "box" -> mapChars(normalized, boxMap)
            "neg" -> mapChars(normalized, negBoxMap)
            "upside" -> upsideDown(normalized)
            "zalgo" -> zalgo(normalized)
            "spaced" -> normalized.toCharArray().joinToString(" ")
            else -> normalized
        }
    }

    // -----------------------------------------------------------------
    // Nested-tag resolution helpers
    // -----------------------------------------------------------------

    /** Resolves nested tags in [arg]; style tags are flattened to plain (unstyled) text. */
    private fun resolveArgFlatteningStyles(arg: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < arg.length) {
            val c = arg[i]
            if (c == '<') {
                val end = findMatchingClose(arg, i)
                if (end != -1) {
                    val content = arg.substring(i + 1, end)
                    val spaceIdx = content.indexOf(' ')
                    @Suppress("DEPRECATION")
                    val tagName = (if (spaceIdx == -1) content else content.substring(0, spaceIdx)).toLowerCase()
                    val inner = if (spaceIdx == -1) "" else content.substring(spaceIdx + 1)
                    var consumed = false
                    if (tagName in UTILITY_TAGS) {
                        val result = applyUtilityTag(tagName, inner, DefaultEnvironment())
                        if (result != null) {
                            sb.append(result)
                            i = end + 1
                            consumed = true
                        }
                    } else if (tagName in STYLE_TAGS) {
                        // Flatten: recurse into the inner content, dropping this style.
                        sb.append(resolveArgFlatteningStyles(inner))
                        i = end + 1
                        consumed = true
                    }
                    if (consumed) continue
                }
                sb.append(c)
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Like [resolveArgFlatteningStyles] but used for utility tags that must keep accents intact. */
    private fun resolveArgKeepingAccents(arg: String): String = resolveArgFlatteningStyles(arg)

    /** Finds the index of the `>` matching the `<` at [openIdx], allowing nested `<...>` pairs. */
    private fun findMatchingClose(text: String, openIdx: Int): Int {
        var depth = 0
        for (j in openIdx until text.length) {
            when (text[j]) {
                '<' -> depth++
                '>' -> {
                    depth--
                    if (depth == 0) return j
                }
            }
        }
        return -1
    }

    // -----------------------------------------------------------------
    // Normalization
    // -----------------------------------------------------------------

    /** Strips diacritics: é->e, è->e, à->a, ç->c, etc. via NFD + combining-mark removal. */
    fun stripDiacritics(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "")
    }

    // -----------------------------------------------------------------
    // Char-map based styles
    // -----------------------------------------------------------------

    private fun mapChars(text: String, map: Map<Char, String>): String {
        val sb = StringBuilder()
        for (c in text) {
            sb.append(map[c] ?: c.toString())
        }
        return sb.toString()
    }

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"

    private fun buildMap(source: String, target: String): Map<Char, String> {
        require(source.length == target.codePointCount(0, target.length)) {
            "source/target length mismatch for $source"
        }
        val map = HashMap<Char, String>()
        var ti = 0
        for (sc in source) {
            val cp = target.codePointAt(ti)
            map[sc] = String(Character.toChars(cp))
            ti += Character.charCount(cp)
        }
        return map
    }

    private fun rangeString(startCodePoint: Int, count: Int): String {
        val sb = StringBuilder()
        for (i in 0 until count) sb.appendCodePoint(startCodePoint + i)
        return sb.toString()
    }

    private fun combine(vararg maps: Map<Char, String>): Map<Char, String> {
        val out = HashMap<Char, String>()
        for (m in maps) out.putAll(m)
        return out
    }

    // ---- Bold Sans (Mathematical Sans-Serif Bold): 1D5D4-1D5ED / 1D5EE-1D607 / digits 1D7EC-1D7F5 ----
    private val boldSansMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1D5D4, 26)),
        buildMap(LOWER, rangeString(0x1D5EE, 26)),
        buildMap(DIGITS, rangeString(0x1D7EC, 10))
    )

    // ---- Italic Sans (Mathematical Sans-Serif Italic): 1D608-1D621 / 1D622-1D63B, no digit variant ----
    private val italicSansMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1D608, 26)),
        buildMap(LOWER, rangeString(0x1D622, 26))
    )

    // ---- Bold Italic Sans: 1D63C-1D655 / 1D656-1D66F, no digit variant ----
    private val boldItalicSansMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1D63C, 26)),
        buildMap(LOWER, rangeString(0x1D656, 26))
    )

    // ---- Script (cursive), with the classic "letterlike symbol" exceptions ----
    private val scriptUpperExplicit =
        "𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵"
    private val scriptLowerExplicit =
        "𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏"
    private val scriptMap: Map<Char, String> = combine(
        buildMap(UPPER, scriptUpperExplicit),
        buildMap(LOWER, scriptLowerExplicit)
        // digits: no dedicated script-digit block -> left unchanged (handled by mapChars fallback)
    )

    // ---- Double-struck / blackboard bold, with classic exceptions C H N P Q R Z ----
    private val doubleUpperExplicit =
        "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
    private val doubleLowerExplicit =
        "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"
    private val doubleStruckMap: Map<Char, String> = combine(
        buildMap(UPPER, doubleUpperExplicit),
        buildMap(LOWER, doubleLowerExplicit),
        buildMap(DIGITS, rangeString(0x1D7D8, 10))
    )

    // ---- Monospace: 1D670-1D689 / 1D68A-1D6A3 / digits 1D7F6-1D7FF ----
    private val monospaceMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1D670, 26)),
        buildMap(LOWER, rangeString(0x1D68A, 26)),
        buildMap(DIGITS, rangeString(0x1D7F6, 10))
    )

    // ---- Fullwidth: FF21-FF3A / FF41-FF5A / FF10-FF19 (alias "vapor") ----
    private val fullwidthMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0xFF21, 26)),
        buildMap(LOWER, rangeString(0xFF41, 26)),
        buildMap(DIGITS, rangeString(0xFF10, 10)),
        mapOf(' ' to "\u3000")
    )

    // ---- Superscript ("small" / exposant). Uppercase & 'q' are very limited; missing -> fallback unchanged ----
    private val superscriptLowerExplicit = mapOf(
        'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ", 'g' to "ᵍ",
        'h' to "ʰ", 'i' to "ⁱ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ", 'm' to "ᵐ", 'n' to "ⁿ",
        'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ", 't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ",
        'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ", 'z' to "ᶻ"
        // 'q' has no standard superscript form -> left unmapped (fallback to itself)
    )
    private val superscriptUpperExplicit = mapOf(
        'A' to "ᴬ", 'B' to "ᴮ", 'D' to "ᴰ", 'E' to "ᴱ", 'G' to "ᴳ", 'H' to "ᴴ", 'I' to "ᴵ",
        'J' to "ᴶ", 'K' to "ᴷ", 'L' to "ᴸ", 'M' to "ᴹ", 'N' to "ᴺ", 'O' to "ᴼ", 'P' to "ᴾ",
        'R' to "ᴿ", 'T' to "ᵀ", 'U' to "ᵁ", 'V' to "ⱽ", 'W' to "ᵂ"
        // C,F,Q,S,X,Y,Z: no standard superscript form -> left unmapped
    )
    private val superscriptDigits = mapOf(
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
        '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹"
    )
    private val superscriptMap: Map<Char, String> = combine(superscriptLowerExplicit, superscriptUpperExplicit, superscriptDigits)

    // ---- Subscript (indice): even more limited, no uppercase at all ----
    private val subscriptLowerExplicit = mapOf(
        'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ",
        'l' to "ₗ", 'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ",
        's' to "ₛ", 't' to "ₜ", 'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ"
        // remaining lowercase + all uppercase: no standard subscript form -> unmapped (fallback)
    )
    private val subscriptDigits = mapOf(
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
        '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉"
    )
    private val subscriptMap: Map<Char, String> = combine(subscriptLowerExplicit, subscriptDigits)

    // ---- Squared Latin (box): only A-Z defined; lowercase falls back to uppercase glyph; no digits ----
    private val boxMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1F130, 26)),
        buildMap(LOWER, rangeString(0x1F130, 26)) // lowercase reuses the squared uppercase glyph
    )

    // ---- Negative Squared Latin: only A-Z defined; lowercase falls back to uppercase glyph; no digits ----
    private val negBoxMap: Map<Char, String> = combine(
        buildMap(UPPER, rangeString(0x1F170, 26)),
        buildMap(LOWER, rangeString(0x1F170, 26))
    )

    // -----------------------------------------------------------------
    // Upside-down (manual per-character table, letters + basic digits)
    // -----------------------------------------------------------------
    private val upsideDownMap: Map<Char, Char> = mapOf(
        'a' to 'ɐ', 'b' to 'q', 'c' to 'ɔ', 'd' to 'p', 'e' to 'ǝ', 'f' to 'ɟ', 'g' to 'ƃ',
        'h' to 'ɥ', 'i' to 'ᴉ', 'j' to 'ɾ', 'k' to 'ʞ', 'l' to 'l', 'm' to 'ɯ', 'n' to 'u',
        'o' to 'o', 'p' to 'd', 'q' to 'b', 'r' to 'ɹ', 's' to 's', 't' to 'ʇ', 'u' to 'n',
        'v' to 'ʌ', 'w' to 'ʍ', 'x' to 'x', 'y' to 'ʎ', 'z' to 'z',
        'A' to '∀', 'B' to 'ᗺ', 'C' to 'Ɔ', 'D' to 'ᗡ', 'E' to 'Ǝ', 'F' to 'Ⅎ', 'G' to 'פ',
        'H' to 'H', 'I' to 'I', 'J' to 'ſ', 'K' to 'ʞ', 'L' to '˥', 'M' to 'W', 'N' to 'N',
        'O' to 'O', 'P' to 'Ԁ', 'Q' to 'Q', 'R' to 'ᴚ', 'S' to 'S', 'T' to '⊥', 'U' to '∩',
        'V' to 'Λ', 'W' to 'M', 'X' to 'X', 'Y' to '⅄', 'Z' to 'Z',
        '0' to '0', '1' to 'Ɩ', '2' to 'ᄅ', '3' to 'Ɛ', '4' to 'ㄣ', '5' to 'ϛ', '6' to '9',
        '7' to 'ㄥ', '8' to '8', '9' to '6',
        '.' to '˙', ',' to '\'', '\'' to ',', '?' to '¿', '!' to '¡',
        '(' to ')', ')' to '(', '[' to ']', ']' to '[', '{' to '}', '}' to '{'
    )

    private fun upsideDown(text: String): String {
        // Characters are reversed AND individually flipped, as a real "upside down" render would be.
        val flipped = text.map { upsideDownMap[it] ?: it }
        return flipped.reversed().joinToString("")
    }

    // -----------------------------------------------------------------
    // Zalgo (moderate intensity - readable, not "cursed")
    // -----------------------------------------------------------------
    private val zalgoCombiningAbove = listOf(
        '\u0301', '\u0300', '\u0302', '\u0308', '\u0304', '\u030c', '\u0306', '\u030a'
    )
    private val zalgoCombiningBelow = listOf(
        '\u0316', '\u0317', '\u0323', '\u0324', '\u0325', '\u0330', '\u0331'
    )

    private fun zalgo(text: String, random: Random = Random.Default): String {
        val sb = StringBuilder()
        for (c in text) {
            sb.append(c)
            if (!c.isWhitespace()) {
                // Moderate: 1-2 marks above, 0-1 below - stays legible.
                val aboveCount = 1 + random.nextInt(2)
                repeat(aboveCount) { sb.append(zalgoCombiningAbove[random.nextInt(zalgoCombiningAbove.size)]) }
                if (random.nextBoolean()) {
                    sb.append(zalgoCombiningBelow[random.nextInt(zalgoCombiningBelow.size)])
                }
            }
        }
        return sb.toString()
    }
}
