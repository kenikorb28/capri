package com.capri.assistant.plugins

import android.content.Intent
import com.capri.assistant.engine.Capri
import com.capri.assistant.system.Gate
import com.capri.assistant.system.SelfCheck
import com.capri.assistant.ui.SettingsActivity
import com.capri.core.Checks
import com.capri.core.ExtraPhrases
import com.capri.core.Intents
import com.capri.core.Lang
import com.capri.core.ToolSpec
import com.capri.core.Toolbox
import com.capri.core.Usage

// The November additions as plugins: corrections she keeps, this month's usage, her self-check, the emergency stop
// and the backup page. Each has a tool for her brain and plain phrases (core/Extra.kt) that work with no brain at all.
object Extras {
    fun install(capri: Capri) {
        Toolbox.register(ToolSpec("correction", "Keep a correction, in their own words, when they say you got something wrong or tell you how to do it from now on (action add); or list the corrections you keep (action list). A correction is a note: 'remember' with action forget drops one.",
            Toolbox.s(Toolbox.p("action", "add or list", listOf("add", "list")) + "," + Toolbox.p("text", "For add: what to do from now on, in their words")), -1, "keep corrections so you don't repeat a mistake", "plugins"))
        capri.hands.plug("correction") { args -> if (args.action == "list") corrections(capri) else capri.memory.correct(args.text) }
        Intents.route(ExtraPhrases::correction)

        Toolbox.register(ToolSpec("usage_report", "Say how much of the cloud brain (tokens) and the cloud voice (characters) was used this month, against the monthly limits they set.",
            Toolbox.s(""), -1, "tell this month's usage and limits", "plugins"))
        capri.hands.plug("usage_report") { capri.usage.report(capri.prefs.capBrain, capri.prefs.capVoice, Usage.price(capri.prefs.priceBrain), Usage.price(capri.prefs.priceVoice)) }
        Intents.route(ExtraPhrases::usage)

        Toolbox.register(ToolSpec("self_check", "Really try your own functions on this phone (brain, voice, microphone, bubble, accessibility, notifications, Capri Link) and say what works and what doesn't.",
            Toolbox.s(""), -1, "check that your own functions really work", "plugins"))
        capri.hands.plug("self_check") { Checks.summary(SelfCheck.run(capri)) }
        Intents.route(ExtraPhrases::check)

        Toolbox.register(ToolSpec("emergency_stop", "The emergency stop. Action stop halts everything you are doing and every tool, and it stays stopped; action resume lifts it. Only when they ask for exactly that.",
            Toolbox.s(Toolbox.p("action", "stop or resume", listOf("stop", "resume")), "action"), -1, "stop everything at once, and resume when they say", "plugins"))
        capri.hands.plug("emergency_stop") { args -> emergency(capri, args.action) }
        Intents.route(ExtraPhrases::emergency)

        Toolbox.register(ToolSpec("backup", "Open the page where they create or restore an encrypted backup of your memory and settings. They pick the password and the file themselves.",
            Toolbox.s(""), -1, "open the encrypted backup page", "plugins"))
        capri.hands.plug("backup") {
            try {
                capri.app.startActivity(Intent(capri.app, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Lang.pick("I opened Settings: under BACKUP you pick a password and where the file goes.", "Je t'ai ouvert les Réglages : dans SAUVEGARDE, tu choisis un mot de passe et l'endroit où ranger le fichier.")
            } catch (e: Exception) { Lang.pick("Open Settings > BACKUP in the app.", "Ouvre Réglages > SAUVEGARDE dans l'app.") }
        }
        Intents.route(ExtraPhrases::backup)
    }

    private fun corrections(capri: Capri): String {
        val list = capri.memory.corrections()
        return if (list.isEmpty()) Lang.pick("I keep no correction yet. Tell me \"tu t'es trompée : …\" and I will.", "Je ne garde aucune correction pour l'instant. Dis-moi « tu t'es trompée : … » et je la garde.")
        else Lang.pick("The corrections I keep:\n", "Les corrections que je garde :\n") + list.withIndex().joinToString("\n") { (i, c) -> "" + (i + 1) + ". " + c }
    }

    // Stopping is always allowed at once; starting again asks for the fingerprint when that protection is on.
    private fun emergency(capri: Capri, action: String?): String {
        if (action != "resume") {
            capri.main.post { capri.emergencyStop() }
            return Lang.pick("Emergency stop: everything is off. Say \"resume\" when you want me back.", "Arrêt d'urgence : tout est coupé. Dis « reprends » quand tu veux que je reprenne.")
        }
        if (!capri.prefs.halted) return Lang.pick("Nothing was stopped.", "Rien n'était arrêté.")
        if (capri.prefs.gate && !Gate.blocking(capri.app, Lang.pick("Resume Capri", "Reprendre Capri"))) return Lang.pick("Still stopped: resuming needs your fingerprint or your phone's code.", "Toujours à l'arrêt : il faut ton empreinte ou le code du téléphone pour reprendre.")
        capri.main.post { capri.resumeAll() }
        return Lang.pick("I'm back.", "C'est reparti.")
    }
}
