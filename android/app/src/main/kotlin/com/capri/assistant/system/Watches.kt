package com.capri.assistant.system

import android.app.Notification
import android.app.NotificationManager
import com.capri.assistant.R
import com.capri.assistant.engine.Capri
import com.capri.assistant.engine.Line
import com.capri.assistant.overlay.OverlayService
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.Notice
import com.capri.core.arr
import com.capri.core.num
import com.capri.core.obj
import com.capri.core.str

// "Dis-moi quand la batterie est chargée": things she watches for while her bubble runs, each told once, then dropped.
class Watches(private val capri: Capri) {
    class Watch(val id: Int, val kind: String, val text: String?, val level: Int?)

    private val list = ArrayList<Watch>()
    private var lastPlugged: Boolean? = null
    private var lastWifi: Boolean? = null

    fun load() = synchronized(list) {
        list.clear()
        for (v in Json.parseOrNull(capri.prefs.watches).arr() ?: emptyList()) {
            val o = v.obj() ?: continue
            list.add(Watch(o["id"].num()?.toInt() ?: continue, o["kind"].str() ?: continue, o["text"].str(), o["level"].num()?.toInt()))
        }
    }

    private fun save() { capri.prefs.watches = Json.write(synchronized(list) { list.map { mapOf("id" to it.id, "kind" to it.kind, "text" to it.text, "level" to it.level) } }) }

    private fun describe(w: Watch): String = when (w.kind) {
        "battery_full" -> Lang.pick("battery reaches ${w.level ?: 100}%", "batterie à ${w.level ?: 100} %")
        "battery_low" -> Lang.pick("battery drops to ${w.level ?: 20}%", "batterie sous ${w.level ?: 20} %")
        "charging" -> Lang.pick("charger plugged in", "chargeur branché")
        "unplugged" -> Lang.pick("charger unplugged", "chargeur débranché")
        "wifi_on" -> Lang.pick("Wi-Fi connects", "Wi-Fi connecté")
        "wifi_off" -> Lang.pick("Wi-Fi drops", "Wi-Fi perdu")
        else -> Lang.pick("a notification about ", "une notification de ") + (w.text ?: "?")
    }

    fun run(action: String?, kind: String?, text: String?, level: String?): String {
        when (action) {
            "list" -> {
                val all = synchronized(list) { ArrayList(list) }
                return if (all.isEmpty()) Lang.pick("No watches set.", "Aucune surveillance en cours.") else Lang.pick("Watching for: ", "Je surveille : ") + all.joinToString("; ") { describe(it) }
            }
            "stop" -> {
                val gone = synchronized(list) { val before = list.size; list.removeAll { text.isNullOrBlank() && kind == null || it.kind == kind || (text != null && (it.text ?: "").contains(text, true)) }; before - list.size }
                save()
                return if (gone == 0) Lang.pick("No watch matched.", "Aucune surveillance ne correspond.") else Lang.pick("Stopped $gone watch(es).", "$gone surveillance(s) arrêtée(s).")
            }
        }
        val k = kind ?: return Lang.pick("That didn't work: watch for what?", "Ça n'a pas marché : surveiller quoi ?")
        if (k == "notification" && text.isNullOrBlank()) return Lang.pick("That didn't work: whose notification?", "Ça n'a pas marché : la notification de qui ?")
        val w = Watch((System.currentTimeMillis() % 1_000_000).toInt(), k, text, level?.filter { it.isDigit() }?.toIntOrNull())
        synchronized(list) { list.add(w) }
        save()
        val note = if (!OverlayService.running) Lang.pick(" It only works while her bubble is on.", " Ça marche seulement quand ma bulle est active.") else ""
        val access = if (k == "notification" && !com.capri.assistant.assist.NotificationWatcher.granted(capri.app)) Lang.pick(" Notification access is off, so turn it on in Capri Security.", " L'accès aux notifications est coupé : active-le dans Capri Sécurité.") else ""
        return Lang.pick("Watching: I'll tell them when the ", "Je surveille : je te préviens quand ") + describe(w) + "." + note + access
    }

