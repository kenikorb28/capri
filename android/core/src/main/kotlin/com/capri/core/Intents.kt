package com.capri.core

// Set phrases her hands take straight away, with no brain call, like the desktop's Hands.Heard: "ouvre WhatsApp", "pause",
// "monte le son", "mets du lofi", "rappelle-moi dans 10 minutes de...". Anything less plain goes to her brain.
object Intents {
    private fun r(p: String) = rxi(p)

    private val polite = r("^(?:(?:hey|hé|eh|yo|ok|okay|bon|alors|dis|stp|s'il te plaît|please)[\\s,!]+)*(?:capri[\\s,!]+)?(?:(?:est-ce que tu peux|tu peux|tu pourrais|peux-tu|pourrais-tu|can you|could you|would you|please|je veux que tu|j'aimerais que tu)\\s+)?")
    private val tail = r("[\\s,]*(?:stp|s'il te plaît|s'il te plait|please|merci|thanks|capri|choom)?[\\s.!?]*$")

    // Their words without "hey Capri, can you" in front and "please" behind.
    fun asked(said: String?): String {
        var t = (said ?: "").squash()
        t = polite.replace(t, "")
        t = tail.replace(t, "")
        return t.trim()
    }

    private val sites = r("^(?:[\\w-]+\\.)+(?:com|fr|net|org|io|app|tv|gg|bf|co|me|be|ch|ca|info|dev)(?:/\\S*)?$")
    private val knownSites = mapOf("youtube" to "youtube.com", "google" to "google.com", "wikipedia" to "wikipedia.org", "gmail" to "mail.google.com", "facebook" to "facebook.com", "twitter" to "x.com", "x" to "x.com", "instagram" to "instagram.com", "tiktok" to "tiktok.com", "reddit" to "reddit.com", "netflix" to "netflix.com", "amazon" to "amazon.fr")

    private val open = r("^(?:ouvre|ouvrir|ouvres|lance|lancer|démarre|démarrer|va sur|vas sur|aller sur|mets-moi|open|launch|start|go to|pull up)\\s+(?:moi\\s+)?(?:l'appli(?:cation)?|l'app|l'application|app|application|le site|le site de|the app|the site)?\\s*(.+)$")
    private val search = r("^(?:cherche|recherche|google|googler|trouve-moi|trouve|search for|search|look up|find)\\s+(?:sur\\s+(youtube|google|wikipedia)\\s+)?(.+?)(?:\\s+sur\\s+(youtube|google|wikipedia|internet|le web))?$")
    private val openAndSearch = r("^(?:ouvre|lance|va sur|open)\\s+(youtube|google|wikipedia|spotify)\\s+(?:et\\s+)?(?:cherche|recherche|search(?: for)?|and search(?: for)?)\\s+(.+)$")
    private val play = r("^(?:mets|met|joue|lance|passe|fais-moi écouter|fais moi écouter|j'ai envie d'écouter|je veux écouter|play|put on)\\s+(?:moi\\s+)?(?:(?:de la|du|des|un peu de|une|un|la|le|les|some)\\s+)?(.+?)(?:\\s+(?:sur|on)\\s+(spotify|youtube music|youtube|deezer))?$")
    private val musicWords = r("\\b(musique|music|chanson|song|son|sons|playlist|album|lofi|lo-fi|rap|afro|afrobeat|coupé-décalé|zouk|jazz|rock|pop|beat|beats|morceau|titre|artiste|tube|hits?|soundtrack|ost)\\b|\\bde\\s+\\p{Lu}")

