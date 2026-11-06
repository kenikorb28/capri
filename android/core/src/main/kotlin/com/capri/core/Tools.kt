package com.capri.core

// A tool call's arguments: whatever JSON the brain wrote, read the forgiving way.
class Args(map: Map<String, Any?>) {
    val values = LinkedHashMap<String, Any?>(map)
    var asked: String? = null
    // Set when they said yes to her question, so the same call runs without asking again.
    var confirmed = false

    fun str(key: String): String? = values[key].str()?.takeIf { it.isNotBlank() }
    fun int(key: String): Int? = values[key].num()?.toInt()
    operator fun get(key: String): String? = str(key)
    operator fun set(key: String, value: Any?) { values[key] = value }

    val name get() = str("name"); val action get() = str("action"); val text get() = str("text"); val site get() = str("site")
    val search get() = str("search"); val song get() = str("song"); val app get() = str("app"); val kind get() = str("kind")
    val task get() = str("task"); val focus get() = str("focus"); val whenText get() = str("when"); val level get() = str("level")
    val phrase get() = str("phrase"); val steps get() = str("steps"); val target get() = str("target"); val query get() = str("query")
    val url get() = str("url")

    fun copy(): Args = Args(values).also { it.asked = asked; it.confirmed = confirmed }
    override fun toString(): String = Json.write(values.filterValues { it != null })

    companion object {
        fun parse(json: String?): Args = Args(Json.parseOrNull(json?.trim()?.ifEmpty { "{}" }).obj() ?: emptyMap())
        fun of(vararg pairs: Pair<String, Any?>): Args = Args(linkedMapOf(*pairs))
    }
}

// What the phone layer does for her: run a tool and say what happened, and say which tools can't work right now and why.
interface Hands {
    fun missing(tool: String): String?
    fun run(tool: String, args: Args): String
}

// How much a call can cost them if it goes wrong. LOW runs, MEDIUM follows its SECURITY toggle, HIGH (sending, paying,
// deleting, anything that can't be undone) always asks first.
enum class Risk { LOW, MEDIUM, HIGH }

class ToolSpec(
    val name: String,
    val description: String,
    val schema: String,
    val toggle: Int,
    val ability: String,
    val group: String = "android",
    val online: Boolean = false,
    val risk: (Args) -> Risk = { if (toggle < 0) Risk.LOW else Risk.MEDIUM }
) {
    companion object {
        fun openAiTools(tools: List<ToolSpec>): String = "[" + tools.joinToString(",") { "{\"type\":\"function\",\"function\":{\"name\":" + Json.quote(it.name) + ",\"description\":" + Json.quote(it.description) + ",\"parameters\":" + it.schema + "}}" } + "]"
        fun claudeTools(tools: List<ToolSpec>): String = "[" + tools.joinToString(",") { "{\"name\":" + Json.quote(it.name) + ",\"description\":" + Json.quote(it.description) + ",\"input_schema\":" + it.schema + "}" } + "]"
        // For brains without tools of their own: each call written on its own line as name{json}.
        fun listing(tools: List<ToolSpec>): String = "\nYour tools. To use one, reply with only its call written as name{json arguments} on its own line, one line per call, like open_app{\"name\":\"WhatsApp\"}:\n" +
            tools.joinToString("\n") { "- " + it.name + " " + it.schema + ": " + it.description }
    }
}

// Her tools on a phone, and anything a plugin adds later with Toolbox.register: the desktop's names and words where the
// job is the same, new ones for what a phone does instead of windows, mouse and keyboard.
object Toolbox {
    fun s(props: String, required: String = ""): String = "{\"type\":\"object\",\"properties\":{" + props + "}" + (if (required.isEmpty()) "" else ",\"required\":[" + required.split(',').joinToString(",") { Json.quote(it.trim()) } + "]") + "}"
    fun p(name: String, desc: String, enum: List<String>? = null, type: String = "string"): String =
        Json.quote(name) + ":{\"type\":\"" + type + "\",\"description\":" + Json.quote(desc) + (if (enum == null) "" else ",\"enum\":[" + enum.joinToString(",") { Json.quote(it) } + "]") + "}"

    private val sendWords = setOf("call", "sms", "email")

