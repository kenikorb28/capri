package com.capri.assistant.engine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.capri.assistant.BuildConfig
import com.capri.assistant.CapriApp
import com.capri.assistant.assist.CapriSession
import com.capri.assistant.data.Prefs
import com.capri.assistant.overlay.OverlayService
import com.capri.assistant.sync.LinkJob
import com.capri.assistant.sync.SyncHub
import com.capri.assistant.system.Alarms
import com.capri.assistant.system.DeviceInfo
import com.capri.assistant.system.Watches
import com.capri.assistant.ui.ListenActivity
import com.capri.assistant.voice.Earcon
import com.capri.assistant.voice.Ears
import com.capri.assistant.voice.Mouth
import com.capri.assistant.voice.WakeListener
import com.capri.core.Answer
import com.capri.core.Backends
import com.capri.core.Brain
import com.capri.core.Http
import com.capri.core.HttpCancelled
import com.capri.core.HttpTimeout
import com.capri.core.HttpUnreachable
import com.capri.core.Lang
import com.capri.core.Memory
import com.capri.core.Models
import com.capri.core.Notice
import com.capri.core.Persona
import com.capri.core.Problems
import com.capri.core.Redact
import com.capri.core.Reminders
import com.capri.core.Security
import com.capri.core.Source
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class Mood { IDLE, LISTENING, THINKING, SPEAKING, WORKING, ERROR, ASLEEP }

class Line(val who: Int, val text: String, val at: Long = System.currentTimeMillis()) {
    companion object { const val YOU = 0; const val CAPRI = 1; const val SYS = 2 }
}

// What the assistant session or a capture saw: the screen's words and/or a JPEG, kept a minute for her tools.
class ScreenShot(val text: String?, val jpeg: String?, val app: String?, val at: Long = System.currentTimeMillis())

interface CapriListener {
    fun onMood(mood: Mood, detail: String) {}
    fun onLine(line: Line) {}
    fun onLevel(level: Float) {}
    fun onBubble(command: String, value: String?) {}
}

// Notifications she was given while notification access is on: in memory only, the last day, never sent anywhere unless
// they ask her about them (and then only to the brain they picked, with one-time codes hidden).
class NoticeBox {
    private val list = ArrayList<Notice>()
    fun add(n: Notice) = synchronized(list) {
        list.removeAll { it.pkg == n.pkg && it.title == n.title && it.text == n.text }
        list.add(n)
        val day = System.currentTimeMillis() - 86400_000L
        list.removeAll { it.at < day }
        while (list.size > 150) list.removeAt(0)
    }
    fun all(): List<Notice> = synchronized(list) { ArrayList(list) }
    fun clear() = synchronized(list) { list.clear() }
    val size: Int get() = synchronized(list) { list.size }
}