    private val media = listOf(
        r("^(?:pause|mets en pause|mets pause|arrête la musique|stop la musique|stoppe la musique|coupe la musique|pause (?:la )?(?:musique|vidéo|video)|pause (?:it|the music|music)|stop (?:the )?music)$") to "pause",
        r("^(?:reprends|reprend|relance la musique|remets la musique|continue la musique|play|resume|unpause|lecture)$") to "resume",
        r("^(?:suivant|suivante|chanson suivante|musique suivante|morceau suivant|passe(?: à la suivante| la chanson)?|skip|next(?: song| track)?|zappe)$") to "next",
        r("^(?:précédent|précédente|chanson précédente|reviens à la chanson d'avant|previous(?: song| track)?|go back a song)$") to "previous",
        r("^(?:monte le son|monte le volume|plus fort|augmente le (?:son|volume)|turn it up|volume up|louder)$") to "volume_up",
        r("^(?:baisse le son|baisse le volume|moins fort|diminue le (?:son|volume)|turn it down|volume down|quieter)$") to "volume_down",
        r("^(?:coupe le son|mets en silencieux|mute|silence)$") to "mute",
        r("^(?:remets le son|unmute)$") to "unmute",
        r("^(?:qu'est-ce qui (?:joue|passe)|c'est quoi cette (?:chanson|musique)|quelle est cette chanson|what'?s playing|what song is this)$") to "now_playing"
    )
    private val volumeSet = r("^(?:mets |règle |set )?(?:le )?(?:son|volume)\\s*(?:à|a|au|to|at)?\\s*(\\d{1,3})\\s*%?$")

    private val phone = listOf(
        r("^(?:retour|reviens en arrière|reviens|go back|back)$") to "back",
        r("^(?:accueil|écran d'accueil|retour à l'accueil|go home|home|home screen)$") to "home",
        r("^(?:apps récentes|applications récentes|recent apps|recents|multitâche)$") to "recents",
        r("^(?:(?:ouvre |affiche |montre(?:-moi)? )?(?:les |mes )?notifications|notification shade)$") to "notifications",
        r("^(?:(?:ouvre )?(?:les )?réglages rapides|quick settings)$") to "quick_settings",
        r("^(?:verrouille(?: le téléphone| l'écran| mon téléphone)?|lock(?: the)?(?: phone| screen)?)$") to "lock_screen",
        r("^(?:fais une capture d'écran|capture d'écran|prends une capture(?: d'écran)?|screenshot|take a screenshot)$") to "screenshot"
    )
    private val torch = r("^(allume|éteins|eteins|active|désactive|turn on|turn off|switch on|switch off)\\s+(?:la |le |the )?(?:lampe(?: torche)?|torche|flash|flashlight|torch)$|^(?:lampe(?: torche)?|torche|flashlight)\\s+(on|off)$")
    private val settingsAsk = r("^(?:ouvre|va dans|montre(?:-moi)?|affiche|open|show)\\s+(?:les |le |la )?(?:réglages|paramètres|settings)\\s*(?:du |de la |des |de l'|de |d'|for |)?\\s*(.*)$")
    private val settingNames = linkedMapOf("wi-?fi|wifi" to "wifi", "bluetooth" to "bluetooth", "internet|réseau|network" to "internet", "données mobiles|data|mobile data" to "mobile_data",
        "mode avion|avion|airplane" to "airplane", "écran|affichage|display" to "display", "luminosité|brightness" to "brightness", "son|sons|sound|sonnerie" to "sound", "volume" to "volume",
        "batterie|battery" to "battery", "stockage|storage" to "storage", "applications|apps|application" to "apps", "notifications?" to "notifications", "localisation|position|gps|location" to "location",
        "accessibilité|accessibility" to "accessibility", "langue|language" to "language", "date|heure|date et heure" to "date", "sécurité|security" to "security", "nfc" to "nfc",
        "point d'accès|partage de connexion|hotspot" to "hotspot", "développeur|developer" to "developer", "capri" to "capri", "applis par défaut|apps par défaut|default apps" to "default_apps", "assistant" to "assistant")