    private val builtIn: List<ToolSpec> = listOf(
        ToolSpec("open_app", "Open or launch an app or game installed on their phone, like WhatsApp, YouTube, Spotify, TikTok or Free Fire. Give its name as it shows on their phone. If it's already open and they want something done in it, use use_phone instead.",
            s(p("name", "The app's name"), "name"), Security.Open, "open your apps and games", "apps"),
        ToolSpec("open_website", "Open a website in their browser, or a search on a site like YouTube or Google, for them to read themselves.",
            s(p("site", "The site's name or address, like youtube.com or google") + "," + p("search", "Words to search for on that site, if any")), Security.Open, "open websites in your browser", "web"),
        ToolSpec("web_search", "Search the web yourself and read the results, when they ask something you don't know or that changes: news, weather, prices, scores, how to do something, comparisons. Gives you titles, links and snippets to answer from, with sources.",
            s(p("query", "What to search for, in the words that will find it") + "," + p("site", "Only this site, if it matters, like wikipedia.org")), -1, "look things up on the web and tell you with sources", "web", online = true),
        ToolSpec("read_page", "Read a web page's text yourself, from a link in your search results or one they gave, to answer, summarize or compare.",
            s(p("url", "The page's address"), "url"), -1, "read web pages", "web", online = true),
        ToolSpec("play_music", "Play a song, playlist, album or artist on Spotify, YouTube Music or YouTube. Only for things to listen to. If they leave the pick to you, pick one yourself.",
            s(p("song", "What to play, like a song and its artist or a playlist's name") + "," + p("kind", "What it is", listOf("song", "playlist", "album", "artist")) + "," + p("app", "Where to play it, only if they said", listOf("spotify", "youtube_music", "youtube")), "song"), Security.Media, "play music on Spotify or YouTube", "audio"),
        ToolSpec("media", "Control whatever is playing on their phone, or its volume: pause, resume, skip, go back, volume up, down, set or mute, or read what's playing.",
            s(p("action", "What to do", listOf("play_pause", "pause", "resume", "next", "previous", "volume_up", "volume_down", "volume_set", "mute", "unmute", "now_playing")) + "," + p("level", "For volume_set, 0 to 100"), "action"), Security.Media, "control music, video and volume", "audio"),
        ToolSpec("look_at_screen", "Look at their screen right now: a picture of it, or its words when you can't see pictures. Use it whenever they ask you to look at, check, explain, translate or help with what's on their screen, or say ça, this or ici about something on it. Their screen is only shown to you when they ask.",
            s(p("focus", "What to look at or what they want to know, if they said")), -1, "look at your screen when you ask", "vision"),
        ToolSpec("read_screen", "Read the words on their screen right now, without a picture: messages, a web page, an article, an error. Use it to summarize, explain or answer about the text they're looking at. Password fields are never read.",
            s(p("focus", "What they want to know about the text, if they said")), -1, "read the text on your screen", "vision"),
        ToolSpec("camera", "Take a photo with their camera and look at it: what something is, a sign to translate, a page to read, a chart to explain. Their camera app opens and they take the picture themselves.",
            s(p("focus", "What they want to know about it")), Security.Open, "look through your camera when you take a photo", "vision"),
        ToolSpec("phone_action", "Press one of their phone's own buttons for them: back, home, recent apps, notifications, quick settings, lock the screen, take a screenshot, split the screen, or scroll the page up or down.",
            s(p("action", "Which one", listOf("back", "home", "recents", "notifications", "quick_settings", "lock_screen", "screenshot", "split_screen", "power_menu", "scroll_down", "scroll_up")), "action"), Security.Phone, "press back, home, notifications, scroll or lock the screen", "android"),
        ToolSpec("open_settings", "Open one of their phone's settings pages or panels so they can change it: wifi, bluetooth, internet, mobile_data, airplane, display, brightness, sound, volume, battery, storage, apps, notifications, location, accessibility, language, date, security, nfc, hotspot, developer, default_apps, assistant, or this app's own page.",
            s(p("name", "Which settings page", listOf("wifi", "bluetooth", "internet", "mobile_data", "airplane", "display", "brightness", "sound", "volume", "battery", "storage", "apps", "notifications", "location", "accessibility", "language", "date", "security", "nfc", "hotspot", "developer", "capri", "default_apps", "assistant")), "name"), Security.Panels, "open settings pages and panels", "android"),
        ToolSpec("flashlight", "Turn their phone's flashlight on or off.", s(p("action", "on or off", listOf("on", "off")), "action"), Security.Panels, "turn the flashlight on or off", "android"),
        ToolSpec("do_not_disturb", "Turn Do Not Disturb on or off, or say whether it's on.", s(p("action", "on, off or status", listOf("on", "off", "status")), "action"), Security.Panels, "turn Do Not Disturb on or off", "android"),
        ToolSpec("alarm", "Set an alarm or a timer in their phone's clock app, or show their alarms. Use it for wake me at, alarm at, timer for.",
            s(p("action", "What to do", listOf("set_alarm", "set_timer", "show_alarms")) + "," + p("when", "For set_alarm, the time like 7:30 or 19h") + "," + p("seconds", "For set_timer, how many seconds") + "," + p("text", "A label, if they said"), "action"), Security.Open, "set alarms and timers in the clock app", "tasks"),
        ToolSpec("calendar", "Add an event to their calendar, or open their calendar. Their calendar app opens with it filled in and they save it.",
            s(p("action", "add or open", listOf("add", "open")) + "," + p("text", "The event's title") + "," + p("when", "When it starts, like demain à 14h") + "," + p("minutes", "How long, in minutes", type = "integer"), "action"), Security.Open, "add events to your calendar", "tasks"),
        ToolSpec("remember", "Save a short note about them to your permanent notes (me.md) so you remember it next time, or forget a note. Use it whenever they ask you to remember, note down, save or forget something about them.",
            s(p("text", "The note, about them in the third person, or what to forget") + "," + p("action", "What to do", listOf("remember", "forget")), "text"), -1, "keep notes about you in me.md", "memory"),
        ToolSpec("memory", "Say what you have remembered about them (your notes and memories), or forget all of it when they clearly ask to wipe your memory.",
            s(p("action", "show, or forget_all to wipe notes, memories and chat logs", listOf("show", "forget_all")), "action"), -1, "tell you what she remembers, or wipe it", "memory",
            risk = { if (it.action == "forget_all") Risk.HIGH else Risk.LOW }),
        ToolSpec("project", "Keep a project's memory: what it is, its architecture, the decisions taken and the last task, so you can pick it up later on the phone or the PC. Save adds to it; show reads it back; list names their projects.",
            s(p("action", "What to do", listOf("save", "show", "list", "forget")) + "," + p("name", "The project's name, like Capri Android") + "," + p("text", "For save, what to add: a decision, the architecture, the last task or a note") + "," + p("kind", "For save, which part", listOf("about", "architecture", "decision", "last_task", "note")), "action"), -1, "remember your projects, their decisions and where you left them", "memory",
            risk = { if (it.action == "forget") Risk.HIGH else Risk.LOW }),
        ToolSpec("recap", "Say where you two left off: the last conversation, the project or task in progress and what's next, when they ask on en était où or what were we doing.",
            s(p("name", "A project's name, if they said")), -1, "tell you where you left off", "memory"),
        ToolSpec("plan", "Write a plan of numbered steps for a bigger job they ask help with, show it to them, and keep it as the project's next steps.",
            s(p("name", "What it's for, like a project's name") + "," + p("steps", "The steps, one per line"), "steps"), -1, "plan big jobs step by step", "tasks"),
        ToolSpec("remind", "Save a reminder on their phone, list their reminders, or cancel one. Times use their local clock. Give the reminder in their words; the phone works out when it is due.",
            s(p("text", "What to remind them about, or which reminder to cancel") + "," + p("when", "When, like in 20 minutes, at 5pm, at 17:30, tomorrow at 9, tonight, dans 10 minutes, demain à 9h") + "," + p("action", "What to do; save by default", listOf("save", "list", "cancel"))), -1, "remind you later", "tasks"),
        ToolSpec("routine", "Save steps to run when they say a phrase, list their routines, forget one, or run one. Keep the steps in their words.",
            s(p("phrase", "The phrase that starts the routine") + "," + p("steps", "Everything to do when they say the phrase") + "," + p("action", "What to do; save by default", listOf("save", "list", "forget", "run"))), -1, "save routines that run when you say a phrase", "tasks"),
        ToolSpec("watch", "Watch for something on their phone and tell them when it happens: battery charged or low, plugged or unplugged, Wi-Fi connected or lost, or a notification from someone or an app. List or stop watches too.",
            s(p("action", "What to do", listOf("add", "list", "stop")) + "," + p("kind", "What to watch for", listOf("battery_full", "battery_low", "charging", "unplugged", "wifi_on", "wifi_off", "notification")) + "," + p("text", "For notification, the person or app and words to look for") + "," + p("level", "For battery_low or battery_full, the percent")), -1, "tell you when something happens on your phone", "tasks"),
        ToolSpec("contact_action", "Start a call, a text message, an email, a share or directions. Their phone opens it ready to go and they confirm the last tap themselves. target is a name from their contacts, a number, an email address or a place.",
            s(p("action", "What to start", listOf("call", "sms", "email", "share", "navigate", "contact")) + "," + p("target", "Who or where") + "," + p("text", "The message, subject or text to share, if any"), "action"), Security.Sends, "start calls, texts, emails, shares and directions", "android",
            risk = { if (it.action in sendWords) Risk.MEDIUM else Risk.LOW }),
        ToolSpec("use_phone", "Use their screen for a job inside an app: tapping, typing, scrolling, several steps, like searching inside an app, finding a button, filling a field, or writing a message. Pass the whole job in their exact words. She always asks them before the last tap that sends, posts, pays or deletes.",
            s(p("task", "The whole job in their exact words"), "task"), Security.Screen, "do jobs inside your apps by tapping and typing for you", "android"),
        ToolSpec("files", "Open a file they choose from their phone (a document, code, a log, a PDF, a photo) and read it, save text you wrote into a new file they pick, or delete a file they chose. Opening shows them the file picker.",
            s(p("action", "What to do", listOf("open", "save", "delete")) + "," + p("text", "For save, the file's content") + "," + p("name", "For save, a file name like notes.md") + "," + p("focus", "For open, what they want to do with it"), "action"), Security.Open, "read files you pick, and save or delete them when you ask", "files",
            // Delete picks the file first, then asks with its name ("Tu veux bien supprimer « test.zip » ?"); the yes runs it.
            risk = { when { it.action == "delete" && it.str("uri") != null -> Risk.HIGH; it.action == "save" -> Risk.MEDIUM; else -> Risk.LOW } }),
        ToolSpec("notifications", "Read, group and summarize their recent notifications, or one app's or one kind's (messages, school, important, promotions, apps), when they ask what came in.",
            s(p("action", "list or summary; clear forgets them", listOf("list", "summary", "clear")) + "," + p("app", "Only this app or person, if they said") + "," + p("kind", "Only this kind", listOf("messages", "school", "important", "promotions", "apps"))), Security.Notifications, "read and summarize your notifications", "notifications"),
        ToolSpec("clipboard", "Read what they copied, to translate, summarize, fix, explain or rewrite it, or copy text you wrote for them.",
            s(p("action", "read or copy", listOf("read", "copy")) + "," + p("text", "For copy, the text"), "action"), -1, "use what you copied, or copy text for you", "clipboard"),
        ToolSpec("device_status", "See how their phone is doing right now: battery, charging and temperature, heat, memory, storage, network, Bluetooth and headphones. Use it when they ask about battery, storage, heat, why it's slow, or their connection.",
            s(p("focus", "What to check", listOf("battery", "memory", "storage", "network", "heat", "all"))), -1, "check your battery, heat, storage, memory and connection", "device"),
        ToolSpec("mode", "Switch your mode when they ask: study (explain lessons, summaries, flashcards, quizzes, at a beginner, normal or advanced level), dev (code, logs, errors, Git), gaming (short replies and the game panel) or normal.",
            s(p("name", "Which mode", listOf("normal", "study", "dev", "gaming")) + "," + p("level", "For study, how deep", listOf("beginner", "normal", "advanced")), "name"), -1, "switch to study, developer or gaming mode", "modes"),
        ToolSpec("gaming_panel", "Show or hide your small gaming panel over their game: battery, heat, memory and the clock, with your mic and chat buttons.",
            s(p("action", "show or hide", listOf("show", "hide")), "action"), -1, "show a small gaming panel over games", "modes"),
        ToolSpec("sync", "Capri Link with their PC, where you are the same Capri with the same memory (it syncs by itself): hand this conversation to the PC so they carry on there, say what they were doing with you on the PC, sync right now, send a file to the PC, get the files the PC queued, or check the link.",
            s(p("action", "What to do: send_file sends a file they pick to the PC, pull_files brings the files the PC queued for the phone", listOf("send_chat", "pull_chat", "sync_memory", "send_file", "pull_files", "status"))), -1, "carry on with your Capri on the PC", "sync"),
        ToolSpec("emergency", "Their emergency button, only if they set it up: call their emergency contact, or share where they are with them. Never use it unless they clearly ask for emergency help.",
            s(p("action", "What to do", listOf("call", "share_location", "info")), "action"), -1, "call your emergency contact when you ask", "android",
            risk = { if (it.action == "info") Risk.LOW else Risk.HIGH }),
        ToolSpec("nap", "Go to sleep yourself, or wake up: your own nap, not their phone's.", s(p("action", "What to do", listOf("sleep", "wake")), "action"), -1, "take a nap", "capri"),
        ToolSpec("animate", "Play one of your own animations: your glitch, your grin, a blink, or dance until they tell you to stop.",
            s(p("name", "Which animation", listOf("idle", "glitch", "grin", "blink", "dance", "stop_dancing")), "name"), -1, "dance and play your animations", "capri"),
        ToolSpec("capri_settings", "Read or change your own Capri settings when they ask: voice on or off, quiet mode, Hey Capri on or off, bubble size, bubble opacity, replies length, language, listening after a reply, battery saver profile.",
            s(p("name", "Which setting", listOf("voice", "quiet", "hey_capri", "bubble_size", "bubble_opacity", "replies", "language", "listen_after_reply", "power_profile")) + "," + p("action", "list, on, off or set", listOf("list", "on", "off", "set")) + "," + p("level", "For set, the value: small, medium, large, 20 to 100, short, medium, long, performance, normal, saver, or a language")), -1, "change your own settings", "capri"),
        ToolSpec("speech", "Stop your current spoken reply when they ask you to stop talking or say wait.", s(p("action", "stop", listOf("stop")), "action"), -1, "stop talking", "capri"),
        ToolSpec("voice_chat", "Start or stop listening to them by voice, or say whether you're listening.", s(p("action", "on, off or status", listOf("on", "off", "status")), "action"), -1, "start or stop voice chat", "capri"),
        ToolSpec("check_app", "Check whether an app is installed on their phone, by the name open_app would open.", s(p("name", "The app's name"), "name"), -1, "check whether an app is installed", "apps")
    )

