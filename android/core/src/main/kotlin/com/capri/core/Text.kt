package com.capri.core

import java.security.MessageDigest
import java.text.Normalizer

// The desktop wrote its patterns for .NET, where \b and \w know every letter. Java's know only ASCII since JDK 19, while
// Android's ICU engine knows every letter, so each pattern goes through here: \b, \B, \w and \W become their Unicode
// forms, and Capri reads "été" or "rappelle-moi" the same in the tests and on the phone.
object Rx {
    private const val WORD = "\\p{L}\\p{M}\\p{N}_"
    private const val BOUNDARY = "(?:(?<=[$WORD])(?![$WORD])|(?<![$WORD])(?=[$WORD]))"
    private const val INSIDE = "(?:(?<=[$WORD])(?=[$WORD])|(?<![$WORD])(?![$WORD]))"

    fun unicode(pattern: String): String {
        val out = StringBuilder(pattern.length + 16)
        var i = 0
        var inClass = false
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\\' && i + 1 < pattern.length) {
                val n = pattern[i + 1]
                when {
                    n == 'b' && !inClass -> out.append(BOUNDARY)
                    n == 'B' && !inClass -> out.append(INSIDE)
                    n == 'w' -> out.append(if (inClass) WORD else "[$WORD]")
                    n == 'W' && !inClass -> out.append("[^$WORD]")
                    else -> out.append(c).append(n)
                }
                i += 2
                continue
            }
            if (c == '[' && !inClass) inClass = true
            else if (c == ']' && inClass) inClass = false
            out.append(c)
            i++
        }
        return out.toString()
    }
}

fun rx(pattern: String, vararg options: RegexOption): Regex = Regex(Rx.unicode(pattern), options.toSet())

fun rxi(pattern: String): Regex = rx(pattern, RegexOption.IGNORE_CASE)

fun String.squash(): String = this.replace(SPACES, " ").trim()

private val SPACES = Regex("\\s+")

fun quoted(value: String?): String = Json.quote(value ?: "")

// A readable short form for logs and bubbles.
fun String.cut(most: Int): String = if (length <= most) this else substring(0, maxOf(0, most - 3)).trimEnd() + "..."

fun stamp(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder()
    for (i in 0 until 8) sb.append(String.format("%02x", digest[i]))
    return sb.toString()
}

// Lower case, accents off, curly apostrophes straight, spaces squashed: how phrases are compared.
fun fold(text: String?): String {
    val lower = (text ?: "").lowercase().replace('’', '\'')
    val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
        sb.append(if (ch == 'ł') 'l' else ch)
    }
    return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC).squash()
}

object Redact {
    private val keys = rx("(?:nvapi-|sk-|AIza)[A-Za-z0-9_-]+|\\bAQ\\.[A-Za-z0-9_.-]+|\\bBearer\\s+[^\\s\"',}]+|data:image/[^;\\s]+;base64,[A-Za-z0-9+/=]+", RegexOption.IGNORE_CASE)
    private val named = rx("((?:api[_ -]?key|authorization|access[_ -]?token|xi-api-key|x-api-key)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+", RegexOption.IGNORE_CASE)

    // Keys, tokens and pictures never reach a log line or an error she shows.
    fun text(value: String?): String {
        val clean = keys.replace(value ?: "", "[redacted]")
        return named.replace(clean) { it.groupValues[1] + "[redacted]" }
    }
}

// Emoji and pictographs come off her bubble and her voice. Done by code point, since regex engines differ on surrogate pairs.
fun noEmoji(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val emoji = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp == 0xFE0F || cp == 0x200D || cp in 0x1F1E6..0x1F1FF || cp in 0x2B00..0x2BFF || cp in 0xE0020..0xE007F
        if (!emoji) sb.appendCodePoint(cp)
        i += Character.charCount(cp)
    }
    return sb.toString()
}

fun keyTail(key: String?): String = if (key == null || key.length < 4) "" else key.substring(key.length - 4)
