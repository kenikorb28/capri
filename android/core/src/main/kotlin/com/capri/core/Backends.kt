package com.capri.core

class Msg(val role: String, val content: String, val images: List<String> = emptyList())

class Call(val name: String, val args: Args)

class Reply(val text: String, val calls: List<Call>, val stop: String?, val thinking: String?)

// One brain request on whichever service holds her brain: Ollama's API, any OpenAI-style API (local servers, NVIDIA,
// Gemini, OpenRouter, OpenAI, DeepSeek) or Claude's Messages API. Mirrors Think, Complete and AskClaude in app/Brain.cs.
class Backends(val http: Http) {
    @Volatile var cloudWaitMs = 45000
    @Volatile var localWaitMs = 180000
    @Volatile var window = 4096
    private val efforts = HashMap<String, String>()
    @Volatile private var anthropicVersion = Source.AnthropicVersion
    // Tests point her cloud brains at a fake server on this machine.
    @Volatile var baseOverride: String? = null
    // CONSOMMATION: told after every reply what it cost, as the provider reported it (or an estimate when it didn't).
    @Volatile var onUsage: ((Source, Tally) -> Unit)? = null

    private fun cloudUrl(kind: String, path: String): String = baseOverride?.let { it.trimEnd('/') + (if (kind == "anthropic" || kind == "openai" || kind == "nvidia") "/v1" else "") + path } ?: Source.address(kind, path)

    fun chat(source: Source, system: String, steady: Int, messages: List<Msg>, tools: List<ToolSpec>?, limit: Int, ticket: Int, brief: Boolean = false, job: Boolean = false): Reply {
        return when {
            source.kind == "ollama" -> ollama(source, system, messages, tools, limit, ticket)
            source.claude -> claude(source, system, steady, messages, tools, limit, ticket, brief)
            else -> openAi(source, system, messages, tools, limit, ticket, job)
        }
    }