    private val look = r("^(?:regarde|vois|check|analyse|lis|look at|check out|explique(?:-moi)?|décris(?:-moi)?|traduis(?:-moi)?|résume(?:-moi)?)\\s+(?:ce qu'il y a (?:sur|à) |ce qui est (?:sur|à) |ce que j'ai (?:sur|à) |ce que je vois (?:sur|à) |ce qui s'affiche (?:sur|à) |what'?s on |)(?:mon|l'|ton|my|the)\\s*(?:écran|screen)\\b.*$|^(?:c'est quoi|qu'est-ce que c'est|what is|what'?s) (?:ça|ce truc|ce message|this|that)(?: sur| à)?(?: mon| l')? ?(?:écran|screen)?\\s*\\??$|^(?:tu vois|qu'est-ce que tu vois|what do you see)(?: (?:sur|on) (?:mon|l'|my) (?:écran|screen))?\\s*\\??$")
    // "Regarde ça", "aide-moi avec ça", "dis-moi si ce code fonctionne": about what's in front of them.
    private val lookThis = r("^(?:regarde|vois|analyse|check|explique(?:-moi)?|décris(?:-moi)?|traduis(?:-moi)?|corrige|look at|explain|translate)\\s+(?:ça|ceci|cela|cette page|ce code|cette erreur|ce message|ce texte|cette image|ce truc|ce graphique|cet exercice|this|that|this page|this code|this error)\\b.*$|^(?:aide-moi|aide moi|help me)\\s+(?:avec|à faire|with)\\s+(?:ça|ceci|cette page|ce code|cette erreur|cet exercice|this|that)\\b.*$|^(?:qu'est-ce que je dois faire|je dois faire quoi|what should i do)(?: (?:ici|là|here|sur cet écran))?\\s*\\??$")
    private val scrollAsk = r("^(?:descends|descend|défile|fais défiler|scrolle|scroll)(?:\\s+(?:la page|l'écran|vers le bas|en bas|down))*$|^(?:remonte|monte la page|défile vers le haut|scroll up)(?:\\s+(?:la page|l'écran|vers le haut|en haut))*$")
    private val tapAsk = r("^(?:appuie|appuies|clique|cliques|tape|touche|press|tap|click)\\s+(?:sur\\s+|on\\s+)?(?:le bouton\\s+|la touche\\s+|l'option\\s+|le lien\\s+|the button\\s+|the\\s+)?(.{1,40})$")
    private val readText = r("^(?:lis|lit|lire|résume|resume|read|summari[sz]e)\\s+(?:moi\\s+)?(?:(?:cette|ce|la|le|this|the)\\s+)?(?:page|article|message|messages|conversation|texte|text|discussion)(?:\\s+(?:à|a)\\s+l'écran)?$")
    private val rememberIt = r("^(?:souviens-toi|souviens toi|rappelle-toi|retiens|note|note bien|n'oublie pas|garde en mémoire|remember|note that|don't forget)(?:\\s+(?:que|qu'|that))?\\s+(.+)$")
    private val forgetIt = r("^(?:oublie|efface de ta mémoire|forget)(?:\\s+(?:que|qu'|that|ce que je t'ai dit sur))?\\s+(.+)$")
    private val alarmAt = r("^(?:mets|règle|programme|set)?\\s*(?:un |une |an? )?(?:réveil|alarme|alarm)\\s+(?:à|a|pour|for|at)\\s+(.+)$|^réveille-moi\\s+(?:à|a)\\s+(.+)$|^wake me(?: up)? at\\s+(.+)$")
    private val napNow = r("^(?:va dormir|dors|fais (?:une )?(?:la )?sieste|go to sleep|take a nap)$")
    private val shush = r("^(?:tais-toi|chut|arrête de parler|arrête|attends|attend|attends une seconde|une seconde|stop talking|shut up|silence capri|wait|hold on)$")
    private val callSomeone = r("^(?:appelle|téléphone à|call|ring)\\s+(.+)$")
    private val textSomeone = r("^(?:envoie un (?:sms|message|texto) à|écris à|texte|text|send a text to)\\s+(.+?)(?:\\s*(?::|pour (?:lui )?dire(?: que)?|that|saying)\\s+(.+))?$")
    private val directions = r("^(?:emmène-moi à|emmène moi à|itinéraire (?:vers|pour)|guide-moi (?:vers|jusqu'à)|navigate to|directions to|take me to)\\s+(.+)$")
    private val battery = r("^(?:combien de batterie|il me reste combien de batterie|niveau de batterie|batterie|battery|how much battery)\\b.*$")
    // Capri 2.0: where they left off, what she remembers, notifications, the clipboard, modes, the camera, the PC.
    private val recap = r("^(?:on en (?:était|etait) où|où (?:on en (?:était|etait)|en étions-nous)|on (?:était|etait) où|qu'est-ce qu'on faisait|on faisait quoi|where were we|what were we doing|where did we leave off)\\s*\\??$")
    private val whatYouKnow = r("^(?:qu'est-ce que tu (?:as mémorisé|as retenu|sais|te rappelles) (?:sur|de) moi|que sais-tu (?:sur|de) moi|tu sais quoi (?:sur|de) moi|montre(?:-moi)? ta mémoire|what do you (?:remember|know) about me|show (?:me )?your memory)\\s*\\??$")
    private val wipeAll = r("^(?:efface|supprime|vide) (?:tout ce que tu sais sur moi|toute ta mémoire|ta mémoire|tous tes souvenirs)|^oublie tout(?: (?:sur|de) moi)?$|^(?:wipe|erase|delete) (?:your|all (?:your|of your)) memor(?:y|ies)$|^forget everything(?: about me)?$")
    private val notifs = r("^(?:lis|lit|résume|resume|montre(?:-moi)?|read|summari[sz]e)\\s+(?:moi\\s+)?(?:mes|les|my)\\s+(?:notifications?|notifs?)(?:\\s+(importantes?|urgentes?|de l'école|d'école|scolaires?|de cours|promo(?:tion)?s?|publicitaires?|des messages|de messages|des applis|des apps|important|school|messages|promotions|apps))?(?:\\s+(?:de|from)\\s+(.+))?$|^(?:quelles|j'ai des|j'ai reçu des|any) (?:notifications?|notifs?)(?: (?:de|from) (.+?))?\\s*\\??$")
    private val clip = r("^(?:lis|lit|traduis|résume|explique|corrige|reformule|read|translate|summari[sz]e|explain|fix)(?:-moi)?\\s+(?:ce que j'ai copié|le presse-papiers?|mon presse-papiers?|ce qui est copié|what i copied|my clipboard|the clipboard)$")
    private val modeAsk = r("^(?:(?:passe|mets-toi|active|lance|switch|go)\\s+(?:en|au|le|to|into)?\\s*)?(?:le\\s+)?mode\\s+(étude|etude|study|révision|dev|développeur|developpeur|developer|code|jeu|gaming|game|normal)(?:\\s+(débutant|debutant|beginner|normal|avancé|avance|advanced))?$|^(study|dev|gaming|normal) mode$")
    private val cameraAsk = r("^(?:prends une photo|regarde avec (?:la|ta) (?:caméra|camera)|ouvre la caméra et dis-moi|take a photo|look through the camera)\\b.*$")
    private val toPc = r("^(?:envoie|passe|continue|transfère)\\s+(?:cette |la |notre )?(?:conversation|discussion)?\\s*(?:sur|vers|à) (?:mon |le )?(?:pc|ordi|ordinateur)$|^(?:continue|on continue) sur (?:mon |le )?(?:pc|ordi)$|^send (?:this|the) (?:chat|conversation) to (?:my|the) pc$")
    private val fromPc = r("^(?:qu'est-ce que je faisais|on faisait quoi|reprends ce que je faisais) sur (?:mon |le )?(?:pc|ordi)\\s*\\??$|^what was i doing on (?:my|the) pc\\s*\\??$")
    private val fromPcFiles = r("^(?:récupère|recupere|reçois|prends|télécharge) (?:les |mes )?fichiers (?:du|de mon|depuis le|depuis mon) (?:pc|ordi|ordinateur)$|^(?:get|fetch) (?:the |my )?files from (?:my |the )?pc$")
    private val syncMemory = r("^(?:synchronise|synchro|sync)(?:-toi)? (?:ta mémoire|tes notes|avec (?:mon |le )?pc|memory|your memory)$")
    private val sos = r("^(?:appelle mon contact d'urgence|urgence,? appelle(?: mon contact)?|call my emergency contact)$")
    private val heat = r("^(?:comment va mon téléphone|état du téléphone|pourquoi (?:mon téléphone|il) (?:chauffe|rame|est lent)|how'?s my phone(?: doing)?|why is my phone (?:hot|slow))\\s*\\??$")

