package com.capri.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.capri.assistant.BuildConfig
import com.capri.assistant.engine.CapriListener
import com.capri.assistant.engine.Line
import com.capri.assistant.engine.Mood
import com.capri.assistant.overlay.OverlayService
import com.capri.assistant.overlay.PanelView
import com.capri.assistant.screen.CapriAccessibility
import com.capri.assistant.assist.NotificationWatcher
import com.capri.core.Lang

// Her home screen: what's connected, the bubble switch, and the way to every page. The first time, a short checklist.
class MainActivity : NeonActivity(), CapriListener {
    private lateinit var form: Form
    private var hero: HeroView? = null

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        neonStart()
        val bubble = intent?.action == "com.capri.assistant.SHOW_BUBBLE"
        // Her intro, over the whole window, when the app is opened by hand.
        if (saved == null && !bubble && moving() && capri.prefs.intro && Motion.introDue())
            (window.decorView as? ViewGroup)?.addView(IntroView(this, Neon.Dark.ground), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (bubble) startBubble()
    }

    // Her animations here follow the same switch and power profile as her bubble's.
    private fun moving() = capri.prefs.animations == 0 && capri.prefs.power != "saver"

    override fun onStart() { super.onStart(); capri.addListener(this) }
    override fun onStop() { capri.removeListener(this); Motion.release(); super.onStop() }
    override fun onMood(mood: Mood, detail: String) { runOnUiThread { hero?.mood(mood) } }