    private fun fire(w: Watch, said: String) {
        synchronized(list) { list.removeAll { it.id == w.id } }
        save()
        capri.main.post {
            capri.line(Line.CAPRI, said)
            capri.speak(said, voice = false)
            capri.bubble("glitch")
        }
        if (Notify.allowed(capri.app)) {
            val n = Notification.Builder(capri.app, Notify.REMIND).setSmallIcon(R.drawable.ic_stat_capri).setContentTitle("Capri").setContentText(said)
                .setContentIntent(Notify.openApp(capri.app)).setAutoCancel(true).build()
            capri.app.getSystemService(NotificationManager::class.java).notify(3000 + (w.id % 1000), n)
        }
    }

    // CAPRI DEVICE's own warnings, once each: a low battery while unplugged, and storage nearly full (checked at most daily).
    private var lowWarned = false

    fun proactive(percent: Int, plugged: Boolean) {
        if (!capri.prefs.deviceAlerts || percent < 0) return
        if (plugged || percent > 20) { lowWarned = false; return }
        if (percent <= 15 && !lowWarned) { lowWarned = true; tell(Lang.pick("Your battery is low: $percent%.", "Ta batterie est faible : $percent %."), 2901) }
    }

    fun storage() {
        if (!capri.prefs.deviceAlerts) return
        val now = System.currentTimeMillis()
        if (now - capri.prefs.storageCheckedAt < 86400_000L) return
        capri.prefs.storageCheckedAt = now
        val (free, total) = DeviceInfo.storage()
        if (free < 2L * 1024 * 1024 * 1024 || free * 10 < total) tell(Lang.pick("Your storage is nearly full: %.1f GB left.".format(free / 1_073_741_824.0), "Ton stockage commence à être presque plein : %.1f Go libres.".format(free / 1_073_741_824.0)), 2902)
    }

    private fun tell(said: String, id: Int) {
        capri.main.post { capri.line(Line.CAPRI, said); if (!capri.prefs.quiet) capri.speak(said, voice = false) }
        if (Notify.allowed(capri.app)) {
            val n = Notification.Builder(capri.app, Notify.REMIND).setSmallIcon(R.drawable.ic_stat_capri).setContentTitle("Capri Device").setContentText(said)
                .setContentIntent(Notify.openApp(capri.app)).setAutoCancel(true).build()
            capri.app.getSystemService(NotificationManager::class.java).notify(id, n)
        }
    }

    private fun each(match: (Watch) -> Boolean, said: (Watch) -> String) {
        val due = synchronized(list) { list.filter(match) }
        for (w in due) fire(w, said(w))
    }

    fun battery(percent: Int, plugged: Boolean) {
        each({ it.kind == "battery_full" && percent >= (it.level ?: 100) }) { Lang.pick("Your battery is at $percent%.", "Ta batterie est à $percent %.") }
        each({ it.kind == "battery_low" && percent <= (it.level ?: 20) && !plugged }) { Lang.pick("Battery's down to $percent%. Plug me in, choom.", "Batterie à $percent %. Branche-moi, choom.") }
        val was = lastPlugged
        lastPlugged = plugged
        if (was != null && was != plugged) {
            if (plugged) each({ it.kind == "charging" }) { Lang.pick("Charger's in.", "Chargeur branché.") }
            else each({ it.kind == "unplugged" }) { Lang.pick("Charger's out.", "Chargeur débranché.") }
        }
    }

    fun wifi(on: Boolean) {
        val was = lastWifi
        lastWifi = on
        if (was == null || was == on) return
        if (on) each({ it.kind == "wifi_on" }) { Lang.pick("Wi-Fi's back.", "Le Wi-Fi est revenu.") } else each({ it.kind == "wifi_off" }) { Lang.pick("Wi-Fi dropped.", "Le Wi-Fi a sauté.") }
    }

    fun notice(n: Notice) {
        each({ w -> w.kind == "notification" && w.text != null && w.text.split(Regex("\\s+")).filter { it.length > 1 }.all { word -> (n.app + " " + n.title + " " + n.text).contains(word, true) } }) {
            Lang.pick("${n.app}: ${n.title}", "${n.app} : ${n.title}")
        }
    }
}