    // Phrases a plugin answers itself, like "calcule 12 fois 3", tried after her own.
    private val routes = java.util.concurrent.CopyOnWriteArrayList<(String) -> Call?>()
    fun route(r: (String) -> Call?) { routes.add(r) }

    // A plain request and the tool for it, or null when it needs thought.
    fun heard(said: String, reminders: Reminders?): Call? {
        val text = asked(said)
        if (text.isEmpty() || text.length > 160) return null
        reminders?.route(text)?.let { return it }
        for ((p, action) in media) if (p.matches(text)) return Call("media", Args.of("action" to action))
        volumeSet.matchEntire(text)?.let { return Call("media", Args.of("action" to "volume_set", "level" to it.groupValues[1])) }
        for ((p, action) in phone) if (p.matches(text)) return Call("phone_action", Args.of("action" to action))
        torch.matchEntire(text)?.let {
            val w = (it.groupValues[1] + it.groupValues[2]).lowercase()
            return Call("flashlight", Args.of("action" to if (w.contains("allume") || w == "active" || w.contains(" on") || w == "on") "on" else "off"))
        }
        settingsAsk.matchEntire(text)?.let { m ->
            val rest = m.groupValues[1].trim()
            if (rest.isEmpty()) return Call("open_settings", Args.of("name" to "apps"))
            for ((words, name) in settingNames) if (rxi("^(?:$words)$").matches(rest)) return Call("open_settings", Args.of("name" to name))
        }
        if (napNow.matches(text)) return Call("nap", Args.of("action" to "sleep"))
        if (shush.matches(text)) return Call("speech", Args.of("action" to "stop"))
        if (look.matches(text) || lookThis.matches(text)) return Call("look_at_screen", Args.of("focus" to text))
        scrollAsk.matchEntire(text)?.let { return Call("phone_action", Args.of("action" to if (rxi("remonte|monte|haut|up").containsMatchIn(text)) "scroll_up" else "scroll_down")) }
        if (readText.matches(text)) return Call("read_screen", Args.of("focus" to text))
        if (battery.matches(text)) return Call("device_status", Args.of("focus" to "battery"))
        if (heat.matches(text)) return Call("device_status", Args.of("focus" to if (rxi("chauffe|hot").containsMatchIn(text)) "heat" else "all"))
        if (recap.matches(text)) return Call("recap", Args.of())
        if (whatYouKnow.matches(text)) return Call("memory", Args.of("action" to "show"))
        if (wipeAll.containsMatchIn(text)) return Call("memory", Args.of("action" to "forget_all"))
        notifs.matchEntire(text)?.let { m ->
            val k = m.groupValues[1].lowercase()
            val kind = when { k.isEmpty() -> null; k.startsWith("import") || k.startsWith("urgent") -> "important"; k.contains("école") || k.startsWith("scol") || k.contains("cours") || k == "school" -> "school"; k.contains("promo") || k.startsWith("public") -> "promotions"; k.contains("message") -> "messages"; else -> "apps" }
            return Call("notifications", Args.of("action" to "summary", "kind" to kind, "app" to (m.groupValues[2] + m.groupValues[3]).trim().ifEmpty { null }))
        }
        if (clip.matches(text)) return Call("clipboard", Args.of("action" to "read"))
        modeAsk.matchEntire(text)?.let { m ->
            val w = (m.groupValues[1] + m.groupValues[3]).lowercase()
            val name = when { w.startsWith("ét") || w.startsWith("et") || w.startsWith("st") || w.startsWith("ré") -> "study"; w.startsWith("dev") || w.startsWith("dé") || w == "code" -> "dev"; w.startsWith("j") || w.startsWith("ga") -> "gaming"; else -> "normal" }
            val lv = m.groupValues[2].lowercase().let { when { it.startsWith("d") || it.startsWith("b") -> "beginner"; it.startsWith("av") || it.startsWith("adv") -> "advanced"; it.isEmpty() -> null; else -> "normal" } }
            return Call("mode", Args.of("name" to name, "level" to lv))
        }
        if (cameraAsk.matches(text)) return Call("camera", Args.of("focus" to text))
        if (toPc.matches(text)) return Call("sync", Args.of("action" to "send_chat"))
        if (fromPc.matches(text)) return Call("sync", Args.of("action" to "pull_chat"))
        if (fromPcFiles.matches(text)) return Call("sync", Args.of("action" to "pull_files"))
        if (syncMemory.matches(text)) return Call("sync", Args.of("action" to "sync_memory"))
        if (sos.matches(text)) return Call("emergency", Args.of("action" to "call"))
        tapAsk.matchEntire(text)?.let { m ->
            val label = m.groupValues[1].trim().trimEnd('.', '!')
            if (label.isNotEmpty() && !rxi("^(?:pause|play|lecture|retour|back|accueil|home)$").matches(label) && !rxi("^(?:pas|rien|jamais|plus|à rien|not|nothing)\\b").containsMatchIn(label)) return Call("use_phone", Args.of("task" to text, "target" to label))
        }
        // A plugin's own phrases come before the general "ouvre…", "cherche…" and "mets…" ones.
        for (r in routes) try { r(text)?.let { return it } } catch (_: Exception) { }
        rememberIt.matchEntire(text)?.let { m -> if (!Regex("\\?\\s*$").containsMatchIn(m.groupValues[1])) return Call("remember", Args.of("text" to m.groupValues[1], "action" to "remember")) }
        forgetIt.matchEntire(text)?.let { return Call("remember", Args.of("text" to it.groupValues[1], "action" to "forget")) }
        alarmAt.matchEntire(text)?.let { m -> return Call("alarm", Args.of("action" to "set_alarm", "when" to (m.groupValues[1] + m.groupValues[2] + m.groupValues[3]).trim())) }
        directions.matchEntire(text)?.let { return Call("contact_action", Args.of("action" to "navigate", "target" to it.groupValues[1])) }
        callSomeone.matchEntire(text)?.let { return Call("contact_action", Args.of("action" to "call", "target" to it.groupValues[1])) }
        textSomeone.matchEntire(text)?.let { m -> return Call("contact_action", Args.of("action" to "sms", "target" to m.groupValues[1], "text" to m.groupValues[2].ifEmpty { null })) }
        openAndSearch.matchEntire(text)?.let { m ->
            val where = m.groupValues[1].lowercase()
            return if (where == "spotify") Call("play_music", Args.of("song" to m.groupValues[2], "app" to "spotify")) else Call("open_website", Args.of("site" to where, "search" to m.groupValues[2]))
        }
        search.matchEntire(text)?.let { m ->
            val where = (m.groupValues[1] + m.groupValues[3]).lowercase().let { if (it == "internet" || it == "le web" || it.isEmpty()) "google" else it }
            return Call("open_website", Args.of("site" to where, "search" to m.groupValues[2]))
        }
        play.matchEntire(text)?.let { m ->
            val what = m.groupValues[1].trim()
            val where = m.groupValues[2].lowercase().replace(' ', '_').ifEmpty { null }
            if (where != null || musicWords.containsMatchIn(what)) return Call("play_music", Args.of("song" to what, "app" to where))
        }
        open.matchEntire(text)?.let { m ->
            val target = m.groupValues[1].trim().trimEnd('.', '!')
            if (target.isEmpty() || target.split(' ').size > 4) return null
            if (sites.matches(target)) return Call("open_website", Args.of("site" to target))
            knownSites[target.lowercase()]?.let { if (target.lowercase() in setOf("google", "wikipedia")) return Call("open_website", Args.of("site" to it)) }
            if (rxi("\\b(?:et|and|puis|then)\\b").containsMatchIn(target)) return null
            return Call("open_app", Args.of("name" to target))
        }
        return null
    }

