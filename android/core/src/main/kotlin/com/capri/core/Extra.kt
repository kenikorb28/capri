package com.capri.core

// Plain phrases that reach the November tools with no brain call, so they work offline: a correction to keep, this
// month's usage, her self-check, the emergency stop and the backup page. The tools themselves are plugins of the app.
object ExtraPhrases {
    private val wrong = rxi("^(?:non[, ]+)?(?:tu t'es tromp[ée]e?|tu te trompes|c'est faux|correction|corrige[- ]toi|retiens (?:la|cette) correction|you(?:'re| are| were| got (?:it|that))? wrong|that'?s wrong|correct yourself)\\s*[:,;.!-]*\\s*(.*)$")
    private val fromNowOn = rxi("^((?:la prochaine fois|à l'avenir|dorénavant|désormais|à partir de maintenant|next time|from now on|in (?:the )?future)\\b[ ,:]+.+)$")
    private val listing = rxi("^(?:quelles? sont (?:tes|mes|les) corrections|(?:montre|liste|donne)(?:[- ]moi)? (?:tes|mes|les) corrections|(?:tes|mes) corrections|(?:show|list)(?: me)? (?:your|my|the) corrections|what corrections do you (?:have|keep))\\s*\\??$")
    private val used = rxi("^(?:combien (?:j'ai|ai-je|tu as|as-tu|on a|a-t-on) (?:consommé|dépensé|utilisé)\\b.*|(?:quelle est |c'est quoi )?(?:ma|ta|notre) (?:consommation|conso)\\b.*|où en est (?:mon|ton|le) (?:plafond|budget)\\b.*|how much (?:have i|did i|have you|did you|have we) (?:used|spent)\\b.*|(?:what'?s |show )?(?:my|your|our) usage\\b.*)$")
    private val checking = rxi("^(?:capri[, ]+)?(?:v[ée]rifie[- ]toi|v[ée]rifie que tout (?:marche|fonctionne)|est-ce que tout (?:marche|fonctionne)|fais (?:ton|un) auto[- ]?test|auto[- ]?test|check yourself|run (?:a |your )?self[- ]?(?:check|test)|self[- ]?(?:check|test)|is everything working)\\s*[.!?]*$")
    private val stopping = rxi("^(?:arr[êe]t d'urgence|stop total|coupe tout|emergency stop|kill switch)\\s*[.!]*$")
    private val resuming = rxi("^(?:reprends|tu peux reprendre|c'est bon,? (?:tu peux )?repren(?:ds|dre)|fin de l'arr[êe]t d'urgence|l[èe]ve l'arr[êe]t d'urgence|resume|you can resume|end (?:the )?emergency stop)\\s*[.!]*$")
    private val saving = rxi("^(?:(?:fais|cr[ée]e|lance)(?:[- ]moi)? une sauvegarde|sauvegarde (?:ta|la|ma) mémoire|restaure(?:r)? (?:une|la|ma) sauvegarde|(?:make|create) a backup|back ?up (?:your|my) memory|restore (?:a|my|the) backup)\\b.*$")

    fun correction(text: String): Call? {
        val t = text.trim()
        if (listing.matches(t)) return Call("correction", Args.of("action" to "list"))
        fromNowOn.matchEntire(t)?.let { return Call("correction", Args.of("action" to "add", "text" to it.groupValues[1])) }
        val what = (wrong.matchEntire(t) ?: return null).groupValues[1].trim()
        // "Tu t'es trompée" alone gives nothing to keep: that one is for her brain to answer.
        return if (what.split(Regex("\\s+")).size < 2) null else Call("correction", Args.of("action" to "add", "text" to what))
    }

    fun usage(text: String): Call? = if (used.matches(text.trim())) Call("usage_report", Args.of()) else null

    fun check(text: String): Call? = if (checking.matches(text.trim())) Call("self_check", Args.of()) else null

    fun emergency(text: String): Call? {
        val t = text.trim()
        return if (stopping.matches(t)) Call("emergency_stop", Args.of("action" to "stop")) else if (resuming.matches(t)) Call("emergency_stop", Args.of("action" to "resume")) else null
    }

    fun backup(text: String): Call? = if (saving.matches(text.trim())) Call("backup", Args.of()) else null
}
