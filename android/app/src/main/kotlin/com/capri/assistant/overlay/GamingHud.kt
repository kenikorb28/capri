package com.capri.assistant.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.capri.assistant.engine.Capri
import com.capri.assistant.system.DeviceInfo
import com.capri.assistant.ui.Neon
import com.capri.assistant.ui.Plate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// CAPRI GAMING's small panel over a game: battery, heat, free memory and the time, with her mic. It only reads what any
// app can read; it never touches the game, and FPS isn't readable by apps on Android, so it isn't shown.
@SuppressLint("ViewConstructor")
class GamingHud(context: Context, private val capri: Capri, private val openPanel: () -> Unit, private val moveBy: (Int, Int) -> Unit) : LinearLayout(context) {
    private val p = Neon.palette(context)
    private val font = Neon.font(context)
    private val info = TextView(context)
    private val tick = Runnable { refresh(); schedule() }
    private var lastX = 0f; private var lastY = 0f; private var moved = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Plate(0xCC0A0A10.toInt(), p.pink, Neon.dp(context, 6f).toFloat(), Neon.dp(context, 1f).toFloat(), p.cyan)
        setPadding(Neon.dp(context, 8f), Neon.dp(context, 4f), Neon.dp(context, 6f), Neon.dp(context, 4f))
        info.apply { textSize = 11f; setTextColor(p.text); typeface = font }
        addView(info)
        // 🎙 talk · 💬 her panel · 📊 how the phone is doing · 🔕 Do Not Disturb.
        fun button(label: String, go: () -> Unit) = addView(TextView(context).apply { text = label; textSize = 15f; setPadding(Neon.dp(context, 7f), 0, Neon.dp(context, 2f), 0); setOnClickListener { go() } })
        button("🎙") { capri.listen(conversation = false) }
        button("💬") { openPanel() }
        button("📊") { capri.say(com.capri.core.Lang.pick("How's my phone doing?", "Comment va mon téléphone ?")) }
        button("🔕") { capri.io.execute { val r = com.capri.assistant.system.Dnd.run(context, if (com.capri.assistant.system.Dnd.on(context)) "off" else "on"); capri.main.post { capri.line(com.capri.assistant.engine.Line.SYS, r) } } }
        refresh()
    }

    private fun interval(): Long = when (capri.prefs.power) { "performance" -> 2000L; "saver" -> 10000L; else -> 5000L }
    private fun schedule() { removeCallbacks(tick); postDelayed(tick, interval()) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    fun refresh() {
        val b = DeviceInfo.battery(context)
        val (free, _, low) = DeviceInfo.memory(context)
        val heat = b.celsius?.let { "%.0f°C".format(it) } ?: "--"
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        info.text = "⚡${b.percent}%${if (b.charging) "+" else ""}  ${heat}  RAM ${"%.1f".format(free / 1_073_741_824.0)}G${if (low) "!" else ""}  $clock"
        info.setTypeface(font, if (low || (b.celsius ?: 0.0) >= 43.0) Typeface.BOLD else Typeface.NORMAL)
        info.setTextColor(if (low || (b.celsius ?: 0.0) >= 43.0) p.hot else p.text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastX = e.rawX; lastY = e.rawY; moved = false }
        if (e.actionMasked == MotionEvent.ACTION_MOVE && (Math.abs(e.rawX - lastX) > 12 || Math.abs(e.rawY - lastY) > 12)) { moved = true; return true }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_MOVE) { moveBy((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt()); lastX = e.rawX; lastY = e.rawY }
        return true
    }
}