    private val extra = LinkedHashMap<String, ToolSpec>()

    // A plugin's tool: it shows in every brain's tool list and goes to Hands.run like the rest.
    fun register(spec: ToolSpec) = synchronized(extra) { extra[spec.name] = spec }
    fun unregister(name: String) = synchronized(extra) { extra.remove(name) }

    val all: List<ToolSpec> get() = builtIn + synchronized(extra) { extra.values.toList() }
    val plugins: List<ToolSpec> get() = synchronized(extra) { extra.values.toList() }

    fun spec(name: String): ToolSpec? = all.firstOrNull { it.name == name }

    // Calls that only find out where things stand, which are never jobs she did.
    fun checking(tool: String, args: Args?): Boolean = tool == "check_app" || tool == "device_status" || tool == "recap" || tool == "web_search" || tool == "read_page" ||
        (tool == "capri_settings" && args?.action.let { it == null || it == "list" }) || (tool == "voice_chat" && args?.action == "status") || (tool == "media" && args?.action == "now_playing") ||
        (tool == "memory" && args?.action == "show") || (tool == "project" && args?.action in setOf("show", "list")) || (tool == "sync" && args?.action == "status")

    fun valid(tool: String, args: Args?): Boolean {
        val spec = spec(tool) ?: return false
        val required = Regex("\"required\":\\[([^\\]]*)\\]").find(spec.schema)?.groupValues?.get(1)?.split(',')?.map { it.trim().trim('"') }?.filter { it.isNotEmpty() } ?: emptyList()
        return required.all { !(args?.str(it)).isNullOrBlank() }
    }
}