    private fun headers(source: Source): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        val key = source.key
        if (!key.isNullOrEmpty()) {
            if (source.claude) { h["x-api-key"] = key; h["anthropic-version"] = anthropicVersion }
            else h["Authorization"] = "Bearer $key"
        }
        if (source.kind == "openrouter") h["X-Title"] = "Capri"
        return h
    }

    private fun send(source: Source, url: String, body: Any?, timeout: Int, ticket: Int, extra: Map<String, String> = emptyMap()): HttpReply {
        if (!source.key.isNullOrEmpty() && !Http.safeForKey(url)) throw BrainError(Lang.pick("Your key only goes over https or to your own network.", "Ta clé ne part qu'en https ou vers ton propre réseau."), true)
        val sent = Json.write(body).toByteArray(Charsets.UTF_8)
        val reply = http.request("POST", url, headers(source) + extra, sent, timeout, ticket)
        if (reply.status in 300..399) throw BrainError(Lang.pick("Their API moved. Your key wasn't sent to the new address.", "Leur API a déménagé. Ta clé n'a pas été envoyée à la nouvelle adresse."), true)
        if (reply.status >= 400) throw problem(source, reply)
        onUsage?.let { told -> try { told(source, Tally.read(reply.text, sent.size)) } catch (e: Exception) { } }
        return reply
    }

    private fun problem(source: Source, reply: HttpReply): BrainError {
        val said = Problems.problemText(reply.text) ?: ("HTTP " + reply.status)
        val toolless = (reply.status == 400 || reply.status == 404 || reply.status == 422) && Problems.toolless(said)
        val meaning = if (!source.local && !toolless) Problems.meaning(reply.status, reply.text, source.kind == "google", source.kind == "openai") else null
        val retry = if (reply.status == 429) Problems.retryAt(reply.header("Retry-After")) else 0L
        return if (meaning != null) BrainError(meaning, true, reply.status, said, retry) else BrainError(said, false, reply.status, said, retry)
    }

    fun effort(model: String, openAi: Boolean, first: String? = null): String? {
        synchronized(efforts) { efforts[model]?.let { return it.ifEmpty { null } } }
        return first ?: if (openAi && Regex("^(gpt-5|o\\d)").containsMatchIn(model)) "low" else null
    }

    // The reasoning_effort an error says a model takes instead, "" when it takes none, null when the error is about something else.
    fun effortFrom(error: String?, sent: String?): String? {
        if (!rxi("reasoning[_ ]effort").containsMatchIn(error ?: "")) return null
        for (level in listOf("none", "minimal", "low", "medium", "high", "xhigh")) if (level != sent && Regex("['\"`]$level['\"`]", RegexOption.IGNORE_CASE).containsMatchIn(error!!)) return level
        return if (sent != null && rxi("unrecognized|unknown (parameter|argument|field)|not (supported|allowed|permitted)|unsupported (parameter|argument)|extra (inputs?|fields?)").containsMatchIn(error!!)) "" else null
    }

    private fun openAiMessages(system: String, messages: List<Msg>): List<Any?> {
        val out = ArrayList<Any?>()
        out.add(mapOf("role" to "system", "content" to system))
        for (m in messages) {
            if (m.images.isEmpty()) out.add(mapOf("role" to m.role, "content" to m.content))
            else {
                val parts = ArrayList<Any?>()
                parts.add(mapOf("type" to "text", "text" to m.content))
                for (img in m.images) parts.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$img")))
                out.add(mapOf("role" to m.role, "content" to parts))
            }
        }
        return out
    }

    private fun openAi(source: Source, system: String, messages: List<Msg>, tools: List<ToolSpec>?, limit: Int, ticket: Int, job: Boolean): Reply {
        val openAi = source.kind == "openai"
        val google = source.kind == "google"
        val screen = messages.any { it.images.isNotEmpty() } && source.kind == "nvidia"
        val superVl = screen && !job && source.model == "nvidia/nemotron-3.5-super-vl-120b-a12b"
        val body = LinkedHashMap<String, Any?>()
        body["model"] = source.model
        body["messages"] = openAiMessages(system, messages)
        val room = maxOf(limit, 256)
        if (openAi) body["max_completion_tokens"] = maxOf(room, 2000) else body["max_tokens"] = if (google) maxOf(2048, room) else if (source.local) room else maxOf(room, 2000)
        var effort: String? = if (source.local) null else effort(source.model, openAi, if (superVl) "none" else if (google) "low" else null)
        if (effort != null) body["reasoning_effort"] = effort
        if (source.kind == "deepseek") body["thinking"] = mapOf("type" to "disabled")
        if (!tools.isNullOrEmpty()) body["tools"] = RawJson(ToolSpec.openAiTools(tools))
        val url = if (source.local) source.host!!.trimEnd('/') + "/v1/chat/completions" else cloudUrl(source.kind, "/chat/completions")
        val wait = if (source.local || screen) maxOf(localWaitMs, cloudWaitMs) else cloudWaitMs
        val reply = try { send(source, url, body, wait, ticket) } catch (e: BrainError) {
            val taken = if (e.told) null else effortFrom(e.message, effort)
            if (taken == null) throw e
            synchronized(efforts) { efforts[source.model] = taken }
            if (taken.isEmpty()) body.remove("reasoning_effort") else body["reasoning_effort"] = taken
            effort = taken
            send(source, url, body, wait, ticket)
        }
        return readOpenAi(reply.text)
    }

    fun readOpenAi(text: String): Reply {
        val root = Json.parseOrNull(text).obj() ?: throw BrainError(Problems.Wordless, true)
        root["error"].obj()?.let { throw BrainError(it["message"].str() ?: Problems.Wordless) }
        val choice = root["choices"].arr()?.firstOrNull().obj() ?: throw BrainError(Problems.Wordless, true)
        val message = choice["message"].obj() ?: throw BrainError(Problems.Wordless, true)
        val calls = ArrayList<Call>()
        for (c in message["tool_calls"].arr() ?: emptyList()) {
            val f = c.obj()?.get("function").obj() ?: continue
            val name = f["name"].str() ?: continue
            val rawArgs = f["arguments"]
            val args = when (rawArgs) { is String -> Args.parse(rawArgs); is Map<*, *> -> Args(rawArgs.obj() ?: emptyMap()); else -> Args(emptyMap()) }
            calls.add(Call(name, args))
        }
        val content = message["content"].str() ?: ""
        val thinking = message["reasoning_content"].str() ?: message["reasoning"].str() ?: message["thinking"].str()
        val stop = choice["finish_reason"].str()
        if (calls.isEmpty() && Cleanup.garbled(content)) throw BrainError(Problems.Gibberish, true)
        if (calls.isEmpty() && Cleanup.clean(content).isEmpty()) {
            if (stop == "length") throw BrainError(Lang.pick("That model ran out of room before answering. Pick another model.", "Ce modèle a manqué de place avant de répondre. Choisis un autre modèle."), true)
            if (!thinking.isNullOrBlank()) throw BrainError(Problems.Answerless, true)
            throw BrainError(Problems.Wordless, true)
        }
        return Reply(content, calls, stop, thinking)
    }

    private fun ollama(source: Source, system: String, messages: List<Msg>, tools: List<ToolSpec>?, limit: Int, ticket: Int): Reply {
        val list = ArrayList<Any?>()
        list.add(mapOf("role" to "system", "content" to system))
        for (m in messages) {
            val o = LinkedHashMap<String, Any?>()
            o["role"] = m.role
            o["content"] = m.content
            if (m.images.isNotEmpty()) o["images"] = m.images
            list.add(o)
        }
        val body = LinkedHashMap<String, Any?>()
        body["model"] = source.model
        body["messages"] = list
        body["stream"] = false
        body["think"] = false
        body["keep_alive"] = "15m"
        body["options"] = mapOf("num_predict" to maxOf(limit, 90), "temperature" to 0.85, "num_ctx" to window)
        if (!tools.isNullOrEmpty()) body["tools"] = RawJson(ToolSpec.openAiTools(tools))
        val host = (source.host ?: Source.Ollama).trimEnd('/')
        val reply = send(source, "$host/api/chat", body, localWaitMs, ticket)
        val root = Json.parseOrNull(reply.text).obj() ?: throw BrainError(Problems.Wordless, true)
        root["error"].str()?.let { throw BrainError(it) }
        val message = root["message"].obj() ?: throw BrainError(Problems.Wordless, true)
        val calls = ArrayList<Call>()
        for (c in message["tool_calls"].arr() ?: emptyList()) {
            val f = c.obj()?.get("function").obj() ?: continue
            val name = f["name"].str() ?: continue
            val a = f["arguments"]
            calls.add(Call(name, if (a is String) Args.parse(a) else Args(a.obj() ?: emptyMap())))
        }
        val content = message["content"].str() ?: ""
        if (calls.isEmpty() && Cleanup.garbled(content)) throw BrainError(Problems.Gibberish, true)
        return Reply(content, calls, root["done_reason"].str(), message["thinking"].str())
    }

    // Claude's cache keeps the steady start of her prompt cheap, so it's split where things start changing.
    private fun claude(source: Source, system: String, steady: Int, messages: List<Msg>, tools: List<ToolSpec>?, limit: Int, ticket: Int, brief: Boolean): Reply {
        val model = source.model
        val fallback = Regex("^claude-(opus-5|sonnet-5-5|fable-5-1)").containsMatchIn(model)
        val body = LinkedHashMap<String, Any?>()
        body["model"] = model
        body["max_tokens"] = if (brief) maxOf(limit, 256) else maxOf(limit, 4096)
        val blocks = ArrayList<Any?>()
        val cut = steady.coerceIn(0, system.length)
        if (cut > 0) blocks.add(mapOf("type" to "text", "text" to system.substring(0, cut), "cache_control" to mapOf("type" to "ephemeral")))
        if (system.substring(cut).isNotBlank()) blocks.add(mapOf("type" to "text", "text" to system.substring(cut)))
        if (blocks.isNotEmpty()) body["system"] = blocks
        val list = ArrayList<Any?>()
        for (m in messages) {
            if (m.images.isEmpty()) list.add(mapOf("role" to m.role, "content" to m.content))
            else {
                val parts = ArrayList<Any?>()
                for (img in m.images) parts.add(mapOf("type" to "image", "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to img)))
                parts.add(mapOf("type" to "text", "text" to m.content))
                list.add(mapOf("role" to m.role, "content" to parts))
            }
        }
        body["messages"] = list
        if (Regex("^claude-((opus|sonnet|haiku|fable|mythos)-5|opus-4-[5-8]|sonnet-4-6)").containsMatchIn(model)) body["output_config"] = mapOf("effort" to "low")
        if (fallback) body["fallbacks"] = "default"
        if (brief) body["thinking"] = mapOf("type" to "disabled")
        if (!tools.isNullOrEmpty()) body["tools"] = RawJson(ToolSpec.claudeTools(tools))
        val extra = if (fallback) mapOf("anthropic-beta" to "server-side-fallback-2026-07-01") else emptyMap()
        val reply = try { send(source, cloudUrl("anthropic", "/messages"), body, cloudWaitMs, ticket, extra) } catch (e: BrainError) {
            // An account or proxy that doesn't know her newer API version, or the extras that came with it, gets the plain request.
            if (e.status != 400 || !rxi("anthropic-version|output_config|fallbacks|anthropic-beta|unknown field|extra inputs").containsMatchIn(e.detail ?: "")) throw e
            anthropicVersion = Source.AnthropicFallbackVersion
            body.remove("output_config"); body.remove("fallbacks")
            send(source, cloudUrl("anthropic", "/messages"), body, cloudWaitMs, ticket)
        }
        val root = Json.parseOrNull(reply.text).obj() ?: throw BrainError(Problems.Wordless, true)
        val stop = root["stop_reason"].str()
        if (stop == "refusal") return Reply(Problems.Declined, emptyList(), stop, null)
        val said = StringBuilder()
        val calls = ArrayList<Call>()
        val content = root["content"].arr() ?: emptyList()
        for ((i, b) in content.withIndex()) {
            val block = b.obj() ?: continue
            when (block["type"].str()) {
                "text" -> said.append(block["text"].str() ?: "").append(' ')
                "tool_use" -> if (!(i == content.size - 1 && stop == "max_tokens")) calls.add(Call(block["name"].str() ?: continue, Args(block["input"].obj() ?: emptyMap())))
            }
        }
        val text = said.toString().trim()
        if (text.isEmpty() && calls.isEmpty()) throw BrainError(Problems.Wordless, true)
        return Reply(text, calls, stop, null)
    }

    // The models a brain offers: Ollama's pulled models, a server's /v1/models, or a key's list.
    fun models(source: Source, freeOnly: Boolean = false): List<String> {
        if (source.kind == "ollama") {
            val host = (source.host ?: Source.Ollama).trimEnd('/')
            val reply = http.request("GET", "$host/api/tags", emptyMap(), null, 6000)
            if (reply.status >= 400) throw BrainError("Ollama: HTTP " + reply.status)
            return Json.parseOrNull(reply.text).obj()?.get("models").arr()?.mapNotNull { it.obj()?.get("name").str() }?.filter { !it.contains("embed", true) } ?: emptyList()
        }
        if (source.kind == "local") {
            val reply = http.request("GET", source.host!!.trimEnd('/') + "/v1/models", headers(source), null, 6000)
            if (reply.status >= 400) throw problem(source, reply)
            return Json.parseOrNull(reply.text).obj()?.get("data").arr()?.mapNotNull { it.obj()?.get("id").str() }?.filter { !it.contains("embed", true) } ?: emptyList()
        }
        val url = cloudUrl(source.kind, "/models") + if (source.claude) "?limit=100" else ""
        if (!Http.safeForKey(url)) throw BrainError("https only", true)
        val reply = http.request("GET", url, headers(source), null, 30000)
        if (reply.status in 300..399) throw BrainError(Lang.pick("Their API moved. Your key wasn't sent to the new address.", "Leur API a déménagé. Ta clé n'a pas été envoyée."), true)
        if (reply.status >= 400) {
            val p = problem(source, reply)
            if (p.message == Problems.Gone) throw BrainError(Lang.pick("${source.company} couldn't find its model list. Try Refresh.", "${source.company} ne trouve pas sa liste de modèles. Actualise."), true)
            throw p
        }
        val found = Models.read(source.kind, reply.text).toMutableList()
        if (freeOnly && source.kind == "openrouter") {
            found.removeAll { !it.endsWith(":free") }
            if (found.isEmpty()) throw BrainError(Lang.pick("OpenRouter has no free chat models on this key.", "OpenRouter n'a aucun modèle gratuit sur cette clé."), true)
        }
        return found
    }

    fun sighted(source: Source): Boolean = when (source.kind) {
        "openrouter" -> Models.listed(source.kind, source.model)?.images ?: true
        "nvidia" -> rxi("vl|vision|omni|llama-3\\.2-(11|90)b|gemma-[34]|phi-4-multimodal|kimi").containsMatchIn(source.model)
        "deepseek" -> source.model == "deepseek-flash"
        else -> true
    }

    fun takesTools(source: Source): Boolean = source.kind != "openrouter" || (Models.listed(source.kind, source.model)?.tools ?: true)
}
