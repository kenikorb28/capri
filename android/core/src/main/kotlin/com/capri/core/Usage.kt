package com.capri.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// What one reply cost, as its provider reported it; estimated from sizes (about four characters a token) when it didn't say.
class Tally(val input: Long, val output: Long, val exact: Boolean) {
    companion object {
        fun read(reply: String, sentBytes: Int): Tally {
            val root = try { Json.parseOrNull(reply).obj() } catch (e: Exception) { null }
            val u = root?.get("usage").obj()
            val g = root?.get("usageMetadata").obj()
            // Claude counts what it read from its cache apart from the rest of the prompt.
            val cached = (u?.get("cache_creation_input_tokens").num() ?: 0.0) + (u?.get("cache_read_input_tokens").num() ?: 0.0)
            val input = u?.get("prompt_tokens").num() ?: u?.get("input_tokens").num()?.let { it + cached } ?: g?.get("promptTokenCount").num() ?: root?.get("prompt_eval_count").num()
            val output = u?.get("completion_tokens").num() ?: u?.get("output_tokens").num() ?: g?.get("candidatesTokenCount").num() ?: root?.get("eval_count").num()
            if (input != null || output != null) return Tally((input ?: 0.0).toLong(), (output ?: 0.0).toLong(), true)
            return Tally(sentBytes / 4L, reply.length / 4L, false)
        }
    }
}

// CONSOMMATION: what her cloud brain (tokens) and her cloud voice (characters) used, month by month, kept in usage.json
// on the phone. Names are "brain:nvidia", "voice:fish"…; "brain:pc" and "voice:pc" are their own machine, shown but
// never counted against a limit.
class Usage(private val folder: File) {
    class Count { var asks = 0L; var input = 0L; var output = 0L; var chars = 0L; var saved = 0L; var guessed = 0L }

    private val file = File(folder, "usage.json")
    private val months = java.util.TreeMap<String, LinkedHashMap<String, Count>>()
    private var loaded = false

    companion object {
        const val Kept = 6
        fun month(now: Date = Date()): String = SimpleDateFormat("yyyy-MM", Locale.ROOT).format(now)
        fun cloud(name: String): Boolean = !name.endsWith(":pc")
        fun price(typed: String?): Double? = typed?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
        fun money(units: Long, perMillion: Double?): Double? = if (perMillion == null) null else units * perMillion / 1_000_000.0
        fun over(limit: Long, used: Long): Boolean = limit > 0 && used >= limit
        fun share(limit: Long, used: Long): Int = if (limit <= 0) 0 else (used * 100 / limit).toInt()
        fun n(v: Long): String = String.format(Locale.ROOT, "%,d", v).let { if (Lang.french) it.replace(',', ' ') else it }
        private fun cash(v: Double): String = String.format(Locale.ROOT, "%.2f", v).let { if (Lang.french) it.replace('.', ',') else it }
        fun shown(provider: String): String = when (provider) {
            "nvidia" -> "NVIDIA"; "google" -> "Gemini"; "openrouter" -> "OpenRouter"; "anthropic" -> "Claude"; "deepseek" -> "DeepSeek"; "openai" -> "OpenAI"
            "fish" -> "Fish Audio"; "elevenlabs" -> "ElevenLabs"; "pc" -> Lang.pick("your PC", "ton PC"); else -> provider
        }
    }

    private fun Any?.long(): Long = this.num()?.toLong() ?: 0L