// Her SECURITY window, the desktop's way: a toggle for each kind of thing she does, set by four presets. A toggle that's
// off means she asks first in chat and does it on their yes, never a silent no. HIGH-risk calls always ask.
object Security {
    const val Open = 0; const val Media = 1; const val Panels = 2; const val Phone = 3; const val Lock = 4; const val Screen = 5
    const val Sends = 6; const val Pays = 7; const val Unasked = 8; const val Secrets = 9; const val Notifications = 10; const val Count = 11
    const val Lockdown = 0; const val Guarded = 1; const val Trusted = 2; const val Blackwall = 3; const val Custom = 4

    val Names = arrayOf("Ouvrir des apps, des sites et l'appareil photo", "Musique, médias et volume", "Réglages et panneaux (Wi-Fi, lampe, Ne pas déranger)", "Boutons du téléphone (retour, accueil, défilement)",
        "Verrouiller l'écran", "Utiliser l'écran pour des tâches (toucher, taper)", "Préparer appels, SMS, e-mails et partages", "Payer, acheter, réserver (dans les tâches à l'écran)",
        "Les étapes que ta demande n'a pas mentionnées", "Taper des mots de passe et numéros de carte", "Lire tes notifications")
    val Presets = arrayOf("Lockdown", "Guarded", "Trusted", "Blackwall", "Personnalisé")
    val PresetLines = arrayOf("Tout est coupé : elle demande avant chaque action.", "Apps, musique et réglages ; elle demande pour le reste.",
        "Elle fait ce que tu demandes, et demande avant de payer, de lire tes notifications ou ce que tu n'as pas dit.", "Elle ne demande que pour les actions à risque élevé (envoyer, payer, supprimer), qui demandent toujours.", "Ton propre mélange.")
    private val Masks = intArrayOf(0, 0x7, 0x7F, 0x7FF)
    @Volatile var mask = 0x7F