// One Capri for the whole app: her brain, memory, voice and hands, shared by the bubble, the chat screen, the assistant
// session and the tile, so a conversation carries on whichever window shows it.
class Capri(val app: CapriApp) {
    val prefs = Prefs(app)
    val folder = File(app.filesDir, "capri").apply { mkdirs() }
    val http = Http("Capri-Android/" + BuildConfig.VERSION_NAME)
    val backends = Backends(http)
    val memory = Memory(folder)
    val reminders = Reminders(folder)
    val brain = Brain(folder, backends, memory, reminders)
    val main = Handler(Looper.getMainLooper())
    private val work: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "capri-brain") }
    val io: ExecutorService = Executors.newCachedThreadPool()
    val hands = PhoneHands(this)
    val mouth = Mouth(this)
    val ears = Ears(this)
    val wake = WakeListener(this)
    val notices = NoticeBox()
    val watches = Watches(this)
    val sync = SyncHub(this)
    val lines = ArrayList<Line>()
    private val listeners = CopyOnWriteArrayList<CapriListener>()
    @Volatile var mood = Mood.IDLE
        private set
    @Volatile var moodDetail = ""
        private set
    @Volatile var voiceChat = false
    @Volatile var assist: ScreenShot? = null
    @Volatile var busy = false
        private set
    // How many of her own screens are in front right now: Android lets the microphone start only then, from the bubble's
    // service once it holds the microphone, or while she is the assistant on screen.
    @Volatile var front = 0
        private set
    // In HYBRIDE, when their PC stopped answering: the cloud carries on for two minutes before she tries the PC again.
    @Volatile var pcAwayUntil = 0L
    private var follow: Runnable? = null
    private var idleBack: Runnable? = null
    private val errors = File(folder, "errors.txt")

    fun start() {
        Persona.personalityFile = File(folder, "personality.md")
        brain.hands = hands
        brain.onSwitch = { model -> log("nvidia", "switched to $model for this ask") }
        brain.situation = { DeviceInfo.situation(app, this) }
        reminders.schedule = { Alarms.schedule(app, it) }
        reminders.unschedule = { Alarms.cancel(app, it) }
        // Capri Link: whatever she writes down reaches their PC a few seconds later.
        memory.changed = { sync.soon() }
        apply()
        watches.load()
        io.execute {
            brain.load()
            memory.warmUp()
            val restored = brain.historyCopy().map { Line(if (it.role == "user") Line.YOU else Line.CAPRI, it.content) }
            main.post { if (lines.isEmpty()) { lines.addAll(restored); for (l in listeners) l.onLine(Line(Line.SYS, "")) } }
            if (sync.paired()) { LinkJob.schedule(app); sync.periodic() }
        }
    }

    // Settings into her brain, voice and SECURITY, whenever they change.
    fun apply() {
        val p = prefs
        brain.language = p.language
        Lang.french = p.language == "French"
        brain.nickname = p.nickname
        brain.wordiness = p.wordiness
        brain.replyLength = p.replyLength
        brain.followUps = p.followUps
        brain.ownSteps = p.ownSteps
        brain.stayOnModel = p.stayOnModel
        brain.cues = p.voice == "fish" && p.cues
        brain.mode = p.mode
        brain.studyLevel = p.studyLevel
        memory.recall = intArrayOf(8, 16, 32)[p.recall.coerceIn(0, 2)]
        memory.keeping = p.keeping
        memory.digging = p.digging
        memory.learning = p.autoLearn
        backends.window = if (memory.recall > 8) 8192 else 4096
        Security.load(p.security)
        brain.source = source()
        mouth.configure()
        wake.configure()
        sync.configure()
    }

    // ---- which brain: LOCAL, HYBRIDE or CLOUD

    fun serverSource(): Source? {
        val host = Source.serverAddress(prefs.server) ?: return null
        val model = prefs.serverModel.ifEmpty { return null }
        val ollama = host.endsWith(":11434") || prefs.server.contains("ollama", true)
        return Source(if (ollama) "ollama" else "local", host, null, model)
    }

    fun cloudSource(): Source? {
        val key = (if (prefs.tier == 1) prefs.mediumKey else prefs.maxKey) ?: return null
        val kind = Source.kindOf(key) ?: return null
        val model = (if (prefs.tier == 1) prefs.mediumModel else prefs.maxModel).ifEmpty { defaultModel(kind) ?: return null }
        return Source(kind, null, key, model)
    }

    fun source(): Source? = when (prefs.brainMode) {
        "local" -> serverSource()
        "hybrid" -> if (System.currentTimeMillis() < pcAwayUntil) cloudSource() ?: serverSource() else serverSource() ?: cloudSource()
        else -> cloudSource()
    }

    fun defaultModel(kind: String): String? = when (kind) {
        "nvidia" -> Models.NvidiaPicks[0]
        "anthropic" -> Models.ClaudeFastest
        "deepseek" -> "deepseek-flash"
        else -> null
    }

    // What the brain chip says, honestly: offline only when nothing at all is connected.
    fun brainLabel(): String {
        val s = brain.source ?: return Lang.pick("OFFLINE · plain commands only", "HORS LIGNE · commandes simples seulement")
        val where = if (s.host != null) Lang.pick("your PC", "ton PC") else when (s.kind) { "nvidia" -> "NVIDIA"; "google" -> "Gemini"; "openrouter" -> "OpenRouter"; "anthropic" -> "Claude"; "deepseek" -> "DeepSeek"; "openai" -> "OpenAI"; else -> s.kind }
        val mode = when (prefs.brainMode) { "local" -> "LOCAL"; "hybrid" -> Lang.pick("HYBRID", "HYBRIDE"); else -> "CLOUD" }
        return "$mode · ${s.model} · $where"
    }

    fun addListener(l: CapriListener) { listeners.addIfAbsent(l) }
    fun removeListener(l: CapriListener) { listeners.remove(l) }

    fun line(who: Int, text: String) {
        if (text.isBlank()) return
        val l = Line(who, text)
        lines.add(l)
        while (lines.size > 80) lines.removeAt(0)
        for (x in listeners) x.onLine(l)
    }

    fun setMood(m: Mood, detail: String = "") {
        mood = m
        moodDetail = detail
        idleBack?.let { main.removeCallbacks(it) }
        for (x in listeners) x.onMood(m, detail)
        if (m == Mood.ERROR) idleBack = Runnable { if (mood == Mood.ERROR) setMood(Mood.IDLE) }.also { main.postDelayed(it, 5000) }
    }

    fun level(v: Float) { for (x in listeners) x.onLevel(v) }

    fun bubble(command: String, value: String? = null) = main.post { for (x in listeners) x.onBubble(command, value) }

    fun inFront(shown: Boolean) { front = maxOf(0, front + if (shown) 1 else -1) }

    // The microphone can start here and now: Android allows it from a screen in front, from a foreground service started
    // with the microphone type while she was in front, or for the assistant session.
    fun micHere(): Boolean = hasMic() && (front > 0 || OverlayService.micReady || CapriSession.showing)

    // ---- a turn: typed, heard, shared or from the assistant. Her brain runs off the main thread, one turn at a time.

    fun say(text: String, voice: Boolean = false, images: List<String> = emptyList(), context: String? = null, echo: String? = null) {
        val asked = text.trim()
        if (asked.isEmpty() && images.isEmpty()) return
        cancelFollow()
        mouth.stop()
        line(Line.YOU, echo ?: asked)
        setMood(Mood.THINKING)
        busy = true
        if (prefs.brainMode == "hybrid") brain.source = source()
        work.execute {
            // A cloud brain with no internet: plain commands still run on the phone, and she says the rest needs the network.
            val chosen = brain.source
            val noNet = chosen != null && chosen.host == null && !DeviceInfo.online(app)
            if (noNet) brain.source = null
            try {
                // Capri Link: if the last sync is old, a short one first, so she knows what they just told her on the PC.
                sync.quick()
                val before = hands.runs
                val answer = try {
                    brain.ask(asked, images, context)
                } catch (e: com.capri.core.BrainError) {
                    if (noNet && e.message == Problems.Offline) throw com.capri.core.BrainError(Lang.pick("No internet right now, so I can only do plain things on the phone (open an app, the flashlight, the volume, a reminder…). The rest comes back with the network.",
                        "Pas de connexion internet : je ne peux faire que les commandes simples sur le téléphone (ouvrir une app, la lampe, le volume, un rappel…). Le reste revient avec le réseau."), true)
                    throw e
                } catch (e: HttpCancelled) {
                    throw e
                } catch (e: Exception) {
                    // HYBRIDE: their PC didn't answer before any tool ran, so the same ask goes to the cloud once.
                    val pcDown = e is HttpUnreachable || e is HttpTimeout
                    val cloud = if (pcDown && prefs.brainMode == "hybrid" && brain.source?.host != null && hands.runs == before) cloudSource() else null
                    if (cloud == null) throw e
                    pcAwayUntil = System.currentTimeMillis() + 120_000L
                    brain.source = cloud
                    main.post { line(Line.SYS, Lang.pick("Your PC isn't answering, so I'm on the cloud for now.", "Ton PC ne répond pas : je passe sur le cloud pour l'instant.")) }
                    brain.ask(asked, images, context)
                }
                main.post { deliver(answer, voice) }
                // And what was said here reaches the PC a few seconds later.
                sync.soon(6000)
                brain.foldIfDue()
            } catch (e: HttpCancelled) {
                main.post { setMood(Mood.IDLE) }
            } catch (e: Throwable) {
                log("ask", e.javaClass.simpleName + ": " + (e.message ?: ""))
                val said = Problems.explain(e, brain.source)
                main.post {
                    if (said == null) setMood(Mood.IDLE)
                    else { line(Line.CAPRI, said); setMood(Mood.ERROR, said); if (voice) speak(said, voice = false) }
                }
            } finally {
                busy = false
                if (noNet && brain.source == null) brain.source = chosen
            }
        }
    }

    private fun deliver(answer: Answer, voice: Boolean) {
        answer.switchedNote?.let { line(Line.SYS, it) }
        line(Line.CAPRI, answer.text)
        if (brain.mode != prefs.mode || brain.studyLevel != prefs.studyLevel) {
            prefs.mode = brain.mode
            prefs.studyLevel = brain.studyLevel
            bubble("mode", brain.mode)
        }
        if (Security.pending()) bubble("confirm")
        speak(answer.spoken, voice)
        if (answer.follow.isNotEmpty()) {
            val r = Runnable {
                follow = null
                brain.followed(answer.follow)
                line(Line.CAPRI, answer.follow)
                speak(answer.follow, false)
            }
            follow = r
            main.postDelayed(r, 2600)
        }
    }

    private fun cancelFollow() { follow?.let { main.removeCallbacks(it) }; follow = null }

    // Out loud when her voice is on: always for what they said out loud, and for typed chat unless Quiet is on.
    fun speak(text: String, voice: Boolean) {
        val talks = prefs.voice != "off" && (voice || !prefs.quiet)
        if (!talks) { setMood(Mood.IDLE); if (voice) keepGoing(); return }
        setMood(Mood.SPEAKING)
        mouth.say(text) { interrupted -> main.post { if (mood == Mood.SPEAKING) setMood(Mood.IDLE); if (voice && !interrupted) keepGoing() } }
    }

    private fun keepGoing() { if (voiceChat && prefs.keepListening) main.postDelayed({ if (voiceChat && mood == Mood.IDLE) listen() }, 350) }

    fun hasMic(): Boolean = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // Listening: in the bubble's service when it holds the microphone, otherwise through a see-through screen, which
    // Android counts as being in front, so the microphone is allowed.
    fun listen(conversation: Boolean = voiceChat) {
        voiceChat = conversation
        cancelFollow()
        mouth.stop()
        if (!micHere()) {
            app.startActivity(Intent(app, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("conversation", conversation))
            return
        }
        ears.listen()
    }

    fun heard(text: String) = say(text, voice = true)

    // "Hey Capri": she stops what she's saying or thinking and listens.
    fun wakeHeard() {
        if (ears.listening) return
        if (busy) { brain.cancel(); hands.stopJob() }
        mouth.stop()
        bubble("wake")
        if (prefs.wakeSays && prefs.voice != "off") {
            setMood(Mood.SPEAKING)
            mouth.say(Lang.pick("Yes?", "Oui ?")) { cut -> main.post { if (mood == Mood.SPEAKING) setMood(Mood.IDLE); if (!cut) listen(conversation = prefs.keepListening) } }
            return
        }
        Earcon.ping(app)
        main.postDelayed({ listen(conversation = prefs.keepListening) }, 180)
    }

    // They talked over her: her voice stops and she listens to what they're saying.
    fun talkedOver() {
        if (mood != Mood.SPEAKING) return
        mouth.stop()
        setMood(Mood.IDLE)
        listen(conversation = true)
    }

    fun stopVoice() {
        voiceChat = false
        ears.cancel()
        mouth.stop()
        if (mood == Mood.LISTENING || mood == Mood.SPEAKING) setMood(Mood.IDLE)
    }

    // Stops everything she's doing: thinking, talking, listening and any job on the screen.
    fun interrupt() {
        cancelFollow()
        brain.cancel()
        hands.stopJob()
        stopVoice()
        setMood(Mood.IDLE)
    }

    fun forgetChat() {
        brain.forget()
        lines.clear()
        for (x in listeners) x.onLine(Line(Line.SYS, ""))
    }

    fun log(where: String, what: String) {
        io.execute {
            try {
                val line = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()) + " | " + where + " | " + Redact.text(what).replace('\n', ' ')
                val old = if (errors.exists()) errors.readLines() else emptyList()
                errors.writeText((old.takeLast(199) + line.take(400)).joinToString("\n") + "\n")
            } catch (_: Exception) { }
        }
    }

    fun errorLog(): String = try { if (errors.exists()) errors.readText() else "" } catch (e: Exception) { "" }

    companion object {
        fun of(app: CapriApp) = app.capri
    }
}
