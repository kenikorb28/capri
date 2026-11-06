package com.capri.core.link

import com.capri.core.Memory

// The three-way merges behind Capri's shared memory (contract/memory-v2.md). Each one takes the version both devices had at
// their last sync (null before the first), this device's file and the other device's file, and returns the file both keep.
// A line both had at the last sync stays only if both still have it; a line added on either side stays. So "oublie ça" on
// one device wins on the other, and nothing either side added is lost. Before the first sync there is no base: both
// devices' lines are kept.
object Merge {
    // Lines Capri Desktop's "getting to know you" writes one of each (Brain.Jot): a second one replaces the first.
    private val labels = listOf("name", "pronouns", "into")
    private val labelled = Regex("^-\\s*([^:]{1,40}?)\\s*:\\s*(.*)$")

    fun key(line: String): String = line.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun split(text: String): List<String> = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    // ---- me.md: a head of # and > lines, then one note per line

    fun notes(base: String?, mine: String, theirs: String, mineIsNewer: Boolean = true): String {
        if (mine.isBlank() && theirs.isBlank()) return mine
        val m = noteParts(mine)
        val t = noteParts(theirs)
        val b = base?.let { noteParts(it) }
        val kept = oneOfEachLabel(items(b?.second, m.second, t.second), b?.second ?: emptyList(), m.second, t.second, mineIsNewer)
        if (theirs.isNotBlank()) return patched(theirs, kept)
        val head = m.first.ifBlank { Memory.NotesHead.trimEnd() }
        return head.trimEnd() + "\n\n" + kept.joinToString("") { it + "\n" }
    }

