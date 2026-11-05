package com.capri.core

// What she says after her brain writes it: thoughts, junk tokens, emoji and actions come off, long replies are cut at a
// sentence, "choom" is kept rare, stock closing questions go, and an opener she just used isn't used again.
// Ported from app/Brain.cs (Clean, Visible, Sparing, Unpraised, Unclosed, Fresh, Garbled) and app/Voice.cs (Sayable, Uncued).
object Cleanup {
    private val junk = rxi("<(unused\\d+|pad|mask|unk|eos|bos|start_of_(turn|image|audio)|end_of_(turn|image|audio))>")
    private val think = Regex("<think>.*?(</think>|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val channel = Regex("<\\|channel>thought\\b.*?(<channel\\|>|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val hurt = rxi("\\[hurt\\]\\s*")
    private val actions = Regex("\\*[^*]{0,80}\\*")
    private val label = rxi("^(capri|capricorn)\\s*(:|>)\\s*")

    val Caps = intArrayOf(220, 420, 820)

    fun thoughtless(text: String?): String {
        var t = junk.replace(text ?: "", " ")
        t = think.replace(t, " ")
        return channel.replace(t, " ")
    }

    fun unhurt(text: String): String = hurt.replace(text, "")

    fun visible(text: String?): String = Voice.uncued(unhurt(thoughtless(text)))

    fun hurtTagged(text: String?): Boolean = hurt.containsMatchIn(thoughtless(text))

    // wordiness picks her length cap, unless they chose a reply size, which lifts the cap.
    fun clean(text: String?, wordiness: Int = 0, uncapped: Boolean = false): String {
        if (text == null) return ""
        var t = visible(text)
        t = actions.replace(t, " ")
        t = noEmoji(t)
        t = t.squash()
        t = label.replace(t, "")
        t = unquoted(t)
        if (uncapped) return t
        val most = Caps[wordiness.coerceIn(0, 2)]
        if (t.length <= most) return t
        val cut = t.lastIndexOfAny(charArrayOf('.', '!', '?'), most - 1)
        if (cut >= 60) return t.substring(0, cut + 1)
        val space = t.lastIndexOf(' ', most - 2)
        return t.substring(0, if (space > 60) space else most - 2) + "..."
    }

    // Study and dev answers keep their lines, lists and code: only thoughts, junk tokens, cues and emoji come off.
    fun rich(text: String?): String {
        val t = noEmoji(visible(text)).replace("\r\n", "\n")
        return label.replace(t.trim(), "").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // Quotes around the whole reply come off; a pair that belongs to a song or a name stays.
    fun unquoted(text: String): String {
        val marks = Regex("[\"“”]").findAll(text).count()
        if (marks > 2 || (marks == 2 && !Regex("^[\"“”].*[\"“”]$").matches(text))) return text
        return text.trim('"', '“', '”', ' ')
    }

    fun garbled(text: String?): Boolean {
        val count = junk.findAll(text ?: "").count()
        return count >= 3 || (count > 0 && clean(text).isEmpty()) || looping(visible(text))
    }

    private fun looping(text: String): Boolean {
        val words = Regex("\\p{L}+").findAll(text.lowercase()).map { it.value }.toList()
        val seen = HashSet<String>()
        var run = 0
        var longest = 0
        for (i in words.indices) {
            seen.add(words[i])
            run = if (i > 0 && words[i] == words[i - 1]) run + 1 else 1
            longest = maxOf(longest, run)
        }
        return longest >= 8 || (words.size >= 24 && seen.size * 10 < words.size * 3)
    }

    private val praise = rxi("(^|(?<=[.!?]\\s))[^.!?]*\\bgood (boy|girl)\\b[^.!?]*[.!?]?\\s*")

    // "Good boy" only lands now and then.
    fun unpraised(said: String, lately: Boolean): String {
        if (!lately) return said
        val kept = praise.replace(said, "").trim()
        return if (kept.isEmpty()) said else kept
    }

    // "choom" on its own as a sentence goes, and when she said it lately, it goes everywhere. Their nickname takes its place.
    fun sparing(said: String, lately: Boolean, name: String? = null): String {
        var kept = rxi("(^|(?<=[.!?]\\s))choom\\s*[.!?]+\\s*").replace(said, "")
        if (lately) {
            kept = rxi("(^|[.!?]\\s+)choom\\s*,\\s*(\\p{L})").replace(kept) { it.groupValues[1] + it.groupValues[2].uppercase() }
            kept = rxi(",\\s*choom\\b").replace(kept, "")
            kept = rxi("\\s*\\bchoom\\b").replace(kept, "")
            kept = Regex("\\s+([.!?,])").replace(kept, "$1").trim()
        }
        if (kept.isEmpty()) kept = said
        if (!name.isNullOrEmpty()) kept = rxi("\\bchoom\\b").replace(kept) { name }
        return kept
    }

    private val closer = rxi("(?<=[.!?])\\s+[^.!?]*\\b(what'?s next|what'?s the plan|what now|what (are|r) (we|you) (doing|up to|getting into|cooking|diving into)|what (kind|sort) of|what (kinda|sorta)|what \\w+ \\w+ (are|should|shall) we|wanna (hop|dive|jump|get|go|cause|start|stir|see|make|hit|break)|shall we|ready for|ready to|got any (plans|ideas)|on fait quoi|quel est le plan|et maintenant|on commence|prêt à|prête à|tu veux qu'on)\\b[^.!?]*\\?\\s*$")

    fun unclosed(said: String?): String = closer.replace(said ?: "", "").trimEnd()

    private val echoed = rxi("^(?:(?:a|an|the|your|some|more|un|une|le|la|les|ton|ta|tes|du|des)\\s+)?(?<words>[\\p{L}\\p{N}'-]+(?:\\s+[\\p{L}\\p{N}'-]+){0,2})(?:,\\s*(?:huh|eh|yeah|really|seriously|right|alright|nice|cool|preem|hein|vraiment|sérieux))?\\?\\s+")
    private val doubted = rxi("^(?:seriously|really|huh|wow|sérieux|vraiment)\\?\\s+")
    private val opening = rxi("^(?<word>whoa|woah|seriously|man|dude|ugh|yo|ooh+|oh|hey|okay|ok|alright|well|nah|wow|haha+|heh|huh|aww|damn|dang|yezzirrr|nice|smart|bummer|classic|right|bon|ouah|waouh|eh bien|ah|oh là là|sérieux|bref)[,.!?]+\\s+")

    // Her opening word, or their own word said back as a question, wears thin fast, so a line with enough left loses it.
    fun fresh(said: String?, asked: String?, before: List<String>): String {
        if (said.isNullOrEmpty()) return ""
        var line: String = said
        val e = echoed.find(line)
        if (e != null && theirs(e.groups["words"]?.value ?: "", asked)) {
            line = line.substring(e.value.length)
            val d = doubted.find(line)
            if (d != null) line = line.substring(d.value.length)
        }
        val o = opening.find(line)
        if (o != null) {
            val word = o.groups["word"]?.value ?: ""
            if (before.any { (opening.find(it)?.groups?.get("word")?.value ?: "").equals(word, true) }) line = line.substring(o.value.length)
        }
        if (line.length == said.length || Regex("\\S+").findAll(line).count() < 3) return said
        return line.substring(0, 1).uppercase() + line.substring(1)
    }

    private fun theirs(words: String, asked: String?): Boolean {
        val spoken = (asked ?: "").lowercase().split(Regex("[^\\p{L}\\p{N}'-]+")).toHashSet()
        return words.lowercase().split(Regex("\\s+")).all { it in spoken }
    }

    // A reply that only says "act" means her brain wants her hands (any language).
    fun handed(reply: String?): Boolean =
        rxi("^\\W*act\\W*($|\\n)").containsMatchIn(reply ?: "") || Regex("^\\W*act\\s+\\p{Lu}").containsMatchIn(reply ?: "")

    // Follow-up: a second thought she may add a moment later, split off with a marker line.
    fun split(text: String): Pair<String, String> {
        val marker = rxi("\\n\\s*(?:\\(?follow[- ]?up\\)?|\\[then\\]|---)\\s*:?\\s*\\n?")
        val m = marker.find(text) ?: return text to ""
        return text.substring(0, m.range.first).trim() to text.substring(m.range.last + 1).trim()
    }
}

// Words for her voice: links read as their site, emoji gone, her slang spelled so a voice says it right.
object Voice {
    private const val FEELINGS = "happy|sad|angry|excited|calm|nervous|confident|surprised|satisfied|delighted|scared|worried|upset|frustrated|depressed|empathetic|embarrassed|disgusted|moved|proud|relaxed|grateful|curious|sarcastic|disdainful|unhappy|anxious|hysterical|indifferent|uncertain|doubtful|confused|disappointed|regretful|guilty|ashamed|jealous|envious|hopeful|optimistic|pessimistic|nostalgic|lonely|bored|contemptuous|sympathetic|compassionate|determined|resigned|in a hurry tone|shouting|screaming|whispering|soft tone|laughing|chuckling|sobbing|crying loudly|sighing|groaning|panting"
    private const val SOUNDS = "emphasis|gasping|yawning|snoring|clear throat|clears throat|long-break|break|laughs?|giggl(?:e|es|ing)|chuckles?|sighs?|gasps?|whispers?|sobs?|cr(?:ying|ies)|sniffl(?:e|es|ing)|groans?|yawns?|scoff(?:s|ing)?|snorts?|hum(?:s|ming)?|pauses?|teasing(?:ly)?|playful(?:ly)?|smug(?:ly)?|flirt(?:y|atious|atiously)|sweetly|softly|warm(?:ly)?|cheerful(?:ly)?|mischievous(?:ly)?|annoyed|pout(?:s|ing)?|gentl(?:e|y)|shy(?:ly)?|sleepy|tired|dramatic(?:ally)?|mock(?:ing|ingly)?|sing(?:s|ing)|cheeky|deadpan|quietly|loudly|evil"
    val Cue = Regex("\\[\\s*(?<cue>(?:(?:very|slightly|extremely|really|super|so|quite|a bit|a little|kind of|kinda)\\s+)?(?:$FEELINGS|$SOUNDS)(?:[\\s-]+[a-z']+){0,3})\\s*\\](?!\\()|\\(\\s*(?<cue2>(?!moved\\b)(?:$FEELINGS))\\s*\\)", RegexOption.IGNORE_CASE)

    const val Cueing = "\nYour voice acts out cues in square brackets, so when a feeling fits, start the sentence with one of these: [laughing], [chuckling], [giggling], [sighing], [whispering], [soft tone], [excited], [happy], [sad], [nervous], [surprised], [sarcastic], [teasing], [playful] or [gasping]. At most one per sentence."

    fun uncued(text: String?): String {
        if (text.isNullOrEmpty() || !Cue.containsMatchIn(text)) return text ?: ""
        val plain = Cue.replace(text, "")
        return Regex("[ \\t]+(?=[.,!?…;:]|$)", RegexOption.MULTILINE).replace(plain, "").replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private val respelled = arrayOf(arrayOf("chooms", "chewms"), arrayOf("choom", "chewm"), arrayOf("preem", "preme"), arrayOf("yezzirrr", "yessirrr"))

    fun sayable(text: String?): String {
        var t = rxi("https?://(?:www\\.)?([^/\\s]+?)(?:/\\S*?)?(?=[.,!?)]*(?:\\s|$))").replace(text ?: "") { it.groupValues[1].replace(".", " dot ") }
        t = noEmoji(t)
        t = Regex("\\s*&\\s*").replace(t, " and ")
        t = Regex("(\\d)\\s*%").replace(t, "$1 %")
        t = Regex("\\s*(->|=>|~+)\\s*").replace(t, " ")
        t = t.squash()
        for (pair in respelled) {
            t = rxi("\\b" + pair[0] + "\\b").replace(t) { m ->
                val w = m.value
                if (w.uppercase() == w) pair[1].uppercase() else if (w[0].isUpperCase()) pair[1].replaceFirstChar { it.uppercase() } else pair[1]
            }
        }
        return t
    }

    // What a voice says: brackets and asterisks off, unless a cue-reading voice keeps its cues.
    fun spoken(said: String?, cues: Boolean = false): String {
        var s = said ?: ""
        if (cues) s = Cue.replace(s) { "\u0001" + (it.groups["cue"]?.value ?: it.groups["cue2"]?.value ?: "").lowercase() + "\u0002" }
        var words = Regex("\\[[^\\]]*\\]").replace(s, " ").replace("*", "").squash()
        if (cues) words = words.replace('\u0001', '[').replace('\u0002', ']')
        return if (Regex("[\\p{L}\\p{Nd}]").containsMatchIn(uncued(words))) words else ""
    }

    // Sentences, a few at a time, so a long reply starts sounding before it's all made.
    fun pieces(text: String, most: Int = 240): List<String> {
        val sentences = Regex("(?<=[.!?…])\\s+").split(text.trim()).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty() && sb.length + s.length + 1 > most) { out.add(sb.toString()); sb.setLength(0) }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