    override fun onResume() { super.onResume(); build() }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.action == "com.capri.assistant.SHOW_BUBBLE") startBubble() }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    private fun build() {
        form = Form(this)
        val c = capri
        // Capri herself at the top: the same view across rebuilds, so her clip doesn't start over each time.
        val her = hero ?: HeroView(this, p).also { hero = it }
        (her.parent as? ViewGroup)?.removeView(her)
        her.animated = moving()
        her.mood(c.mood)
        form.column.addView(her)
        form.title("CAPRI", Lang.pick("Your companion on Android · ", "Ta compagne sur Android · ") + "v" + BuildConfig.VERSION_NAME)
        form.section(Lang.pick("NOW", "MAINTENANT"))
        form.value(Lang.pick("Brain", "Cerveau"), c.brainLabel(), null) { open(SettingsActivity::class.java) }
        val hey = c.wake.why()
        form.value("Hey Capri", if (hey == null) Lang.pick("listening", "à l'écoute") else hey)
        form.value(Lang.pick("Bubble", "Bulle"), if (OverlayService.running) (if (OverlayService.micReady) Lang.pick("on, microphone ready", "active, micro prêt") else Lang.pick("on (talk via the notification)", "active (parler via la notification)")) else Lang.pick("off", "coupée"))
        form.value(Lang.pick("Hands on screen", "Mains sur l'écran"), mark(CapriAccessibility.on()))
        form.value(Lang.pick("Notifications", "Notifications"), mark(NotificationWatcher.granted(this)))
        form.buttons(
            (if (OverlayService.running) Lang.pick("Stop bubble", "Arrêter la bulle") else Lang.pick("Start bubble", "Lancer la bulle")) to { if (OverlayService.running) { c.prefs.bubbleOn = false; OverlayService.stop(this); recreateSoon() } else startBubble() },
            Lang.pick("Talk", "Parler") to { startActivity(Intent(this, ListenActivity::class.java)) },
            Lang.pick("Write", "Écrire") to { open(ChatActivity::class.java) })
        if (!c.prefs.onboarded) checklist()
        form.section(Lang.pick("PAGES", "PAGES"))
        form.buttons(Lang.pick("Settings", "Réglages") to { open(SettingsActivity::class.java) }, Lang.pick("Capri Security", "Capri Sécurité") to { open(PermissionsActivity::class.java) })
        form.buttons(Lang.pick("Memory", "Mémoire") to { open(MemoryActivity::class.java) }, "Capri Device" to { open(DiagnosticsActivity::class.java) })
        form.buttons(Lang.pick("Tools & plugins", "Outils et plugins") to { open(ToolsActivity::class.java) }, Lang.pick("Tune Capri for TECNO", "Optimiser Capri sur TECNO") to { startActivity(Intent(this, PermissionsActivity::class.java).putExtra("tecno", true)) })
        form.section(Lang.pick("ABOUT", "À PROPOS"))
        form.note(Lang.pick("A fan port of Capri (github.com/lachydotmcg/capri-desktop), PolyForm Noncommercial 1.0.0. Her art is by a private artist. Not for sale.",
            "Portage fan de Capri (github.com/lachydotmcg/capri-desktop), licence PolyForm Noncommercial 1.0.0. Son art est d'un artiste privé. Pas à vendre."))
        form.note("Fan work: Capri is an unofficial fan project and is not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors.")
        form.note(Lang.pick("She only looks at your screen, listens or acts when you ask, and every permission can be turned off in Capri Security.",
            "Elle ne regarde ton écran, n'écoute ou n'agit que si tu le demandes, et chaque permission se coupe dans Capri Sécurité."))
        setContentView(form.scroll)
        padForBars(form.scroll)
    }

    private fun recreateSoon() = form.scroll.postDelayed({ build() }, 400)

    private fun checklist() {
        val c = capri
        form.section(Lang.pick("GET STARTED", "POUR COMMENCER"))
        val brain = c.brain.source != null
        val overlay = Settings.canDrawOverlays(this)
        val mic = c.hasMic()
        form.value("1. " + Lang.pick("Connect a brain", "Branche un cerveau"), mark(brain), Lang.pick("A free NVIDIA or Gemini key, or Ollama on your PC.", "Une clé NVIDIA ou Gemini gratuite, ou Ollama sur ton PC.")) { open(SettingsActivity::class.java) }
        form.value("2. " + Lang.pick("Allow the bubble", "Autorise la bulle"), mark(overlay), Lang.pick("Lets her float over other apps.", "Lui permet de flotter par-dessus les apps.")) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        form.value("3. " + Lang.pick("Allow the microphone", "Autorise le micro"), mark(mic), Lang.pick("Only used when you talk to her or turn on Hey Capri.", "Utilisé seulement quand tu lui parles ou si tu actives Hey Capri.")) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        form.value("4. " + Lang.pick("Optional: more powers", "Optionnel : plus de pouvoirs"), "→", Lang.pick("Hands on screen, notifications, default assistant: in Capri Security.", "Mains sur l'écran, notifications, assistant par défaut : dans Capri Sécurité.")) { open(PermissionsActivity::class.java) }
        if (brain && overlay && mic) form.buttons(Lang.pick("Done", "C'est bon") to { c.prefs.onboarded = true; build() })
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        val ask = ArrayList<String>()
        if (!capri.hasMic()) ask.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ask.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ask.isNotEmpty()) { requestPermissions(ask.toTypedArray(), 2); return }
        capri.prefs.bubbleOn = true
        // Started from this screen in front, so Android lets her bubble hold the microphone.
        OverlayService.start(this)
        recreateSoon()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) { if (Settings.canDrawOverlays(this)) { capri.prefs.bubbleOn = true; OverlayService.start(this) } }
        build()
    }

    private fun open(c: Class<*>) = startActivity(Intent(this, c))
}

// The chat on a full screen: the same panel as over other apps, with the whole conversation.
class ChatActivity : NeonActivity(), CapriListener {
    private lateinit var panel: PanelView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        neonStart()
        panel = PanelView(this, capri, object : PanelView.Host {
            override fun close() = finish()
            override fun full(on: Boolean) {}
            override fun typing(on: Boolean) {}
        }, screen = true)
        setContentView(panel)
        padForBars(panel)
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        intent.getStringExtra("say")?.let { capri.say(it) }
    }

    override fun onStart() { super.onStart(); capri.addListener(this); panel.refill() }
    override fun onStop() { capri.removeListener(this); super.onStop() }
    override fun onMood(mood: Mood, detail: String) = panel.mood(mood, detail)
    override fun onLine(line: Line) = panel.add(line)
    override fun onBubble(command: String, value: String?) { when (command) { "partial" -> panel.partial(value); "mode" -> panel.mode(value ?: "normal"); "confirm" -> panel.showConfirm(true) } }
}