    // The other device's file with the notes both now drop taken out and the new ones added at its end. Everything else in
    // it (its head, headings, blank lines) stays where it was, so a note in their Obsidian vault keeps its shape, and a file
    // with nothing to change comes back exactly as it was.
    private fun patched(theirs: String, kept: List<String>): String {
        val want = LinkedHashMap<String, String>()
        for (line in kept) want.putIfAbsent(key(line), line)
        val out = ArrayList<String>()
        val placed = HashSet<String>()
        var dropped = false
        for (raw in split(theirs)) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(">")) { out.add(raw); continue }
            val k = key(line)
            if (k in want && placed.add(k)) out.add(raw) else dropped = true
        }
        val added = want.filterKeys { it !in placed }.values
        if (!dropped && added.isEmpty()) return theirs
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.size - 1)
        return (out + added).joinToString("\n") + "\n"
    }

    private fun noteParts(text: String): Pair<String, List<String>> {
        val head = ArrayList<String>()
        val notes = ArrayList<String>()
        for (raw in split(text)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith(">")) head.add(line) else notes.add(line)
        }
        return head.joinToString("\n") to notes
    }

    // The heart of it: a three-way merge of two lists of lines, each line by its words (case and spacing aside).
    fun items(base: List<String>?, mine: List<String>, theirs: List<String>): List<String> {
        val inBase = base?.map(::key)?.toHashSet()
        val inMine = mine.map(::key).toHashSet()
        val inTheirs = theirs.map(::key).toHashSet()
        fun stays(k: String) = if (inBase != null && k in inBase) k in inMine && k in inTheirs else true
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (line in mine + theirs) {
            val k = key(line)
            if (stays(k) && seen.add(k)) out.add(line.trim())
        }
        return out
    }

    private fun oneOfEachLabel(kept: List<String>, base: List<String>, mine: List<String>, theirs: List<String>, mineIsNewer: Boolean): List<String> {
        val baseKeys = base.map(::key).toHashSet()
        val mineKeys = mine.map(::key).toHashSet()
        val theirKeys = theirs.map(::key).toHashSet()
        val out = kept.toMutableList()
        for (label in labels) {
            val same = out.filter { labelled.find(it)?.groupValues?.get(1)?.trim()?.lowercase() == label }
            if (same.size < 2) continue
            val changed = same.filter { key(it) !in baseKeys }
            val pool = if (changed.isEmpty()) same else changed
            val keep = pool.firstOrNull { if (mineIsNewer) key(it) in mineKeys else key(it) in theirKeys } ?: pool.first()
            out.removeAll { it in same && it != keep }
        }
        return out
    }

    // ---- memories.md and project files: a head, then "## " sections of lines

    class Doc(val head: String, val sections: List<Pair<String, List<String>>>)

    fun parse(text: String): Doc {
        val head = ArrayList<String>()
        val sections = ArrayList<Pair<String, MutableList<String>>>()
        for (raw in split(text)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("## ") || line == "##") { sections.add(line to ArrayList()); continue }
            if (sections.isEmpty() && (line.startsWith("#") || line.startsWith(">"))) { head.add(line); continue }
            if (sections.isEmpty()) sections.add("" to ArrayList())
            sections.last().second.add(line)
        }
        return Doc(head.joinToString("\n"), sections)
    }

    private fun sectioned(base: String?, mine: String, theirs: String, keyOf: (String) -> String): Triple<String, List<String>, Map<String, List<String>>> {
        val m = parse(mine)
        val t = parse(theirs)
        val b = base?.let { parse(it) }
        fun pairs(d: Doc?): List<String>? = d?.sections?.flatMap { (h, lines) -> lines.map { keyOf(h) + "\u0000" + it } }
        val kept = items(pairs(b), pairs(m)!!, pairs(t)!!)
        val headings = LinkedHashMap<String, String>()
        for ((h, _) in m.sections + t.sections) headings.putIfAbsent(keyOf(h), h)
        val bySection = LinkedHashMap<String, MutableList<String>>()
        for (h in headings.keys) bySection[h] = ArrayList()
        for (pair in kept) {
            val at = pair.indexOf('\u0000')
            bySection.getOrPut(pair.substring(0, at)) { ArrayList() }.add(pair.substring(at + 1))
        }
        val head = m.head.ifBlank { t.head }
        return Triple(head, headings.keys.toList(), bySection.mapValues { (k, v) -> listOf(headings[k] ?: k) + v })
    }

    // memories.md: one section a day, "Earlier" first, then the days in order; a day with nothing left goes.
    fun memories(base: String?, mine: String, theirs: String): String {
        if (mine.isBlank() && theirs.isBlank()) return mine
        val (head, keys, sections) = sectioned(base, mine, theirs, ::dayKey)
        val order = keys.withIndex().sortedWith(compareBy({ rank(it.value) }, { if (rank(it.value) == 1) it.value else "" }, { it.index })).map { it.value }
        val lines = ArrayList<String>()
        for (k in order) {
            val section = sections[k] ?: continue
            if (section.size < 2) continue
            if (section[0].isNotEmpty()) lines.add(section[0])
            lines.addAll(section.drop(1))
        }
        return head.ifBlank { Memory.MemoriesHead.trimEnd() }.trimEnd() + "\n\n" + lines.joinToString("") { it + "\n" }
    }

    private fun rank(dayKey: String) = when { dayKey == "earlier" -> 0; Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dayKey) -> 1; else -> 2 }

    private val months = linkedMapOf(
        "january" to 1, "janvier" to 1, "janv" to 1, "jan" to 1, "february" to 2, "février" to 2, "fevrier" to 2, "févr" to 2, "fevr" to 2, "feb" to 2,
        "march" to 3, "mars" to 3, "mar" to 3, "april" to 4, "avril" to 4, "avr" to 4, "apr" to 4, "may" to 5, "mai" to 5,
        "june" to 6, "juin" to 6, "jun" to 6, "july" to 7, "juillet" to 7, "juil" to 7, "jul" to 7, "august" to 8, "août" to 8, "aout" to 8, "aug" to 8,
        "september" to 9, "septembre" to 9, "sept" to 9, "sep" to 9, "october" to 10, "octobre" to 10, "oct" to 10,
        "november" to 11, "novembre" to 11, "nov" to 11, "december" to 12, "décembre" to 12, "decembre" to 12, "déc" to 12, "dec" to 12)

    // "## Tuesday 3 March 2026" and "## Mardi 3 mars 2026" are both 2026-03-03; "## Earlier" is "earlier".
    fun dayKey(heading: String): String {
        val h = heading.trimStart('#', ' ').trim().lowercase()
        if (Regex("^(earlier|plus tôt|auparavant|avant)\\b").containsMatchIn(h)) return "earlier"
        var day: Int? = null
        var month: Int? = null
        var year: Int? = null
        for (token in Regex("\\p{L}+|\\d+").findAll(h).map { it.value }) {
            if (token[0].isDigit()) {
                val n = token.toIntOrNull() ?: continue
                if (token.length == 4 && year == null) year = n else if (token.length <= 2 && n in 1..31 && day == null) day = n
            } else if (month == null) month = months[token]
        }
        return if (day != null && month != null && year != null) "%04d-%02d-%02d".format(year, month, day) else h
    }

    // A project file (projects/<name>.md): its head, then its sections in the order this device has them. Under
    // "Dernière tâche" only the newest line stays.
    fun project(base: String?, mine: String, theirs: String): String {
        val (head, keys, sections) = sectioned(base, mine, theirs) { it.trim().lowercase() }
        val blocks = ArrayList<String>()
        for (k in keys) {
            val section = sections[k] ?: continue
            var lines = section.drop(1)
            if (k.contains("dernière tâche") || k.contains("derniere tache") || k.contains("last task")) lines = lines.sortedByDescending { it.removePrefix("- ").take(16) }.take(1)
            blocks.add((if (section[0].isEmpty()) "" else section[0] + "\n") + lines.joinToString("") { it + "\n" })
        }
        return (if (head.isBlank()) "" else head.trimEnd() + "\n\n") + blocks.joinToString("\n").trimEnd('\n') + "\n"
    }

    // ---- personality.md and anything kept whole: the side that changed it wins; if both did, the newer one, and the other
    // is handed back to be kept beside it.
    class Whole(val text: String?, val other: String?)

    fun whole(base: String?, mine: String?, theirs: String?, mineIsNewer: Boolean): Whole = when {
        mine == theirs -> Whole(mine, null)
        mine == base -> Whole(theirs, null)
        theirs == base -> Whole(mine, null)
        mineIsNewer -> Whole(mine, theirs)
        else -> Whole(theirs, mine)
    }
}

// Lines of the daily chat logs (transcripts/AAAA-MM-JJ.md). They only ever grow: each device sends its own lines, and the
// other adds them once, marked with where they were said, so her recall on either device finds both.
object Journal {
    private val talk = Regex("^\\*\\*(\\d\\d:\\d\\d) ([^*]+?):\\*\\* (.*)$")
    private val elsewhere = Regex("\\((PC|phone|téléphone)\\)$")

    fun own(line: String): Boolean {
        val m = talk.find(line.trim()) ?: return false
        return !elsewhere.containsMatchIn(m.groupValues[2].trim())
    }

    fun tag(line: String, from: String): String? {
        val m = talk.find(line.trim()) ?: return null
        val who = m.groupValues[2].trim()
        if (elsewhere.containsMatchIn(who)) return null
        return "**" + m.groupValues[1] + " " + who + " (" + from + "):** " + m.groupValues[3]
    }
}
