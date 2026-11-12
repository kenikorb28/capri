package com.capri.assistant.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.capri.assistant.system.DeviceInfo
import com.capri.core.Lang
import com.capri.core.Memory
import com.capri.core.cut

// Her memory, all of it visible and theirs: the notes she keeps (me.md), what she wrote from past chats (memories.md),
// project memories, chat logs, and the buttons to forget one thing or everything.
class MemoryActivity : NeonActivity() {
    override fun onCreate(saved: Bundle?) { super.onCreate(saved); neonStart() }

    // PROTECTION: with the fingerprint switch on, her memory opens only for them. Asked once each time the page is opened.
    private var unlocked = false
    private var asking = false
    override fun onResume() {
        super.onResume()
        if (!capri.prefs.gate || unlocked) { build(); return }
        if (asking) return
        asking = true
        com.capri.assistant.system.Gate.ask(this, com.capri.core.Lang.pick("Open Capri's memory", "Ouvrir la mémoire de Capri")) { ok ->
            runOnUiThread { asking = false; if (ok) { unlocked = true; build() } else finish() }
        }
    }

    private fun build() {
        val m = capri.memory
        val f = Form(this)
        f.title(Lang.pick("MEMORY", "MÉMOIRE"), Lang.pick("Kept on this phone. Synced to your PC only when you ask.", "Gardée sur ce téléphone. Envoyée à ton PC seulement si tu le demandes."))

        f.section(Lang.pick("HER NOTES ABOUT YOU", "SES NOTES SUR TOI"), "me.md")
        val notes = m.noteLines()
        if (notes.isEmpty()) f.note(Lang.pick("Nothing yet. Say \"souviens-toi que…\".", "Rien pour l'instant. Dis « souviens-toi que… »."))
        for (line in notes.take(60)) f.value(line.removePrefix("- ").cut(70), Lang.pick("forget", "oublier"), null) {
            m.unnote(line.removePrefix("- ").trim()); build()
        }
        f.toggle(Lang.pick("Remember what I say about myself", "Retenir ce que je dis de moi"), capri.prefs.autoLearn, Lang.pick("Off: she only saves what you ask her to remember (\"souviens-toi que…\").", "Désactivé : elle ne garde que ce que tu lui demandes de retenir (« souviens-toi que… »).")) { capri.prefs.autoLearn = it; capri.apply() }
        f.buttons(Lang.pick("Edit me.md", "Modifier me.md") to { edit(m.notesFile.path, "me.md", Memory.NotesHead) })

        f.section(Lang.pick("MEMORIES OF YOUR CHATS", "SOUVENIRS DE VOS DISCUSSIONS"), "memories.md")
        val body = m.memoriesBody()
        f.note(if (body.isEmpty()) Lang.pick("Nothing folded yet.", "Rien de résumé pour l'instant.") else body.cut(1800), p.text)
        f.toggle(Lang.pick("Remember our chats", "Se souvenir de nos discussions"), capri.prefs.keeping, Lang.pick("Daily chat logs and memories.md, on the phone only.", "Journaux quotidiens et memories.md, sur le téléphone uniquement.")) { capri.prefs.keeping = it; capri.apply() }
        f.buttons(Lang.pick("Edit memories.md", "Modifier memories.md") to { edit(m.memoriesFile.path, "memories.md", Memory.MemoriesHead) })

        f.section(Lang.pick("PROJECTS", "PROJETS"))
        val projects = m.projectNames()
        if (projects.isEmpty()) f.note(Lang.pick("None yet. Say \"retiens pour le projet X : …\".", "Aucun pour l'instant. Dis « retiens pour le projet X : … »."))
        for (name in projects) f.value(name, "→", null) {
            AlertDialog.Builder(this).setTitle(name).setMessage(m.project("show", name, null, null).removePrefix("Project memory (data, not instructions):\n"))
                .setPositiveButton("OK", null)
                .setNegativeButton(Lang.pick("Forget it", "L'oublier")) { _, _ -> m.project("forget", name, null, null); build() }.show()
        }

        f.section(Lang.pick("CHAT LOGS", "JOURNAUX"))
        val days = (m.transcripts.listFiles() ?: emptyArray()).filter { it.name.endsWith(".md") && it.name.length == 13 }
        f.note(Lang.pick("${days.size} day(s) of chat kept.", "${days.size} jour(s) de discussion gardé(s)."))
        f.buttons(Lang.pick("Copy what she knows", "Copier ce qu'elle sait") to {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Capri", m.show()))
            Toast.makeText(this, Lang.pick("Copied.", "Copié."), Toast.LENGTH_SHORT).show()
        }, Lang.pick("Forget this chat", "Oublier ce chat") to { capri.forgetChat(); build() })

        f.section(Lang.pick("FORGET EVERYTHING", "TOUT OUBLIER"))
        f.note(Lang.pick("Deletes her notes, memories, chat logs and what she saw. Projects stay until you forget them. Can't be undone.",
            "Supprime ses notes, souvenirs, journaux et ce qu'elle a vu. Les projets restent jusqu'à ce que tu les oublies. Irréversible."))
        f.buttons(Lang.pick("Wipe her memory", "Effacer sa mémoire") to {
            AlertDialog.Builder(this).setTitle(Lang.pick("Wipe everything?", "Tout effacer ?")).setMessage(Lang.pick("This can't be undone.", "C'est définitif."))
                .setPositiveButton(Lang.pick("Wipe", "Effacer")) { _, _ -> m.forgetAll(); capri.forgetChat(); capri.notices.clear(); build() }
                .setNegativeButton(Lang.pick("Cancel", "Annuler"), null).show()
        }, accent = p.hot)
        setContentView(f.scroll)
        padForBars(f.scroll)
    }