    fun on(toggle: Int): Boolean = toggle < 0 || (mask and (1 shl toggle)) != 0
    fun set(toggle: Int, value: Boolean) { mask = if (value) mask or (1 shl toggle) else mask and (1 shl toggle).inv() }
    fun pick(preset: Int) { if (preset in Masks.indices) mask = Masks[preset] }
    val preset: Int get() = Masks.indexOfFirst { it == (mask and 0x7FF) }.let { if (it < 0) Custom else it }
    fun saved(): String = (0 until Count).joinToString("") { if (on(it)) "1" else "0" }
    fun load(saved: String?) { mask = if (saved == null || saved.length != Count || saved.trim('0', '1').isNotEmpty()) Masks[Trusted] else saved.withIndex().fold(0) { m, (i, c) -> if (c == '1') m or (1 shl i) else m } }

    // Whether a call needs their yes before it runs.
    fun asks(spec: ToolSpec, args: Args): Boolean {
        if (args.confirmed) return false
        val risk = spec.risk(args)
        if (risk == Risk.HIGH) return true
        return spec.toggle >= 0 && !on(spec.toggle)
    }

    const val Hold = "Not done yet: their SECURITY settings ask first. Ask them: "

    private class Held(val tool: String, val args: Args, val asked: String?, val question: String)
    private var held: Held? = null

