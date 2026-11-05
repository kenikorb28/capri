package com.capri.assistant.ui

import android.os.Bundle
import com.capri.core.Args
import com.capri.core.Lang
import com.capri.core.Risk
import com.capri.core.Security
import com.capri.core.Toolbox

// Her tools and plugins: what each one does, its danger level, the SECURITY switch that governs it, and whether it can
// work on this phone right now (and if not, why). Plugins are the tools added from outside her core.
class ToolsActivity : NeonActivity() {
    override fun onCreate(saved: Bundle?) { super.onCreate(saved); neonStart() }
    override fun onResume() { super.onResume(); build() }

    private val groups = linkedMapOf("android" to "ANDROID", "apps" to "APPS", "web" to "WEB", "vision" to "VISION", "audio" to "AUDIO", "files" to "FICHIERS",
        "clipboard" to Lang.pick("CLIPBOARD", "PRESSE-PAPIERS"), "notifications" to "NOTIFICATIONS", "tasks" to Lang.pick("TASKS & REMINDERS", "TÂCHES ET RAPPELS"),
        "memory" to Lang.pick("MEMORY", "MÉMOIRE"), "modes" to "MODES", "device" to "DEVICE", "sync" to "SYNC", "capri" to "CAPRI", "plugins" to "PLUGINS")

    private fun risk(r: Risk) = when (r) { Risk.LOW -> Lang.pick("low", "faible"); Risk.MEDIUM -> Lang.pick("medium", "moyen"); Risk.HIGH -> Lang.pick("high, always asks", "élevé, demande toujours") }

    private fun build() {
        val f = Form(this)
        val all = Toolbox.all
        f.title(Lang.pick("TOOLS & PLUGINS", "OUTILS ET PLUGINS"), Lang.pick("${all.size} tools, ${Toolbox.plugins.size} from plugins. Her brain picks them itself.", "${all.size} outils, dont ${Toolbox.plugins.size} venus de plugins. Son cerveau les choisit seul."))
        val plugins = Toolbox.plugins.map { it.name }.toSet()
        for ((g, label) in groups) {
            val list = all.filter { (if (it.name in plugins) "plugins" else it.group) == g }
            if (list.isEmpty()) continue
            f.section(label, list.size.toString())
            for (t in list) {
                val missing = capri.hands.missing(t.name)
                val highest = listOf(t.risk(Args.of()), t.risk(Args.of("action" to "delete", "uri" to "x")), t.risk(Args.of("action" to "call")), t.risk(Args.of("action" to "forget_all"))).maxByOrNull { it.ordinal } ?: Risk.LOW
                val sub = t.ability + " · " + Lang.pick("risk ", "risque ") + risk(highest) + (if (t.toggle >= 0) " · " + Security.Names[t.toggle] + (if (Security.on(t.toggle)) "" else Lang.pick(" (asks first)", " (demande d'abord)")) else "") +
                    (if (t.online) Lang.pick(" · needs internet", " · demande internet") else "")
                f.value(t.name, if (missing == null) "✓" else "✗", if (missing == null) sub else sub + "\n" + Lang.pick("Not now: ", "Pas maintenant : ") + missing)
            }
        }
        f.section(Lang.pick("ADD A PLUGIN", "AJOUTER UN PLUGIN"))
        f.note(Lang.pick("A plugin is a ToolSpec (name, description, parameters, required ones, SECURITY switch, risk) given to Toolbox.register, its hands given to PhoneHands.plug, and optional phrases via Intents.route. See app/.../plugins/Plugins.kt: the calculator and random pick are plugins.",
            "Un plugin, c'est un ToolSpec (nom, description, paramètres, ceux obligatoires, interrupteur SÉCURITÉ, risque) donné à Toolbox.register, ses mains données à PhoneHands.plug, et s'il veut des phrases via Intents.route. Voir app/.../plugins/Plugins.kt : la calculatrice et le tirage au sort sont des plugins."))
        setContentView(f.scroll)
        padForBars(f.scroll)
    }
}
