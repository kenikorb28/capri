package com.capri.core

// « Capri, vérifie-toi » : one line per function she really tried, with what she found. OK works, WARN works in part or
// needs a look, BAD doesn't work, OFF is something they chose not to use (never counted as a problem).
class Check(val name: String, val level: Int, val detail: String) {
    companion object { const val OK = 2; const val WARN = 1; const val BAD = 0; const val OFF = -1 }
}

object Checks {
    fun mark(level: Int): String = when (level) { Check.OK -> "🟢"; Check.WARN -> "🟡"; Check.BAD -> "🔴"; else -> "⚪" }

    // The full board, for a screen.
    fun report(list: List<Check>): String = list.joinToString("\n") { mark(it.level) + " " + it.name + Lang.pick(": ", " : ") + it.detail }

    // The short version, for her bubble and her voice: no symbols, the problems first.
    fun summary(list: List<Check>): String {
        val used = list.filter { it.level != Check.OFF }
        if (used.isEmpty()) return Lang.pick("I had nothing to check.", "Je n'avais rien à vérifier.")
        val good = used.count { it.level == Check.OK }
        val bad = used.filter { it.level == Check.BAD }
        val partly = used.filter { it.level == Check.WARN }
        val head = if (good == used.size) Lang.pick("Everything works: $good of ${used.size}.", "Tout marche : $good sur ${used.size}.")
        else Lang.pick("Result: $good of ${used.size}.", "Résultat : $good sur ${used.size}.")
        fun told(items: List<Check>) = items.joinToString(Lang.pick("; ", " ; ")) { it.name + " (" + it.detail.trimEnd('.') + ")" }
        return head + (if (bad.isEmpty()) "" else Lang.pick(" Not working: ", " Ne marche pas : ") + told(bad) + ".") +
            (if (partly.isEmpty()) "" else Lang.pick(" Worth a look: ", " À regarder : ") + told(partly) + ".")
    }
}