    fun isHold(result: String?) = result?.startsWith(Hold) == true

    fun question(tool: String, args: Args): String {
        val n = args.name ?: args.app ?: args.site ?: args.song ?: args.target ?: ""
        val fr = when (tool) {
            "open_app" -> "J'ouvre $n ?"
            "open_website" -> if (args.search != null) "Je cherche « ${args.search} » ?" else "J'ouvre $n ?"
            "play_music" -> "Je mets « $n » ?"
            "media" -> "Je touche à la musique ou au volume ?"
            "phone_action" -> "J'appuie sur « ${args.action} » pour toi ?"
            "open_settings", "flashlight", "do_not_disturb" -> "Je change ce réglage ?"
            "contact_action" -> when (args.action) { "call" -> "J'appelle $n ?"; "sms" -> "Je prépare le SMS pour $n ?"; "email" -> "Je prépare l'e-mail pour $n ?"; else -> "Je prépare ça pour $n ?" }
            "use_phone" -> args.str("confirm_label")?.let { "J'appuie sur « $it » ?" } ?: "Je prends ton écran pour : « ${args.task} » ?"
            "notifications" -> "Je lis tes notifications ?"
            "alarm" -> "Je règle ça dans ton horloge ?"
            "calendar" -> "J'ajoute « ${args.text ?: "cet événement"} » à ton agenda ?"
            "files" -> if (args.action == "delete") "Tu veux bien supprimer « ${args.name ?: "ce fichier"} » ? C'est définitif." else "J'enregistre ce fichier ?"
            "memory" -> "J'efface toute ma mémoire de toi (notes, souvenirs, journaux) ? C'est définitif."
            "project" -> "J'oublie le projet « ${args.name} » ?"
            "emergency" -> if (args.action == "call") "J'appelle ton contact d'urgence ?" else "J'envoie ta position à ton contact d'urgence ?"
            "camera" -> "J'ouvre l'appareil photo ?"
            else -> "Je le fais ?"
        }
        val en = when (tool) {
            "open_app" -> "Open $n?"; "open_website" -> if (args.search != null) "Search for \"${args.search}\"?" else "Open $n?"
            "play_music" -> "Play \"$n\"?"; "notifications" -> "Read your notifications?"; "use_phone" -> args.str("confirm_label")?.let { "Tap \"$it\"?" } ?: "Use your screen for \"${args.task}\"?"
            "memory" -> "Wipe everything I remember about you? It can't be undone."; "files" -> if (args.action == "delete") "Delete \"${args.name ?: "that file"}\" for good?" else "Save that file?"
            "contact_action" -> "Start that ${args.action} for $n?"; "emergency" -> "Contact your emergency contact?"
            else -> "Do it?"
        }
        return Lang.pick("$en Say yes and I will.", "$fr Dis oui et je le fais.")
    }

