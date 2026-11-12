package com.capri.core

// Fish Audio's text-to-speech call, built in one place so every surface asks for it the same way: the phone sends what
// the desktop sends (POST /v1/tts, the key as a Bearer token, the model in a header, the voice as reference_id).
// Checked against docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech on 12 Nov 2026.
object Fish {
    const val Url = "https://api.fish.audio/v1/tts"

    // The free developer tier is a model of its own. Paid models answer 402 on an account with no credit, and a name
    // Fish Audio doesn't know quietly becomes its paid default (s2.1-pro), so a typo ends in 402 as well.
    const val Free = "s2.1-pro-free"
    val Known = listOf(Free, "s2.1-pro", "s2-pro", "s1", "drama-3")

    // Desktop test builds saved this name in the Voice box. It means blank, so Fish Audio picks its own default.
    private const val OldDefault = "Capri Default"
    private val link = Regex("fish\\.audio/\\S*?([0-9a-f]{32})", RegexOption.IGNORE_CASE)
    private val spaces = Regex("\\s+")

    // One request, ready to send: nothing here touches the network, so the same call is built on a phone, a PC or in a test.
    class Call(val url: String, val headers: Map<String, String>, val body: ByteArray, val model: String)

    // A voice's ID, taken from its fish.audio link if that's what they pasted, the desktop's way. Blank lets Fish Audio pick.
    fun voiceId(typed: String?): String {
        val voice = (typed ?: "").trim()
        link.find(voice)?.let { return it.groupValues[1].lowercase() }
        if (voice.equals(OldDefault, ignoreCase = true)) return ""
        return if (voice.contains('/')) voice.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/') else voice
    }

    fun modelName(typed: String?): String = (typed ?: "").trim().ifEmpty { Free }

    // Her speed setting (50 to 200 %) as Fish Audio's multiplier (0.5 to 2.0). Null at 100 %: nothing to ask for.
    fun speed(percent: Int): Double? = if (percent == 100) null else percent.coerceIn(50, 200) / 100.0

    // light: the saver profile's smaller file (64 kbps instead of 128), a shorter download and less decoding.
    fun call(key: String, text: String, voice: String? = null, model: String? = null, speedPercent: Int = 100, light: Boolean = false): Call {
        val name = modelName(model)
        val body = LinkedHashMap<String, Any?>()
        body["text"] = text
        voiceId(voice).let { if (it.isNotEmpty()) body["reference_id"] = it }
        body["format"] = "mp3"
        if (light) body["mp3_bitrate"] = 64
        speed(speedPercent)?.let { body["prosody"] = mapOf("speed" to it) }
        val headers = linkedMapOf("Authorization" to "Bearer " + key.trim(), "Content-Type" to "application/json", "model" to name)
        return Call(Url, headers, Json.write(body).toByteArray(Charsets.UTF_8), name)
    }

    // A paid model on an account with no credit shouldn't cost her her voice: the free one can say the line instead.
    fun retryFree(status: Int, model: String?): Boolean = status == 402 && modelName(model) != Free

    // What went wrong, in words they can act on. The key never appears, whatever Fish Audio sent back.
    fun problem(status: Int, body: ByteArray?, model: String?, key: String? = null): String {
        val name = modelName(model)
        val said = said(body, key)
        val more = if (said.isEmpty()) "" else " ($said)"
        return when (status) {
            401, 403 -> Lang.pick("Fish Audio turned down the key (HTTP $status). Check it on fish.audio.", "Fish Audio a refusé la clé (HTTP $status). Vérifie-la sur fish.audio.")
            402 -> if (name == Free) Lang.pick("Fish Audio wants credit even for its free model (HTTP 402)$more.", "Fish Audio demande du crédit même pour son modèle gratuit (HTTP 402)$more.")
            else Lang.pick("Fish Audio wants credit for $name (HTTP 402). For its free tier, set Model to $Free.", "Fish Audio demande du crédit pour $name (HTTP 402). Pour l'offre gratuite, mets le modèle $Free.")
            404 -> Lang.pick("Fish Audio can't find that voice (HTTP 404). Check its ID or link.", "Fish Audio ne trouve pas cette voix (HTTP 404). Vérifie son ID ou son lien.")
            429 -> Lang.pick("Fish Audio is busy (HTTP 429). Try again in a moment.", "Fish Audio est occupé (HTTP 429). Réessaie dans un instant.")
            in 500..599 -> Lang.pick("Fish Audio is having trouble (HTTP $status). Try again later.", "Fish Audio a un souci de son côté (HTTP $status). Réessaie plus tard.")
            else -> Lang.pick("Fish Audio said HTTP $status", "Fish Audio a répondu HTTP $status") + (if (said.isEmpty()) "." else ": $said")
        }
    }

    // Fish Audio's own words from an error reply ({status, message, reason}), short and with nothing secret in them.
    private fun said(body: ByteArray?, key: String?): String {
        if (body == null || body.isEmpty()) return ""
        val raw = String(body, 0, minOf(body.size, 2000), Charsets.UTF_8).trim()
        if (raw.startsWith("<")) return ""
        val reply = try { Json.parseOrNull(raw).obj() } catch (e: Exception) { null }
        var words = (if (reply == null) raw else reply["message"].str() ?: reply["detail"].str() ?: "").replace(spaces, " ").trim()
        if (!key.isNullOrBlank()) words = words.replace(key.trim(), "[redacted]")
        words = Redact.text(words)
        return if (words.length > 140) words.substring(0, 140).trimEnd() + "…" else words
    }
}