    // Set phrases that need no planning even on a brain that works jobs out itself.
    fun instant(tool: String): Boolean = tool in setOf("media", "nap", "animate", "remember", "speech", "phone_action", "flashlight", "remind", "routine", "device_status",
        "recap", "memory", "mode", "notifications", "clipboard", "sync", "emergency")

    // Calls written out as text by a brain without tools: open_app{"name":"WhatsApp"}, <tool_call>{...}</tool_call>,
    // or a JSON object with name and arguments. Only her own tools' names count.
    fun written(raw: String?): List<Call> {
        val text = raw ?: return emptyList()
        val found = ArrayList<Call>()
        val names = Toolbox.all.map { it.name }.toSet()
        var i = 0
        val call = Regex("([a-z_]{3,30})\\s*(\\{)")
        while (i < text.length) {
            val m = call.find(text, i) ?: break
            val name = m.groupValues[1]
            val start = m.groups[2]!!.range.first
            val end = balanced(text, start)
            if (name in names && end > start) {
                val args = Json.parseOrNull(text.substring(start, end + 1)).obj()
                if (args != null) found.add(Call(name, Args(args)))
                i = end + 1
            } else i = m.range.last + 1
        }
        if (found.isNotEmpty()) return found
        // {"name":"open_app","arguments":{...}} anywhere, as Hermes or Qwen write them, tagged or not.
        var j = 0
        while (j < text.length) {
            val at = text.indexOf('{', j)
            if (at < 0) break
            val end = balanced(text, at)
            if (end < 0) break
            val o = Json.parseOrNull(text.substring(at, end + 1)).obj()
            val name = o?.get("name").str() ?: o?.get("tool").str() ?: o?.get("function").str()
            val args = o?.get("arguments") ?: o?.get("parameters") ?: o?.get("args")
            if (name != null && name in names) found.add(Call(name, if (args is String) Args.parse(args) else Args(args.obj() ?: emptyMap())))
            j = end + 1
        }
        return found
    }

