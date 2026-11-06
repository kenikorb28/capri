package com.capri.core

// User-facing words in French when she speaks French, English otherwise. Tool results for her brain stay English, like the desktop.
object Lang {
    @Volatile var french = true
    fun pick(en: String, fr: String): String = if (french) fr else en
}

open class BrainError(message: String, val told: Boolean = false, val status: Int = 0, val detail: String? = null, val retryAt: Long = 0L) : Exception(message)

// Where her brain lives. "ollama" talks Ollama's own API (on their PC over Wi-Fi, or in Termux on the phone), "local" any
// OpenAI-style server (LM Studio, llama.cpp, Jan, KoboldCpp), and the rest are cloud keys, told apart by how they start.
class Source(val kind: String, val host: String?, val key: String?, @Volatile var model: String) {
    val local: Boolean get() = kind == "ollama" || kind == "local"
    val claude: Boolean get() = kind == "anthropic"
    val company: String get() = companyOf(kind)
    val name: String get() = when (kind) { "anthropic" -> "Claude"; "google" -> "Gemini"; "openai" -> "ChatGPT"; "ollama" -> "Ollama"; "local" -> Lang.pick("Local server", "Serveur local"); else -> company }
    val id: String get() = kind + "|" + (host ?: "") + "|" + model

    companion object {
        const val Ollama = "http://127.0.0.1:11434"
        const val OpenAi = "https://api.openai.com"
        const val Anthropic = "https://api.anthropic.com"
        const val DeepSeek = "https://api.deepseek.com"
        const val Nvidia = "https://integrate.api.nvidia.com"
        const val Google = "https://generativelanguage.googleapis.com/v1beta/openai"
        const val OpenRouter = "https://openrouter.ai/api/v1"
        // The desktop's Claude API version (0.8.2). Older accounts that turn it away get the long-standing one instead.
        const val AnthropicVersion = "2026-10-21"
        const val AnthropicFallbackVersion = "2023-06-01"

        fun companyOf(kind: String?): String = when (kind) {
            "anthropic" -> "Anthropic"; "deepseek" -> "DeepSeek"; "nvidia" -> "NVIDIA"; "google" -> "Google AI"; "openrouter" -> "OpenRouter"; "openai" -> "OpenAI"
            "ollama" -> "Ollama"; "local" -> Lang.pick("your server", "ton serveur"); else -> Lang.pick("That provider", "Ce fournisseur")
        }

        fun kindOf(key: String?): String? {
            if (key.isNullOrEmpty()) return null
            if (key.startsWith("AIza") || key.startsWith("AQ.")) return "google"
            if (key.startsWith("sk-or-")) return "openrouter"
            if (key.startsWith("sk-nous-")) return null
            if (key.startsWith("sk-ant-")) return "anthropic"
            if (key.startsWith("nvapi-")) return "nvidia"
            if (Regex("^sk-[0-9a-f]{32}$").matches(key)) return "deepseek"
            return if (key.startsWith("sk-")) "openai" else null
        }

        fun mediumKind(kind: String?) = kind == "nvidia" || kind == "google" || kind == "openrouter"

        fun address(kind: String, path: String): String = when (kind) {
            "anthropic" -> "$Anthropic/v1$path"
            "deepseek" -> DeepSeek + path
            "nvidia" -> "$Nvidia/v1$path"
            "google" -> Google + path
            "openrouter" -> OpenRouter + path
            "openai" -> "$OpenAi/v1$path"
            else -> throw BrainError(Lang.pick("That key's provider isn't supported.", "Ce fournisseur de clé n'est pas pris en charge."), true)
        }

        // A server's address as typed: http:// when it has none, and no /v1 or /chat/completions on the end.
        fun serverAddress(typed: String?): String? {
            var a = (typed ?: "").trim().trimEnd('/')
            if (a.isEmpty()) return null
            if (!Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(a)) a = "http://$a"
            a = Regex("(/v1)?(/chat/completions)?/?$", RegexOption.IGNORE_CASE).replace(a, "")
            return try {
                val u = java.net.URL(a)
                if ((u.protocol != "http" && u.protocol != "https") || u.host.isEmpty()) null else a
            } catch (e: Exception) { null }
        }

        fun serverName(host: String?): String {
            val port = try { java.net.URL(host).port } catch (e: Exception) { -1 }
            return when (port) { 11434 -> "Ollama"; 1234 -> "LM Studio"; 1337 -> "Jan"; 5001 -> "KoboldCpp"; 8080 -> "llama.cpp"; else -> Lang.pick("Local server", "Serveur local") + if (port > 0) " :$port" else "" }
        }
    }
}