    private fun load() {
        if (loaded) return
        loaded = true
        try {
            val root = Json.parseOrNull(if (file.exists()) file.readText() else null).obj() ?: return
            for ((month, names) in root) {
                val into = LinkedHashMap<String, Count>()
                for ((name, value) in names.obj() ?: continue) {
                    val o = value.obj() ?: continue
                    into[name] = Count().apply { asks = o["asks"].long(); input = o["in"].long(); output = o["out"].long(); chars = o["chars"].long(); saved = o["saved"].long(); guessed = o["guessed"].long() }
                }
                months[month] = into
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        try {
            while (months.size > Kept) months.remove(months.firstKey())
            val root = LinkedHashMap<String, Any?>()
            for ((month, names) in months) root[month] = names.mapValues { (_, c) -> linkedMapOf<String, Any?>("asks" to c.asks, "in" to c.input, "out" to c.output, "chars" to c.chars, "saved" to c.saved, "guessed" to c.guessed) }
            folder.mkdirs()
            val fresh = File(file.path + ".new")
            fresh.writeText(Json.write(root))
            if (!fresh.renameTo(file)) { file.delete(); fresh.renameTo(file) }
        } catch (e: Exception) { }
    }

    private fun count(name: String, now: Date): Count { load(); return months.getOrPut(month(now)) { LinkedHashMap() }.getOrPut(name) { Count() } }

    fun brain(provider: String, tally: Tally, now: Date = Date()) = synchronized(this) {
        val c = count("brain:$provider", now)
        c.asks++; c.input += tally.input; c.output += tally.output
        if (!tally.exact) c.guessed++
        save()
    }

    fun voice(provider: String, chars: Int, now: Date = Date()) = synchronized(this) { val c = count("voice:$provider", now); c.asks++; c.chars += chars; save() }

    // A line her voice cache played instead of asking the cloud for it again.
    fun saved(provider: String, chars: Int, now: Date = Date()) = synchronized(this) { count("voice:$provider", now).saved += chars; save() }

    private fun thisMonth(now: Date): Map<String, Count> { load(); return months[month(now)] ?: emptyMap() }

    // This month's totals that count against a limit: cloud only.
    fun tokens(now: Date = Date()): Long = synchronized(this) { thisMonth(now).filter { it.key.startsWith("brain:") && cloud(it.key) }.values.sumOf { it.input + it.output } }
    fun chars(now: Date = Date()): Long = synchronized(this) { thisMonth(now).filter { it.key.startsWith("voice:") && cloud(it.key) }.values.sumOf { it.chars } }

    fun reset() = synchronized(this) { load(); months.clear(); save() }

    fun report(capBrain: Long, capVoice: Long, priceBrain: Double?, priceVoice: Double?, now: Date = Date()): String = synchronized(this) {
        val lines = ArrayList<String>()
        for ((name, c) in thisMonth(now)) {
            val who = shown(name.substringAfter(':'))
            val free = if (cloud(name)) "" else Lang.pick(" (never counted)", " (hors plafond)")
            val asks = n(c.asks) + " " + if (name.startsWith("brain:")) (if (c.asks == 1L) Lang.pick("ask", "demande") else Lang.pick("asks", "demandes")) else (if (c.asks == 1L) Lang.pick("line", "phrase") else Lang.pick("lines", "phrases"))
            if (name.startsWith("brain:")) lines.add(Lang.pick("Brain, $who: $asks, ${n(c.input + c.output)} tokens (${n(c.input)} read, ${n(c.output)} written)",
                "Cerveau, $who : $asks, ${n(c.input + c.output)} jetons (${n(c.input)} lus, ${n(c.output)} écrits)") + (if (c.guessed > 0) Lang.pick(", partly estimated", ", en partie estimés") else "") + free)
            else lines.add(Lang.pick("Voice, $who: $asks, ${n(c.chars)} characters", "Voix, $who : $asks, ${n(c.chars)} caractères") +
                (if (c.saved > 0) Lang.pick("; ${n(c.saved)} more spared by the voice cache", " ; ${n(c.saved)} de plus évités par le cache vocal") else "") + free)
        }
        if (lines.isEmpty()) lines.add(Lang.pick("Nothing used this month.", "Rien consommé ce mois-ci."))
        val t = tokens(now)
        val c = chars(now)
        if (capBrain > 0) lines.add(Lang.pick("Brain limit: ${n(t)} of ${n(capBrain)} tokens (${share(capBrain, t)} %)", "Plafond cerveau : ${n(t)} sur ${n(capBrain)} jetons (${share(capBrain, t)} %)"))
        if (capVoice > 0) lines.add(Lang.pick("Voice limit: ${n(c)} of ${n(capVoice)} characters (${share(capVoice, c)} %)", "Plafond voix : ${n(c)} sur ${n(capVoice)} caractères (${share(capVoice, c)} %)"))
        val costs = listOfNotNull(money(t, priceBrain)?.let { Lang.pick("brain", "cerveau") + " ≈ " + cash(it) }, money(c, priceVoice)?.let { Lang.pick("voice", "voix") + " ≈ " + cash(it) })
        if (costs.isNotEmpty()) lines.add(Lang.pick("Estimated cost, from your prices: ", "Coût estimé, d'après tes prix : ") + costs.joinToString(Lang.pick(", ", " ; ")))
        lines.joinToString("\n")
    }
}
