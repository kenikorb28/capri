package com.capri.assistant.ui

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import com.capri.assistant.assist.NotificationWatcher
import com.capri.assistant.overlay.OverlayService
import com.capri.assistant.screen.CapriAccessibility
import com.capri.assistant.system.Alarms
import com.capri.core.Lang
import com.capri.core.Security

// CAPRI SECURITY: every door Capri can use, whether it's open, what it's for, and the button that opens Android's own
// page to turn it on or off. Nothing here is switched on behind their back; Android shows its own question each time.
class PermissionsActivity : NeonActivity() {
    override fun onCreate(saved: Bundle?) { super.onCreate(saved); neonStart() }
    override fun onResume() { super.onResume(); build() }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun go(i: Intent) = try { startActivity(i) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    private val details get() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun build() {
        val f = Form(this)
        f.title(Lang.pick("CAPRI SECURITY", "CAPRI SÉCURITÉ"), Lang.pick("What Capri may use, and how to take it back.", "Ce que Capri peut utiliser, et comment le reprendre."))
        fun door(name: String, on: Boolean, why: String, open: () -> Unit) { f.value(name, if (on) "✓ " + Lang.pick("on", "actif") else "✗ " + Lang.pick("off", "coupé"), why) { open() } }

        // The board at a glance: 🟢 allowed, 🟡 asked each time or partly, 🔴 off.
        f.section(Lang.pick("AT A GLANCE", "EN UN COUP D'ŒIL"))
        fun dot(name: String, state: Int, note: String) = f.value(name, (when (state) { 2 -> "🟢"; 1 -> "🟡"; else -> "🔴" }) + " " + note)
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION); val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        dot(Lang.pick("Microphone", "Microphone"), if (has(Manifest.permission.RECORD_AUDIO)) 2 else 0, if (capri.wake.why() == null) Lang.pick("Hey Capri listening", "Hey Capri à l'écoute") else Lang.pick("only when you talk to her", "seulement quand tu lui parles"))
        dot(Lang.pick("Camera", "Caméra"), 1, Lang.pick("through your camera app, each photo", "via ton app Photo, à chaque photo"))
        dot(Lang.pick("Location", "Localisation"), if (fine) 2 else if (coarse) 1 else 0, Lang.pick("only for EMERGENCY, if set up", "seulement pour l'URGENCE, si configurée"))
        dot(Lang.pick("Notifications", "Notifications"), if (NotificationWatcher.granted(this)) 2 else 0, Lang.pick("read only when you ask", "lues seulement si tu demandes"))
        dot(Lang.pick("Accessibility", "Accessibilité"), if (CapriAccessibility.on()) 2 else 0, Lang.pick("acts only on your request", "agit seulement à ta demande"))
        dot(Lang.pick("Draw over apps", "Superposition"), if (Settings.canDrawOverlays(this)) 2 else 0, Lang.pick("her bubble", "sa bulle"))
        dot(Lang.pick("Screen capture", "Capture d'écran"), if (CapriAccessibility.on()) 2 else 1, if (CapriAccessibility.on()) Lang.pick("when you ask, never secure screens", "quand tu demandes, jamais les écrans protégés") else Lang.pick("Android asks you each time", "Android te demande à chaque fois"))

        f.section(Lang.pick("ESSENTIAL", "ESSENTIEL"))
        door(Lang.pick("Draw over apps", "Affichage par-dessus"), Settings.canDrawOverlays(this), Lang.pick("Her bubble and panel.", "Sa bulle et son panneau.")) { go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        door(Lang.pick("Microphone", "Micro"), has(Manifest.permission.RECORD_AUDIO), Lang.pick("Only when you talk to her, and for Hey Capri if you turn it on.", "Seulement quand tu lui parles, et pour Hey Capri si tu l'actives.")) { if (has(Manifest.permission.RECORD_AUDIO)) go(details) else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        if (Build.VERSION.SDK_INT >= 33) door(Lang.pick("Notifications from Capri", "Notifications de Capri"), has(Manifest.permission.POST_NOTIFICATIONS), Lang.pick("Reminders, and the notice that shows while she works.", "Les rappels, et l'avis affiché pendant qu'elle agit.")) {
            if (has(Manifest.permission.POST_NOTIFICATIONS)) go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) else requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        f.section(Lang.pick("POWERS", "POUVOIRS"))
        door(Lang.pick("Hands on screen (accessibility)", "Mains sur l'écran (accessibilité)"), CapriAccessibility.on(),
            Lang.pick("Read the screen when you ask, press back/home, and do jobs in apps. Never passwords. Turn it off in Accessibility > Capri.", "Lire l'écran quand tu demandes, appuyer sur Retour/Accueil, faire des tâches dans les apps. Jamais les mots de passe. Se coupe dans Accessibilité > Capri.")) { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        door(Lang.pick("Read notifications", "Lire les notifications"), NotificationWatcher.granted(this),
            Lang.pick("Summaries when you ask, and watches. Kept in memory for a day, never sent by themselves.", "Résumés quand tu demandes, et surveillances. Gardées en mémoire un jour, jamais envoyées d'elles-mêmes.")) {
            go(if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this, NotificationWatcher::class.java).flattenToString()) else Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        val assistant = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        door(Lang.pick("Default assistant", "Assistant par défaut"), assistant, Lang.pick("Long-press home or power opens Capri. Android asks separately whether she may use the screen's text and a screenshot.", "Un appui long sur Accueil ou Marche ouvre Capri. Android demande à part si elle peut utiliser le texte et la capture de l'écran.")) { go(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
        door(Lang.pick("Exact reminders", "Rappels à l'heure exacte"), Alarms.exactAllowed(this), Lang.pick("Off: reminders can be up to 10 minutes late.", "Coupé : les rappels peuvent avoir jusqu'à 10 min de retard.")) { Alarms.askExact(this) }
        door(Lang.pick("Do Not Disturb", "Ne pas déranger"), getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted, Lang.pick("So she can turn it on or off when you ask.", "Pour l'activer ou la couper quand tu demandes.")) { go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        door("Contacts", has(Manifest.permission.READ_CONTACTS), Lang.pick("\"Appelle Maman\" finds the number. Nothing leaves the phone.", "« Appelle Maman » trouve le numéro. Rien ne quitte le téléphone.")) { if (has(Manifest.permission.READ_CONTACTS)) go(details) else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 3) }
        val pm = getSystemService(PowerManager::class.java)
        door(Lang.pick("No battery limits", "Sans limite de batterie"), pm.isIgnoringBatteryOptimizations(packageName), Lang.pick("Keeps her bubble and reminders alive on HiOS.", "Garde sa bulle et ses rappels en vie sur HiOS.")) {
            @Suppress("BatteryLife") go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        f.section(Lang.pick("YOUR RULES", "TES RÈGLES"), Security.Presets[Security.preset])
        f.note(Security.PresetLines[Security.preset])
        f.note(Lang.pick("Sending, paying, deleting, sharing your location and wiping her memory always ask first, in every preset.", "Envoyer, payer, supprimer, partager ta position et effacer sa mémoire demandent toujours d'abord, quel que soit le préréglage."))
        f.buttons(Lang.pick("Change in Settings", "Changer dans les Réglages") to { startActivity(Intent(this, SettingsActivity::class.java)) },
            Lang.pick("Stop everything", "Tout arrêter") to { capri.interrupt(); OverlayService.stop(this); capri.prefs.heyCapri = false; capri.apply(); build() })

        tecno(f, intent.getBooleanExtra("tecno", false))
        setContentView(f.scroll)
        padForBars(f.scroll)
        if (intent.getBooleanExtra("tecno", false)) f.scroll.post { f.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    // Optimiser Capri sur TECNO (HiOS): what keeps a helper app alive and allowed there. Menu names vary between HiOS
    // versions, so each step says what to look for and has a button for the closest page Android can open.
    private fun tecno(f: Form, open: Boolean) {
        f.section(Lang.pick("TUNE CAPRI FOR TECNO", "OPTIMISER CAPRI SUR TECNO"), "HiOS · Android 16")
        f.note(Lang.pick("1. Installed from an APK? Android 15+ blocks accessibility and notification access for it until you open App info > ⋮ (top right) > \"Allow restricted settings\", then turn them on.",
            "1. Installée depuis un APK ? Android 15+ bloque l'accessibilité et l'accès aux notifications tant que tu n'ouvres pas Infos de l'appli > ⋮ (en haut à droite) > « Autoriser les paramètres restreints », puis tu les actives."), p.text)
        f.buttons(Lang.pick("App info", "Infos de l'appli") to { go(details) })
        f.note(Lang.pick("2. Battery: set Capri to \"No restrictions\" (or \"Don't optimize\"), and turn off any battery-saver rule for her in Phone Master / Power settings.",
            "2. Batterie : mets Capri sur « Aucune restriction » (ou « Ne pas optimiser »), et coupe toute règle d'économie pour elle dans Phone Master / les réglages d'alimentation."), p.text)
        f.buttons(Lang.pick("Battery settings", "Réglages batterie") to { @Suppress("BatteryLife") go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })
        f.note(Lang.pick("3. Auto-start: in Phone Master (or Settings > App management), allow Capri to start by itself, so reminders come back after a restart.",
            "3. Démarrage auto : dans Phone Master (ou Réglages > Gestion des applis), autorise Capri à démarrer seule, pour que les rappels reviennent après un redémarrage."), p.text)
        f.note(Lang.pick("4. Recent apps: pull Capri's card down (or tap its lock) so cleaning recent apps doesn't close her bubble.",
            "4. Applis récentes : tire la carte de Capri vers le bas (ou touche son cadenas) pour que le nettoyage ne ferme pas sa bulle."), p.text)
        f.note(Lang.pick("5. Offline voice: in Google's speech settings, download French for offline recognition, and in Text-to-speech, download the French voice.",
            "5. Voix hors ligne : dans les réglages vocaux de Google, télécharge le français pour la reconnaissance hors ligne, et dans Synthèse vocale, la voix française."), p.text)
        f.buttons(Lang.pick("Voice input", "Saisie vocale") to { go(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }, Lang.pick("Text-to-speech", "Synthèse vocale") to { go(Intent("com.android.settings.TTS_SETTINGS")) })
        f.note(Lang.pick("6. Performance: the Helio G99 handles her easily. Use the POWER profile in her settings: Saver turns off Hey Capri and animations for long days.",
            "6. Performances : le Helio G99 la fait tourner sans mal. Utilise le profil ÉNERGIE dans ses réglages : Économie coupe Hey Capri et les animations pour les longues journées."), p.text)
        if (open) f.note(Lang.pick("Capri never needs root, and never asks you to turn off Android's protections.", "Capri n'a jamais besoin du root, et ne te demande jamais de couper les protections d'Android."), p.cyan)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); build() }
}