object Problems {
    val Gone get() = Lang.pick("That model isn't on your key. Pick another one.", "Ce modèle n'est pas disponible avec ta clé. Choisis-en un autre.")
    val Down get() = Lang.pick("Their servers failed. Pick another model or retry.", "Leurs serveurs ont planté. Choisis un autre modèle ou réessaie.")
    val Busy get() = Lang.pick("Too many requests. Wait a minute.", "Trop de requêtes. Attends une minute.")
    val NewKey get() = Lang.pick("Key rejected. Make a new Google AI Studio key and paste it.", "Clé refusée. Crée une nouvelle clé Google AI Studio et colle-la.")
    val Unserved get() = Lang.pick("Google doesn't serve Gemini where you are. Try a free NVIDIA key.", "Google ne fournit pas Gemini là où tu es. Essaie une clé NVIDIA gratuite.")
    val Blocked get() = Lang.pick("Their API is blocked where you are, so a new key won't help. Pick another service, or your own Ollama.", "Leur API est bloquée là où tu es : une nouvelle clé n'y changera rien. Choisis un autre service, ou ton propre Ollama.")
    val Credits get() = Lang.pick("No credits on this key. Add credits on their site, or use a free NVIDIA key.", "Pas de crédits sur cette clé. Ajoute des crédits sur leur site, ou utilise une clé NVIDIA gratuite.")
    val Wordless get() = Lang.pick("My brain sent an empty reply. Try again or pick another model.", "Mon cerveau a renvoyé une réponse vide. Réessaie ou choisis un autre modèle.")
    val Answerless get() = Lang.pick("That model sent thoughts without an answer. Pick another model.", "Ce modèle a réfléchi sans répondre. Choisis un autre modèle.")
    val Gibberish get() = Lang.pick("My brain sent gibberish. Pick another model.", "Mon cerveau a renvoyé du charabia. Choisis un autre modèle.")
    val Offline get() = Lang.pick("My brain's offline. Open Capri's settings and connect a brain under CERVEAU: a free NVIDIA key, Gemini, or Ollama on your PC.",
        "Mon cerveau est hors ligne. Ouvre les réglages de Capri et branche un cerveau dans CERVEAU : une clé NVIDIA gratuite, Gemini, ou Ollama sur ton PC.")
    val Declined = "[SIGNAL CORRUPTED] Lost that one in the static. Ask me something else?"

    private val blockPage = rxi("security policy|access restricted|unsupported[_ ]country|country, region,? or territory|(not available|not supported|unavailable) in your (country|region|location)")

    fun meaning(status: Int, body: String?, google: Boolean = false, openai: Boolean = false): String? {
        val b = body ?: ""
        if (status == 429 && rxi("RESOURCE_EXHAUSTED|rate[_ -]?limit|too many requests").containsMatchIn(b)) return Busy
        if (status == 451 || status == 403 && (blockPage.containsMatchIn(b) || b.trimStart().startsWith("<"))) return Blocked
        if (google && status == 400 && rxi("location is not supported|in your (country|region)").containsMatchIn(b)) return Unserved
        if (status == 402 || rxi("insufficient[_ ](quota|balance)|no credits|credit balance|billing").containsMatchIn(b)) return Credits
        if (google && status == 400 && b.contains("FAILED_PRECONDITION")) return Unserved
        if (google && (status == 401 || status == 403 || (status == 400 && rxi("API[_ ]?key|credential|Authorization header").containsMatchIn(b)))) return NewKey
        if (status == 401 || status == 403) return if (openai) Lang.pick("Key rejected. ChatGPT Plus doesn't include API use, so make a key with credits at platform.openai.com.", "Clé refusée. ChatGPT Plus n'inclut pas l'API : crée une clé avec des crédits sur platform.openai.com.") else Lang.pick("Key rejected. Forget it, then paste it again.", "Clé refusée. Oublie-la, puis colle-la à nouveau.")
        if (status == 429) return Busy
        if (status == 404) return Gone
        if (status >= 500) return Down
        return null
    }