    private fun balanced(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (k in start until text.length) {
            val c = text[k]
            if (inString) {
                if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return k }
            }
        }
        return -1
    }

    // Her brain claiming it did something no tool did this turn: "c'est fait", "reminder set", "I opened it".
    private val promised = rxi("^(?:on it|got it|sure|okay|ok|d'accord|ça marche|c'est parti|je m'en occupe|j'y vais|je lance|j'ouvre|je mets|i'll|i will|let me|opening|playing)\\b")
    private val claimed = rxi("\\b(?:c'est fait|voilà,? c'est|j'ai (?:ouvert|lancé|mis|réglé|programmé|envoyé|noté|enregistré|activé|désactivé|coupé)|rappel (?:réglé|programmé|enregistré)|i (?:opened|launched|played|set|sent|saved|turned)|reminder (?:is )?set|done)\\b")

    fun promised(said: String): Boolean = promised.containsMatchIn(said) && said.length < 80
    fun claims(said: String): Boolean = claimed.containsMatchIn(said)

    val Unkept = "You said you'd do it, but no tool ran, so nothing has happened yet. Call the tool now, or tell them plainly that you can't."
    val Unpromised get() = Lang.pick("I haven't done that yet. Say it another way and I'll try.", "Je ne l'ai pas encore fait. Redis-le autrement et j'essaie.")
}