// A see-through screen that only listens: Android lets the microphone start because it's in front, and it stays until
// she has heard them, with a small card that says so and a button to stop.
class ListenActivity : NeonActivity(), CapriListener {
    private var started = false
    private lateinit var label: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        p = Neon.palette(this); font = Neon.font(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Plate(p.ground, p.cyan, dp(10).toFloat(), dp(1).toFloat(), p.pink)
            setPadding(dp(16), dp(12), dp(12), dp(12))
        }
        label = text(Lang.pick("Listening…", "J'écoute…"), 16f, p.cyan, true)
        card.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(button(Lang.pick("Stop", "Stop"), p.hot) { capri.stopVoice(); finish() })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM; setPadding(dp(12), 0, dp(12), dp(48)); addView(card) }
        root.setOnClickListener { capri.stopVoice(); finish() }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        capri.addListener(this)
        if (started) return
        if (!capri.hasMic()) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3); return }
        go()
    }

    private fun go() {
        started = true
        // While in front, her bubble's service can take the microphone type too, for Hey Capri and the bubble.
        if (OverlayService.running && !OverlayService.micReady) try { OverlayService.start(this) } catch (_: Exception) { }
        val conversation = intent.getBooleanExtra("conversation", capri.prefs.keepListening)
        capri.voiceChat = conversation
        capri.main.postDelayed({ capri.ears.listen() }, 150)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) go()
        else { capri.line(Line.SYS, Lang.pick("I don't have permission to use the microphone.", "Je n'ai pas la permission d'utiliser le micro.")); finish() }
    }

    override fun onMood(mood: Mood, detail: String) {
        when (mood) {
            Mood.LISTENING -> label.text = Lang.pick("Listening…", "J'écoute…")
            Mood.THINKING -> { label.text = Lang.pick("Got it.", "Compris."); label.postDelayed({ finish() }, 300) }
            else -> if (started && !capri.ears.listening) label.postDelayed({ if (!capri.ears.listening) finish() }, 400)
        }
    }

    override fun onBubble(command: String, value: String?) { if (command == "partial" && value != null) label.text = "« $value »" }

    override fun onPause() { capri.removeListener(this); super.onPause() }
}

// Plain text editors for her own files: personality.md, me.md, memories.md. Her safety lines always stay, whatever is
// written in personality.md.
class TextEditActivity : NeonActivity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        neonStart()
        val path = intent.getStringExtra("file") ?: run { finish(); return }
        val file = java.io.File(path)
        val f = Form(this)
        f.title(intent.getStringExtra("title") ?: file.name, intent.getStringExtra("sub"))
        val edit = android.widget.EditText(this).apply {
            setText(if (file.exists()) file.readText() else (intent.getStringExtra("template") ?: ""))
            setTextColor(p.text); typeface = Typeface.MONOSPACE; textSize = 13f
            gravity = Gravity.TOP
            minLines = 14
            background = Plate(p.field, p.edge, dp(6).toFloat(), dp(1).toFloat())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        f.add(edit)
        f.buttons(Lang.pick("Save", "Enregistrer") to {
            file.parentFile?.mkdirs(); file.writeText(edit.text.toString())
            capri.apply()
            android.widget.Toast.makeText(this, Lang.pick("Saved.", "Enregistré."), android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }, Lang.pick("Cancel", "Annuler") to { finish() })
        setContentView(f.scroll)
        padForBars(f.scroll)
    }
}