    fun problemText(body: String?): String? {
        val m = Regex("\"(message|error|detail|msg)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body ?: "")
        if (m != null) return Redact.text(unescape(m.groupValues[2]))
        val plain = (body ?: "").trim()
        return if (plain.isEmpty() || plain.length > 200 || plain[0] == '<') null else Redact.text(plain)
    }

    fun unescape(json: String): String = Regex("\\\\(u[0-9a-fA-F]{4}|.)").replace(json) {
        val code = it.groupValues[1]
        when {
            code.length == 5 -> code.substring(1).toInt(16).toChar().toString()
            code == "n" -> "\n"; code == "t" -> "\t"; code == "r" || code == "b" || code == "f" -> ""
            else -> code
        }
    }

    fun retryAt(header: String?, now: Long = System.currentTimeMillis()): Long {
        val seconds = header?.trim()?.toDoubleOrNull()
        if (seconds != null && seconds >= 0 && seconds < 86400 * 7) return now + (maxOf(1.0, seconds) * 1000).toLong()
        return now + 60_000
    }

    fun toolless(problem: String?): Boolean =
        rxi("does not support (tools|tool use|function calling)|doesn't support tools|(?:tools?|tool use|function calling) (?:are |is )?(?:not supported|unsupported)|unsupported (?:tools?|function calling)|(?:tools?|tool_choice).*(?:not supported|not allowed|not enabled)|no endpoints found that support tool").containsMatchIn(problem ?: "")

    fun noVision(error: BrainError): Boolean {
        val reason = error.detail ?: error.message ?: ""
        if (rxi("multimodal (?:data|processing)\\b[^.]{0,60}\\bnot enabled|--enable-multimodal").containsMatchIn(reason)) return true
        if (error.status != 400 && error.status != 422) return false
        if (rxi("\\b(size|dimensions?|resolution|format|mime|base64|decode|decoding|too large|too many|invalid)\\b").containsMatchIn(reason)) return false
        return rxi("image_url.{0,40}only supported|(?:does not|doesn't|cannot|can't) support (?:\\w+ ){0,2}(?:images?|vision|multimodal)|(?:images?|vision|multimodal)(?: inputs?| content| messages?| processing)? (?:is |are )?(?:not supported|unsupported|not enabled)|(?:not a|is not a) (?:vision|multimodal) model|(?:only supports? text|text.only model)").containsMatchIn(reason)
    }

    // In plain words, for her bubble, when a request never reached an answer.
    fun explain(error: Throwable, source: Source?): String? = when {
        error is HttpCancelled -> null
        error is BrainError -> error.message
        error is HttpTimeout && source != null && source.local -> Lang.pick("${source.name} took too long. Big models are slow on a phone or an old PC: try a smaller one.", "${source.name} a mis trop de temps. Les gros modèles sont lents : essaie un modèle plus petit.")
        error is HttpTimeout && source != null -> Lang.pick("${source.company} didn't answer in time. Try again or pick another model.", "${source.company} n'a pas répondu à temps. Réessaie ou choisis un autre modèle.")
        error is HttpUnreachable && source != null && source.local -> Lang.pick("Can't reach ${source.name} at ${source.host}. Is it running, on the same Wi-Fi, and open to the network?", "Impossible de joindre ${source.name} à ${source.host}. Est-il lancé, sur le même Wi-Fi, et ouvert au réseau (OLLAMA_HOST=0.0.0.0) ?")
        error is HttpUnreachable && source != null -> Lang.pick("Can't reach ${source.company}. Check your internet.", "Impossible de joindre ${source.company}. Vérifie ta connexion internet.")
        error is HttpUnreachable -> Problems.Offline
        else -> "[SIGNAL CORRUPTED] " + Lang.pick("Something in my head just sparked. Try me again?", "Un truc vient de griller dans ma tête. Tu réessaies ?")
    }
}

// Which models a key offers, sorted the desktop's way: NVIDIA's picks first, Gemini's stable Flash, OpenRouter's free
// ones that see and take tools.
object Models {
    val NvidiaPicks = listOf("nvidia/nemotron-3.5-super-vl-120b-a12b", "nvidia/nemotron-3.5-bolt-e4b", "openai/gpt-oss-20b", "meta/llama-3.2-11b-vision-instruct")
    val NvidiaRecommended = listOf("nvidia/nemotron-3.5-super-vl-120b-a12b", "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning")
    val NvidiaDead = setOf("google/gemma-4-31b-it", "deepseek-ai/deepseek-v4.1-flash", "z-ai/glm-5.3", "z-ai/glm-5.3-flash", "moonshotai/kimi-k2.6", "google/gemma-3-12b-it", "meta/llama-3.2-90b-vision-instruct")
    const val ClaudeFastest = "claude-haiku-5-5"
    val OllamaFavourites = listOf("gemma4:e4b", "gemma4:e2b", "qwen3:4b-instruct-2507", "qwen3.5:4b", "qwen3:4b", "llama3.2:3b", "qwen3:1.7b", "llama3.2:1b", "gemma3:1b")