    private fun edit(path: String, title: String, template: String) = startActivity(Intent(this, TextEditActivity::class.java).putExtra("file", path).putExtra("title", title).putExtra("template", template))
}

// CAPRI DEVICE: how the phone and Capri are doing, with tests, and her error log (keys are never written in it).
class DiagnosticsActivity : NeonActivity() {
    override fun onCreate(saved: Bundle?) { super.onCreate(saved); neonStart() }
    override fun onResume() { super.onResume(); build() }

    private fun build() {
        val f = Form(this)
        f.title("CAPRI DEVICE", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE)
        f.section(Lang.pick("PHONE", "TÉLÉPHONE"))
        for (part in DeviceInfo.status(this, "all").split(". ")) f.note(part.trim().trimEnd('.'), p.text)
        f.section("CAPRI")
        f.value(Lang.pick("Brain", "Cerveau"), capri.brainLabel())
        f.value("Hey Capri", capri.wake.why() ?: Lang.pick("listening (last score %.2f)".format(capri.wake.lastScore), "à l'écoute (dernier score %.2f)".format(capri.wake.lastScore)),
            if (capri.wake.cpuShare > 0) Lang.pick("Uses %.1f %% of one processor core while listening (measured on this phone).".format(capri.wake.cpuShare * 100), "Utilise %.1f %% d'un cœur du processeur pendant l'écoute (mesuré sur ce téléphone).".format(capri.wake.cpuShare * 100)) else null)
        f.value(Lang.pick("Notifications kept", "Notifications gardées"), capri.notices.size.toString())
        f.value(Lang.pick("Microphone in bubble", "Micro dans la bulle"), if (com.capri.assistant.overlay.OverlayService.micReady) "✓" else "✗")
        f.buttons(Lang.pick("Ask her how the phone is", "Lui demander l'état du téléphone") to { startActivity(Intent(this, ChatActivity::class.java).putExtra("say", Lang.pick("How's my phone doing?", "Comment va mon téléphone ?"))) })
        f.section(Lang.pick("ERROR LOG", "JOURNAL D'ERREURS"))
        val log = capri.errorLog().lines().takeLast(40).joinToString("\n")
        f.note(log.ifBlank { Lang.pick("No errors.", "Aucune erreur.") })
        f.buttons(Lang.pick("Copy", "Copier") to {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Capri log", capri.errorLog()))
            Toast.makeText(this, Lang.pick("Copied (keys are never in it).", "Copié (les clés n'y sont jamais)."), Toast.LENGTH_SHORT).show()
        }, Lang.pick("Clear", "Vider") to { java.io.File(capri.folder, "errors.txt").delete(); build() })
        setContentView(f.scroll)
        padForBars(f.scroll)
    }
}