    // Held for their yes: what her brain gets as the tool's result.
    fun hold(tool: String, args: Args, asked: String?): String {
        val q = question(tool, args)
        synchronized(this) { held = Held(tool, args.copy(), asked, q) }
        return Hold + q
    }

    // Their reply to her question: the held call to run now, or null. Anything else drops it.
    fun answer(text: String): Pair<String, Args>? = synchronized(this) {
        val h = held ?: return null
        held = null
        if (agrees(text)) h.tool to h.args.also { it.asked = h.asked; it.confirmed = true } else null
    }

    fun pending(): Boolean = synchronized(this) { held != null }
    fun declines(text: String): Boolean = rxi("^\\W*(no|nope|nah|don'?t|stop|cancel|non|nan|pas maintenant|laisse tomber|annule|arrête|surtout pas)\\b").containsMatchIn(text.trim())
    fun agrees(text: String): Boolean = rxi("^\\W*(yes|yeah|yep|yup|sure|ok|okay|do it|go ahead|go for it|please|oui|ouais|ouai|vas-y|vas y|go|d'accord|dac|ok vas-y|fais-le|fais le|bien sûr|carrément|exact|c'est ça|confirme|confirmé|envoie)\\b").containsMatchIn(text.trim())

    // A reply that held a call: its question, said as it is, with no second brain call.
    fun reply(results: List<String?>): String? = results.firstOrNull { isHold(it) }?.substring(Hold.length)

    // Words in a step of a job on the screen that mean something sent, paid or deleted, which always asks first.
    val SendWords = rxi("\\b(send|post|publish|share|delete|remove|reply|call|envoyer|envoie|publier|partager|supprimer|effacer|répondre|appeler|tweeter|poster)\\b")
    val PayWords = rxi("\\b(pay|buy|purchase|order|checkout|book|subscribe|transfer|payer|acheter|commander|réserver|s'abonner|virement|transférer|valider la commande|confirmer l'achat)\\b")
}