    class Listed(val id: String, val created: Long, val createdAt: String, val inputs: List<String>, val outputs: List<String>, val params: List<String>) {
        val images get() = "image" in inputs
        val tools get() = "tools" in params
    }

    private val known = HashMap<String, Listed>()

    fun listed(kind: String, id: String): Listed? = synchronized(known) { known["$kind|$id"] }

    fun chats(kind: String, id: String): Boolean = when (kind) {
        "google" -> id.startsWith("gemini-") && !rxi("embed|image|audio|tts|live|robotics|computer-use|aqa|transcribe|banana|omni").containsMatchIn(id)
        "openai" -> Regex("^(gpt-|o\\d)").containsMatchIn(id) && !Regex("audio|realtime|tts|transcribe|image|search|embed|instruct|codex|moderation|-\\d{4}-\\d{2}-\\d{2}$").containsMatchIn(id)
        "nvidia" -> rxi("instruct|chat|-it\\b|-it-|gpt-oss|kimi|glm|deepseek|qwen|qwq|llama|gemma|mistral|mixtral|magistral|ministral|nemotron|phi-|granite|minimax|hermes|seed|jamba|falcon|solar|olmo|exaone").containsMatchIn(id) &&
            !rxi("embed|rerank|reward|guard|safety|topic-control|parse|clip|retriev|translate|deplot|kosmos|vila|fuyu|neva|calibration|cod|chatqa|palmyra|cosmos|riva|diffusion|recurrent|llama2|-2b$|detect|synthetic|video|ocr|asr|tts|speech|audio|image|segment|depth|pose|classif").containsMatchIn(id)
        else -> true
    }

    private fun flashPick(id: String): Int {
        if (!id.contains("flash", true)) return 4
        return (if (rxi("preview|experimental|\\bexp\\b").containsMatchIn(id)) 2 else 0) + (if (id.contains("lite", true)) 1 else 0)
    }

    private fun geminiVersion(id: String): Double {
        val m = Regex("^gemini-(\\d+)(?:\\.(\\d+))?").find(id) ?: return 0.0
        return (m.groupValues[1] + "." + m.groupValues[2].ifEmpty { "0" }).toDoubleOrNull() ?: 0.0
    }

    private fun routerPick(m: Listed): Int = (if (m.id.endsWith(":free")) 0 else 4) + (if (m.images) 0 else 2) + (if (m.tools) 0 else 1)

    private fun nvidiaPick(id: String): Int {
        val r = NvidiaRecommended.indexOf(id)
        if (r >= 0) return r
        val p = NvidiaPicks.indexOf(id)
        return NvidiaRecommended.size + (if (p < 0) NvidiaPicks.size else p)
    }

