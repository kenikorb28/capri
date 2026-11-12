package com.capri.assistant.system

import android.Manifest
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import com.capri.assistant.assist.NotificationWatcher
import com.capri.assistant.engine.Capri
import com.capri.assistant.screen.CapriAccessibility
import com.capri.core.Check
import com.capri.core.Lang
import com.capri.core.Problems
import com.capri.core.Usage

// « Capri, vérifie-toi » : she really tries each function and says what she found. The brain check lists its models
// (a real call with her key, free); a cloud voice says one short word (a few characters of credit); Capri Link does a
// real sync. Never on the main thread: several of these wait on the network.
object SelfCheck {
    fun run(capri: Capri): List<Check> {
        val app = capri.app
        val p = capri.prefs
        val out = ArrayList<Check>()
        fun add(name: String, level: Int, detail: String) { out.add(Check(name, level, detail)) }
        fun took(since: Long) = String.format(java.util.Locale.ROOT, "%.1f s", (System.currentTimeMillis() - since) / 1000.0)
        fun why(e: Exception) = e.message ?: e.javaClass.simpleName

        if (p.halted) add(Lang.pick("Emergency stop", "Arrêt d'urgence"), Check.WARN, Lang.pick("on: she does nothing until you resume", "actif : elle ne fait rien tant que tu n'as pas repris"))

        val online = DeviceInfo.online(app)
        add("Internet", if (online) Check.OK else Check.BAD, if (online) Lang.pick("connected", "connecté") else Lang.pick("no connection", "pas de connexion"))

        val brain = Lang.pick("Brain", "Cerveau")
        val source = capri.brain.source
        if (source == null) add(brain, Check.BAD, Lang.pick("none set up (Settings > BRAIN)", "aucun configuré (Réglages > CERVEAU)"))
        else try {
            val t = System.currentTimeMillis()
            val models = capri.backends.models(source)
            add(brain, Check.OK, capri.brainLabel() + Lang.pick(" answers (", " répond (") + took(t) + ", " + models.size + Lang.pick(" models)", " modèles)"))
        } catch (e: Exception) { add(brain, Check.BAD, Problems.explain(e, source) ?: why(e)) }

        val voice = Lang.pick("Voice", "Voix")
        when (p.voice) {
            "off" -> add(voice, Check.OFF, Lang.pick("turned off", "coupée"))
            "system" -> if (capri.mouth.voices().isNotEmpty()) add(voice, Check.OK, Lang.pick("the phone's voice is ready", "la voix du téléphone est prête"))
            else add(voice, Check.WARN, Lang.pick("the phone's voice isn't ready: open Text-to-speech in Android", "la voix du téléphone n'est pas prête : ouvre « Synthèse vocale » dans Android"))
            else -> {
                val t = System.currentTimeMillis()
                val failed = capri.mouth.probe()
                val name = Usage.shown(capri.voiceName(p.voice))
                if (failed == null) add(voice, Check.OK, name + Lang.pick(" answers (", " répond (") + took(t) + ")") else add(voice, Check.BAD, failed)
            }
        }

        val mic = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hears = try { SpeechRecognizer.isRecognitionAvailable(app) } catch (e: Exception) { false }
        add(Lang.pick("Microphone", "Micro"), if (!mic) Check.BAD else if (hears || p.ears == "whisper") Check.OK else Check.WARN,
            if (!mic) Lang.pick("not allowed", "pas autorisé") else if (hears || p.ears == "whisper") Lang.pick("allowed, speech recognition is there", "autorisé, la reconnaissance vocale est là")
            else Lang.pick("allowed, but this phone has no speech recognition service", "autorisé, mais ce téléphone n'a pas de service de reconnaissance vocale"))

        if (!p.heyCapri) add("Hey Capri", Check.OFF, Lang.pick("turned off", "désactivé"))
        else capri.wake.why().let { reason -> if (reason == null) add("Hey Capri", Check.OK, Lang.pick("listening", "à l'écoute")) else add("Hey Capri", Check.WARN, reason) }

        val overlay = Settings.canDrawOverlays(app)
        add(Lang.pick("Bubble", "Bulle"), if (overlay) Check.OK else Check.BAD, if (overlay) Lang.pick("may show over apps", "peut s'afficher par-dessus les apps") else Lang.pick("not allowed to show over apps", "pas autorisée à s'afficher par-dessus les apps"))

        val hands = CapriAccessibility.on()
        add(Lang.pick("Hands on screen", "Mains sur l'écran"), if (hands) Check.OK else Check.WARN, if (hands) Lang.pick("accessibility is on", "l'accessibilité est active")
        else Lang.pick("accessibility is off: no \"do it for me\", no pressing buttons", "accessibilité coupée : pas de « fais-le pour moi », pas d'appui sur les boutons"))

        val notes = NotificationWatcher.granted(app)
        add("Notifications", if (notes) Check.OK else Check.WARN, if (notes) Lang.pick("she may read them when you ask", "elle peut les lire quand tu demandes") else Lang.pick("no access: no summaries, no watches", "pas d'accès : ni résumés, ni surveillances"))

        val exact = Alarms.exactAllowed(app)
        add(Lang.pick("Reminders", "Rappels"), if (exact) Check.OK else Check.WARN, if (exact) Lang.pick("on time", "à l'heure exacte") else Lang.pick("can be up to 10 minutes late", "peuvent avoir jusqu'à 10 min de retard"))

        val unlimited = try { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) } catch (e: Exception) { false }
        add(Lang.pick("Battery", "Batterie"), if (unlimited) Check.OK else Check.WARN, if (unlimited) Lang.pick("no limit on Capri", "aucune limite sur Capri") else Lang.pick("the phone may close her bubble to save power", "le téléphone peut fermer sa bulle pour économiser"))

        if (!capri.sync.paired()) add("Capri Link", Check.OFF, Lang.pick("no PC paired", "aucun PC appairé"))
        else if (p.halted) add("Capri Link", Check.WARN, Lang.pick("paused by the emergency stop", "en pause (arrêt d'urgence)"))
        else try {
            val t = System.currentTimeMillis()
            if (capri.sync.syncNow("check") != null) add("Capri Link", Check.OK, Lang.pick("synced with ", "synchronisé avec ") + p.linkPcName.ifBlank { "PC" } + " (" + took(t) + ")")
            else add("Capri Link", Check.WARN, Lang.pick("a sync was already running", "une synchro était déjà en cours"))
        } catch (e: Exception) { add("Capri Link", Check.BAD, Lang.pick("your PC doesn't answer (", "ton PC ne répond pas (") + why(e) + ")") }

        val free = app.filesDir.usableSpace / (1024L * 1024L)
        add(Lang.pick("Storage", "Stockage"), if (free >= 200) Check.OK else Check.WARN, "" + free + Lang.pick(" MB free", " Mo libres"))

        if (!p.gate) add("Protection", Check.OFF, Lang.pick("fingerprint check turned off", "vérification par empreinte désactivée"))
        else if (Gate.ready(app)) add("Protection", Check.OK, Lang.pick("fingerprint or phone code asked for sensitive actions", "empreinte ou code demandés pour les actions sensibles"))
        else add("Protection", Check.BAD, Lang.pick("on, but this phone has no fingerprint or screen lock: sensitive actions are refused", "activée, mais ce téléphone n'a ni empreinte ni verrou : les actions sensibles sont refusées"))
        return out
    }
}
