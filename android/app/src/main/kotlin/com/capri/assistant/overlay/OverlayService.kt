package com.capri.assistant.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.capri.assistant.CapriApp
import com.capri.assistant.R
import com.capri.assistant.engine.Capri
import com.capri.assistant.engine.CapriListener
import com.capri.assistant.engine.Line
import com.capri.assistant.engine.Mood
import com.capri.assistant.system.Notify
import com.capri.assistant.ui.ListenActivity
import com.capri.assistant.ui.MainActivity
import com.capri.assistant.ui.Neon
import com.capri.assistant.ui.Plate
import com.capri.core.Lang
import com.capri.core.cut
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Her bubble over other apps: a foreground service with its notification always showing (Android's rule, and the
// user's way to stop her), the bubble, her panel, her speech bubble and the gaming HUD. When it was started from her app
// in front it also holds the microphone type, which "Hey Capri" and listening from the bubble need.
class OverlayService : Service(), CapriListener {
    companion object {
        const val ACTION_STOP = "com.capri.assistant.STOP"
        const val ACTION_STOP_JOB = "com.capri.assistant.STOP_JOB"
        const val ACTION_LISTEN = "com.capri.assistant.LISTEN"
        const val ACTION_PANEL = "com.capri.assistant.PANEL"
        const val ACTION_TOGGLE = "com.capri.assistant.TOGGLE"
        private const val ID = 7
        @Volatile var running = false
            private set
        @Volatile var micReady = false
            private set
        @Volatile private var instance: OverlayService? = null

        fun start(context: Context, fromBackground: Boolean = false, action: String? = null) {
            val i = Intent(context, OverlayService::class.java).putExtra("background", fromBackground)
            if (action != null) i.action = action
            context.startForegroundService(i)
        }

        fun stop(context: Context) { context.stopService(Intent(context, OverlayService::class.java)) }

        fun showPanel() { instance?.let { s -> s.main.post { s.panel(true) } } }

        // Her own windows out of the way while something takes a picture of the screen.
        fun <T> hiddenWhile(block: () -> T): T {
            val s = instance ?: return block()
            val latch = CountDownLatch(1)
            s.main.post { s.hide(true); latch.countDown() }
            latch.await(1, TimeUnit.SECONDS)
            Thread.sleep(280)
            try { return block() } finally { s.main.post { s.hide(false) } }
        }
    }

    val main = Handler(Looper.getMainLooper())
    private lateinit var capri: Capri
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: PanelView? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var speech: TextView? = null
    private var hud: GamingHud? = null
    private var hudLp: WindowManager.LayoutParams? = null
    private var probe: View? = null
    private var hiddenBubble = false
    private var fullscreen = false
    private val hideSpeech = Runnable { removeSpeech() }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> capri.wake.screen(false)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { capri.wake.screen(true); if (i.action == Intent.ACTION_USER_PRESENT) capri.io.execute { capri.watches.storage() } }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val pct = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
                    val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    capri.watches.battery(pct, plugged)
                    capri.watches.proactive(pct, plugged)
                }
            }
        }
    }

    private val net = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { capri.watches.wifi(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) }
        override fun onLost(network: Network) { capri.watches.wifi(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        capri = CapriApp.capri(this)
        wm = getSystemService(WindowManager::class.java)
        instance = this
        running = true
        capri.addListener(this)
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_BATTERY_CHANGED) })
        try { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(net) } catch (_: Exception) { }
        // Capri Link: while her bubble runs, a change on their PC reaches her in a moment.
        capri.sync.configure()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground(intent?.getBooleanExtra("background", false) != true)
        // "Arrêter" on the "Capri agit sur ton écran" notice only stops the job; it never brings the bubble up.
        if (intent?.action == ACTION_STOP_JOB) { capri.interrupt(); if (bubble == null) { stopSelf(); return START_NOT_STICKY }; return START_STICKY }
        if (!Settings.canDrawOverlays(this)) { capri.log("overlay", "no overlay permission"); stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_STOP -> { capri.prefs.bubbleOn = false; stopSelf(); return START_NOT_STICKY }
            ACTION_LISTEN -> capri.listen(conversation = capri.prefs.keepListening)
            ACTION_PANEL -> panel(true)
            ACTION_TOGGLE -> { hiddenBubble = !hiddenBubble; bubble?.visibility = if (hiddenBubble) View.GONE else View.VISIBLE; foreground(false) }
        }
        capri.prefs.bubbleOn = true
        if (bubble == null) addBubble()
        updateHud()
        capri.wake.configure()
        return START_STICKY
    }

    // SPECIAL_USE always; the MICROPHONE type only when Android allows it now (started from her app in front, from her
    // notification, or while she's the assistant). Otherwise she listens through a see-through screen instead.
    private fun foreground(wantMic: Boolean) {
        val n = notification()
        if ((wantMic || micReady) && capri.hasMic()) {
            try {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                micReady = true
                return
            } catch (e: Exception) { capri.log("overlay", "microphone type refused: " + (e.message ?: e.javaClass.simpleName)) }
        }
        try { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) } catch (e: Exception) { capri.log("overlay", "foreground refused: " + (e.message ?: "")); stopSelf() }
        micReady = false
    }

    private fun notification(): Notification {
        val listen = PendingIntent.getActivity(this, 11, Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggle = PendingIntent.getService(this, 12, Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 13, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val hey = if (capri.wake.wanted()) Lang.pick(" \"Hey Capri\" is on: the microphone listens for it on the phone only.", " « Hey Capri » activé : le micro l'écoute, sur le téléphone uniquement.") else ""
        return Notification.Builder(this, Notify.BUBBLE)
            .setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle(Lang.pick("Capri is here", "Capri est là"))
            .setContentText(Lang.pick("Tap her bubble to chat, hold it to talk.", "Touche sa bulle pour discuter, garde le doigt dessus pour parler.") + hey)
            .setStyle(Notification.BigTextStyle().bigText(Lang.pick("Tap her bubble to chat, hold it to talk.", "Touche sa bulle pour discuter, garde le doigt dessus pour parler.") + hey))
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(Notification.Action.Builder(null, Lang.pick("Talk", "Parler"), listen).build())
            .addAction(Notification.Action.Builder(null, if (hiddenBubble) Lang.pick("Show", "Afficher") else Lang.pick("Hide", "Masquer"), toggle).build())
            .addAction(Notification.Action.Builder(null, Lang.pick("Stop", "Arrêter"), stop).build())
            .build()
    }

    // ---- the bubble

    private fun size(): Int = Neon.dp(this, floatArrayOf(56f, 70f, 88f)[capri.prefs.bubbleSize.coerceIn(0, 2)])

    private fun addBubble() {
        val s = size()
        val m = resources.displayMetrics
        val lp = WindowManager.LayoutParams(s, s, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (capri.prefs.bubbleX >= 0) capri.prefs.bubbleX else m.widthPixels - s
            y = if (capri.prefs.bubbleY >= 0) capri.prefs.bubbleY else m.heightPixels / 3
        }
        val v = BubbleView(this, object : BubbleView.Events {
            override fun tap() { if (bubble?.asleep == true) { capri.setMood(Mood.IDLE); bubble?.mood(Mood.IDLE) } else panel(panel == null) }
            override fun hold() { capri.listen(conversation = capri.prefs.keepListening) }
            override fun doubleTap() { capri.say(Lang.pick("Look at my screen and explain what you see.", "Regarde mon écran et explique-moi ce que tu vois.")) }
            override fun moveBy(dx: Int, dy: Int) { lp.x = (lp.x + dx).coerceIn(0, resources.displayMetrics.widthPixels - lp.width); lp.y = (lp.y + dy).coerceIn(0, resources.displayMetrics.heightPixels - lp.height); try { wm.updateViewLayout(bubble, lp) } catch (_: Exception) { } ; placeSpeech() }
            override fun dropped() {
                val w = resources.displayMetrics.widthPixels
                val to = if (lp.x + lp.width / 2 < w / 2) 0 else w - lp.width
                bubble?.settle(lp.x, to) { x -> lp.x = x; try { wm.updateViewLayout(bubble, lp) } catch (_: Exception) { } }
                capri.prefs.bubbleX = to; capri.prefs.bubbleY = lp.y
            }
        })
        restyle(v)
        wm.addView(v, lp)
        bubble = v
        bubbleLp = lp
        v.mood(capri.mood)
        addProbe()
    }

    private fun restyle(v: BubbleView) {
        v.alpha = (if (fullscreen && capri.prefs.dimFullscreen) 0.45f else 1f) * capri.prefs.bubbleOpacity.coerceIn(20, 100) / 100f
        v.animated = capri.prefs.animations == 0 && capri.prefs.power != "saver"
        Sprites.trim(capri.prefs.power != "saver")
        bubbleLp?.let { lp -> val s = size(); if (lp.width != s) { lp.width = s; lp.height = s; try { wm.updateViewLayout(v, lp) } catch (_: Exception) { } } }
    }

    // A one-pixel window that only listens for the status bar hiding, so she dims over full-screen games and videos.
    private fun addProbe() {
        val v = View(this)
        val lp = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        v.setOnApplyWindowInsetsListener { _, insets ->
            val full = !insets.isVisible(WindowInsets.Type.statusBars())
            if (full != fullscreen) { fullscreen = full; bubble?.let { restyle(it) } }
            insets
        }
        try { wm.addView(v, lp); probe = v } catch (_: Exception) { }
    }

    fun hide(on: Boolean) {
        val vis = if (on) View.INVISIBLE else View.VISIBLE
        if (!hiddenBubble) { bubble?.visibility = vis; if (!on) bubble?.invalidate() }
        panel?.visibility = vis
        speech?.visibility = vis
        hud?.visibility = vis
    }

    // ---- the panel

    fun panel(show: Boolean) {
        if (!show) {
            panel?.let { try { wm.removeView(it) } catch (_: Exception) { } }
            panel = null
            return
        }
        if (panel != null) return
        removeSpeech()
        val m = resources.displayMetrics
        val full = capri.prefs.panelFull
        val lp = WindowManager.LayoutParams(m.widthPixels - Neon.dp(this, 16f), (m.heightPixels * if (full) 0.82 else 0.46).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = Neon.dp(this@OverlayService, 12f)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val v = PanelView(this, capri, object : PanelView.Host {
            override fun close() = panel(false)
            override fun full(on: Boolean) { lp.height = (resources.displayMetrics.heightPixels * if (on) 0.82 else 0.46).toInt(); try { wm.updateViewLayout(panel, lp) } catch (_: Exception) { } }
            override fun typing(on: Boolean) {}
        })
        wm.addView(v, lp)
        panel = v
        panelLp = lp
    }

    // ---- what she says while the panel is closed: a short bubble next to her

    private fun say(text: String) {
        if (panel != null || hiddenBubble || bubble == null) return
        removeSpeech()
        val p = Neon.palette(this)
        val t = TextView(this).apply {
            this.text = text.cut(220)
            textSize = 14f
            setTextColor(p.text)
            typeface = Neon.font(this@OverlayService)
            setPadding(Neon.dp(context, 12f), Neon.dp(context, 8f), Neon.dp(context, 12f), Neon.dp(context, 8f))
            background = Plate(p.ground, p.pink, Neon.dp(context, 8f).toFloat(), Neon.dp(context, 1f).toFloat(), p.cyan)
            maxWidth = (resources.displayMetrics.widthPixels * 0.72).toInt()
            setOnClickListener { removeSpeech(); panel(true) }
        }
        val lp = WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        speech = t
        try { wm.addView(t, lp) } catch (_: Exception) { speech = null; return }
        placeSpeech()
        main.postDelayed(hideSpeech, 5000L + 45L * text.length.coerceAtMost(220))
    }

    private fun placeSpeech() {
        val t = speech ?: return
        val b = bubbleLp ?: return
        val lp = t.layoutParams as? WindowManager.LayoutParams ?: return
        val w = resources.displayMetrics.widthPixels
        t.post {
            lp.x = if (b.x + b.width / 2 < w / 2) b.x + b.width + Neon.dp(this, 4f) else maxOf(0, b.x - t.width - Neon.dp(this, 4f))
            lp.y = maxOf(0, b.y + b.height / 2 - t.height / 2)
            try { wm.updateViewLayout(t, lp) } catch (_: Exception) { }
        }
    }

    private fun removeSpeech() { main.removeCallbacks(hideSpeech); speech?.let { try { wm.removeView(it) } catch (_: Exception) { } }; speech = null }

    // ---- CAPRI GAMING's HUD

    private fun updateHud(force: String? = null) {
        val want = when (force) { "show" -> true; "hide" -> false; else -> capri.prefs.mode == "gaming" && capri.prefs.gamingHud }
        if (want && hud == null) {
            val lp = WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = Neon.dp(this@OverlayService, 8f); y = Neon.dp(this@OverlayService, 30f) }
            val h = GamingHud(this, capri, { panel(true) }) { dx, dy -> lp.x = (lp.x + dx).coerceAtLeast(0); lp.y = (lp.y + dy).coerceAtLeast(0); try { wm.updateViewLayout(hud, lp) } catch (_: Exception) { } }
            try { wm.addView(h, lp); hud = h; hudLp = lp } catch (_: Exception) { }
        } else if (!want && hud != null) {
            try { wm.removeView(hud) } catch (_: Exception) { }
            hud = null
        }
    }

    // ---- her moods and words

    override fun onMood(mood: Mood, detail: String) {
        bubble?.mood(mood)
        panel?.mood(mood, detail)
    }

    override fun onLine(line: Line) {
        panel?.add(line)
        if (panel == null && line.who == Line.CAPRI) say(line.text)
    }

    override fun onLevel(level: Float) { bubble?.level(level) }

    override fun onBubble(command: String, value: String?) {
        when (command) {
            "wake" -> { bubble?.play(Sprites.Wake); if (capri.mood == Mood.ASLEEP) capri.setMood(Mood.IDLE) }
            "sleep" -> capri.setMood(Mood.ASLEEP)
            "glitch" -> bubble?.play(Sprites.Glitch)
            "animate" -> when (value) { "dance" -> bubble?.play(Sprites.Dance); "stop_dancing", "idle" -> bubble?.stopDancing(); "grin" -> bubble?.play(Sprites.Grin); "blink" -> bubble?.play(Sprites.Blink); else -> bubble?.play(Sprites.Glitch) }
            "restyle", "power" -> { bubble?.let { restyle(it) }; capri.wake.configure() }
            "collapse" -> panel(false)
            "confirm" -> panel?.showConfirm(true) ?: panel(true)
            "mode" -> { panel?.mode(value ?: "normal"); updateHud() }
            "hud" -> updateHud(value)
            "partial" -> panel?.partial(value)
            "working" -> capri.setMood(Mood.WORKING)
            "idle" -> if (capri.mood == Mood.WORKING) capri.setMood(Mood.IDLE)
        }
    }

    override fun onDestroy() {
        running = false
        micReady = false
        instance = null
        capri.removeListener(this)
        capri.wake.configure()
        capri.sync.configure()
        try { unregisterReceiver(screen) } catch (_: Exception) { }
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(net) } catch (_: Exception) { }
        for (v in listOfNotNull(bubble, panel, speech, hud, probe)) try { wm.removeView(v) } catch (_: Exception) { }
        bubble = null; panel = null; speech = null; hud = null; probe = null
        super.onDestroy()
    }
}