    fun read(kind: String, json: String): List<String> {
        val data = Json.parseOrNull(json).obj()?.get("data").arr() ?: throw BrainError(Lang.pick("${Source.companyOf(kind)} sent no model list. Try Refresh.", "${Source.companyOf(kind)} n'a pas envoyé de liste de modèles. Actualise."), true)
        val found = ArrayList<Listed>()
        for (item in data) {
            val o = item.obj() ?: continue
            var id = o["id"].str() ?: continue
            if (kind == "google" && id.startsWith("models/")) id = id.substring(7)
            if (id.isBlank() || !chats(kind, id) || found.any { it.id == id }) continue
            if (kind == "nvidia" && id in NvidiaDead) continue
            val arch = o["architecture"].obj()
            val inputs = arch?.get("input_modalities").arr()?.mapNotNull { it.str() } ?: emptyList()
            val outputs = arch?.get("output_modalities").arr()?.mapNotNull { it.str() } ?: emptyList()
            if (kind == "openrouter" && inputs.isNotEmpty() && "text" !in inputs) continue
            if (kind == "openrouter" && outputs.isNotEmpty() && "text" !in outputs) continue
            found.add(Listed(id, o["created"].num()?.toLong() ?: 0L, o["created_at"].str() ?: "", inputs, outputs, o["supported_parameters"].arr()?.mapNotNull { it.str() } ?: emptyList()))
        }
        found.sortWith(Comparator { a, b ->
            var order = when (kind) { "nvidia" -> nvidiaPick(a.id).compareTo(nvidiaPick(b.id)); "openrouter" -> routerPick(a).compareTo(routerPick(b)); "google" -> flashPick(a.id).compareTo(flashPick(b.id)); else -> 0 }
            if (order == 0 && kind == "google") order = geminiVersion(b.id).compareTo(geminiVersion(a.id))
            if (order == 0) order = b.createdAt.compareTo(a.createdAt)
            if (order == 0) order = b.created.compareTo(a.created)
            if (order != 0) order else a.id.compareTo(b.id)
        })
        if (found.isEmpty()) throw BrainError(Lang.pick("${Source.companyOf(kind)} has no chat models on this key.", "${Source.companyOf(kind)} n'a aucun modèle de discussion sur cette clé."), true)
        synchronized(known) {
            known.keys.removeAll { it.startsWith("$kind|") }
            for (m in found) known["$kind|${m.id}"] = m
        }
        return found.map { it.id }
    }

    fun default(kind: String, models: List<String>): String? {
        if (models.isEmpty()) return null
        return when (kind) {
            "anthropic" -> if (ClaudeFastest in models) ClaudeFastest else models[0]
            "deepseek" -> if ("deepseek-flash" in models) "deepseek-flash" else models[0]
            "nvidia" -> NvidiaPicks.firstOrNull { it in models } ?: models[0]
            "google" -> models.minWithOrNull(Comparator { a, b -> val o = flashPick(a).compareTo(flashPick(b)); if (o != 0) o else geminiVersion(b).compareTo(geminiVersion(a)) })
            "openrouter" -> models.minByOrNull { routerPick(listed(kind, it) ?: Listed(it, 0, "", emptyList(), emptyList(), emptyList())) }
            else -> models.firstOrNull { it.startsWith("gpt-") && it.endsWith("-mini") } ?: models[0]
        }
    }

    fun pickOllama(installed: List<String>, saved: String?): String? {
        if (installed.isEmpty()) return null
        if (saved != null && saved in installed) return saved
        for (f in OllamaFavourites) for (n in installed) if (n == f || n.startsWith("$f-", true)) return n
        return installed.firstOrNull { !it.contains("r1", true) } ?: installed[0]
    }

    fun shown(kind: String, id: String): String = if (kind == "nvidia" && id in NvidiaRecommended) "$id · recommandé" else id

    fun next(current: String, models: List<String>?, skip: Collection<String>): String? =
        NvidiaPicks.firstOrNull { it != current && it !in skip && (models == null || it in models) }
}
