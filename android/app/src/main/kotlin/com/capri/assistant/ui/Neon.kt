package com.capri.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.capri.assistant.CapriApp
import com.capri.assistant.R

// Her look on a phone, from app/Neon.cs and the 0.7.7 Mainframe: near-black ground with a wine glow, setting names in rose
// on the left, values in cyan on the right, plates with cut corners and a pink-to-cyan edge. A light palette for daylight.
class Palette(
    val ground: Int, val glow: Int, val band: Int, val rule: Int, val rose: Int, val pink: Int, val cyan: Int,
    val text: Int, val faint: Int, val field: Int, val edge: Int, val hot: Int, val onAccent: Int, val dark: Boolean
)

object Neon {
    val Dark = Palette(
        ground = Color.rgb(10, 10, 16), glow = Color.rgb(54, 14, 26), band = Color.rgb(16, 12, 20), rule = Color.rgb(44, 122, 134),
        rose = Color.rgb(255, 93, 102), pink = Color.rgb(255, 42, 109), cyan = Color.rgb(94, 240, 246), text = Color.rgb(236, 236, 241),
        faint = Color.rgb(146, 130, 140), field = Color.rgb(17, 16, 28), edge = Color.rgb(92, 24, 36), hot = Color.rgb(232, 66, 70),
        onAccent = Color.rgb(6, 14, 18), dark = true
    )
    val Light = Palette(
        ground = Color.rgb(246, 241, 236), glow = Color.rgb(255, 214, 222), band = Color.rgb(255, 250, 246), rule = Color.rgb(20, 120, 132),
        rose = Color.rgb(196, 30, 64), pink = Color.rgb(214, 20, 88), cyan = Color.rgb(0, 122, 132), text = Color.rgb(26, 21, 32),
        faint = Color.rgb(112, 96, 106), field = Color.rgb(255, 255, 255), edge = Color.rgb(226, 180, 190), hot = Color.rgb(200, 40, 50),
        onAccent = Color.rgb(255, 255, 255), dark = false
    )

    private var cached: Typeface? = null

    fun font(context: Context): Typeface {
        cached?.let { return it }
        val f = try { context.resources.getFont(R.font.share_tech_mono) } catch (e: Exception) { Typeface.MONOSPACE }
        cached = f
        return f
    }

    fun palette(context: Context): Palette {
        val theme = CapriApp.capri(context).prefs.theme
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (theme == 2 || (theme == 0 && !night)) Light else Dark
    }

    fun dp(context: Context, v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
}

fun Context.dp(v: Int): Int = Neon.dp(this, v.toFloat())

// A plate with clipped corners and a neon edge: her bubbles, buttons and fields.
class Plate(private val fill: Int, private val edge: Int, private val cut: Float, private val stroke: Float, private val edge2: Int? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val c = minOf(cut, b.height() / 2f, b.width() / 2f)
        val h = stroke / 2f
        path.reset()
        path.moveTo(b.left + c, b.top + h)
        path.lineTo(b.right - h, b.top + h)
        path.lineTo(b.right - h, b.bottom - c)
        path.lineTo(b.right - c, b.bottom - h)
        path.lineTo(b.left + h, b.bottom - h)
        path.lineTo(b.left + h, b.top + c)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = fill
        canvas.drawPath(path, paint)
        if (stroke > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            if (edge2 != null) paint.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), edge, edge2, Shader.TileMode.CLAMP)
            else paint.color = edge
            canvas.drawPath(path, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// The section band behind a group of settings, with its thin teal line on top.
class Band(private val fill: Int, private val rule: Int, private val ruleWidth: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        paint.color = fill
        canvas.drawRect(b, paint)
        paint.color = rule
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + ruleWidth, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Screens built in code with her look. Every screen pads itself for the status and navigation bars (edge-to-edge is
// always on for apps targeting Android 16).
open class NeonActivity : Activity() {
    lateinit var p: Palette
    lateinit var font: Typeface
    val capri get() = CapriApp.capri(this)

    // Her screens in front are what Android counts for the microphone rule.
    override fun onResume() { super.onResume(); capri.inFront(true) }
    override fun onPause() { capri.inFront(false); super.onPause() }

    fun neonStart() {
        p = Neon.palette(this)
        font = Neon.font(this)
        window.decorView.setBackgroundColor(p.ground)
        window.insetsController?.setSystemBarsAppearance(
            if (p.dark) 0 else (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS),
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }

    fun padForBars(view: View) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun text(value: CharSequence, size: Float = 15f, color: Int = p.text, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
        setLineSpacing(0f, 1.12f)
    }

    fun button(label: String, accent: Int = p.cyan, filled: Boolean = false, onClick: () -> Unit): TextView = text(label, 14f, if (filled) p.onAccent else accent).apply {
        gravity = Gravity.CENTER
        isAllCaps = true
        letterSpacing = 0.06f
        setPadding(dp(16), dp(11), dp(16), dp(11))
        background = Plate(if (filled) accent else p.field, accent, dp(8).toFloat(), dp(1).toFloat() + 0.5f)
        isClickable = true
        isFocusable = true
        minHeight = dp(44)
        setOnClickListener { onClick() }
    }
}

// A settings page: sections on dark bands, names in rose, values in cyan, OFF/ON pairs and ◁ ▷ choices like her Mainframe.
class Form(private val a: NeonActivity) {
    val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(28)) }
    val scroll = ScrollView(a).apply { addView(column); isFillViewport = true }
    private var band: LinearLayout? = null
    private val p get() = a.p

    private fun target(): LinearLayout = band ?: column

    fun title(text: String, sub: String? = null) {
        column.addView(a.text(text, 26f, p.pink, true).apply { letterSpacing = 0.12f; setPadding(0, a.dp(8), 0, 0) })
        if (sub != null) column.addView(a.text(sub, 13f, p.faint).apply { setPadding(0, a.dp(2), 0, a.dp(8)) })
    }

    fun section(name: String, right: String? = null): LinearLayout {
        val b = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Band(p.band, p.rule, a.dp(1).toFloat())
            setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(a.text(name, 15f, p.cyan, true).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) head.addView(a.text(right, 12f, p.faint))
        b.addView(head)
        column.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = a.dp(14) })
        band = b
        return b
    }

    fun end() { band = null }

    fun note(text: CharSequence, color: Int = p.faint, size: Float = 13f): TextView {
        val t = a.text(text, size, color).apply { setPadding(0, a.dp(4), 0, a.dp(4)) }
        target().addView(t)
        return t
    }

    private fun line(name: String, right: View, sub: String?): LinearLayout {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, a.dp(8), 0, a.dp(6)) }
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(a.text(name, 15f, p.rose), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(right)
        box.addView(row)
        if (sub != null) box.addView(a.text(sub, 12f, p.faint).apply { setPadding(0, a.dp(2), 0, 0) })
        target().addView(box)
        return box
    }

    fun toggle(name: String, value: Boolean, sub: String? = null, change: (Boolean) -> Unit) {
        val pair = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        var on = value
        val off = a.text("OFF", 13f, p.faint).apply { setPadding(a.dp(12), a.dp(7), a.dp(12), a.dp(7)) }
        val onView = a.text("ON", 13f, p.faint).apply { setPadding(a.dp(12), a.dp(7), a.dp(12), a.dp(7)) }
        fun paint() {
            onView.setTextColor(if (on) p.onAccent else p.faint)
            onView.background = Plate(if (on) p.cyan else p.field, if (on) p.cyan else p.edge, a.dp(5).toFloat(), a.dp(1).toFloat())
            off.setTextColor(if (!on) Color.WHITE else p.faint)
            off.background = Plate(if (!on) p.hot else p.field, if (!on) p.hot else p.edge, a.dp(5).toFloat(), a.dp(1).toFloat())
        }
        paint()
        off.setOnClickListener { if (on) { on = false; paint(); change(false) } }
        onView.setOnClickListener { if (!on) { on = true; paint(); change(true) } }
        pair.addView(off)
        pair.addView(onView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = a.dp(6) })
        val box = line(name, pair, sub)
        box.getChildAt(0).setOnClickListener { on = !on; paint(); change(on) }
    }

    fun choice(name: String, options: List<String>, index: Int, sub: String? = null, change: (Int) -> Unit): TextView {
        var at = index.coerceIn(0, maxOf(0, options.size - 1))
        val value = a.text(options.getOrElse(at) { "" }, 15f, p.cyan).apply { gravity = Gravity.CENTER; minWidth = a.dp(96) }
        val left = a.text("◁", 18f, p.cyan).apply { setPadding(a.dp(10), a.dp(4), a.dp(6), a.dp(4)) }
        val right = a.text("▷", 18f, p.cyan).apply { setPadding(a.dp(6), a.dp(4), a.dp(10), a.dp(4)) }
        left.setOnClickListener { if (options.isNotEmpty()) { at = (at - 1 + options.size) % options.size; value.text = options[at]; change(at) } }
        right.setOnClickListener { if (options.isNotEmpty()) { at = (at + 1) % options.size; value.text = options[at]; change(at) } }
        val box = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(left); addView(value); addView(right) }
        line(name, box, sub)
        return value
    }

    fun slider(name: String, min: Int, max: Int, value: Int, format: (Int) -> String, change: (Int) -> Unit) {
        val shown = a.text(format(value), 14f, p.cyan)
        line(name, shown, null)
        val bar = SeekBar(a).apply {
            this.max = max - min
            progress = value - min
            progressTintList = android.content.res.ColorStateList.valueOf(p.pink)
            thumbTintList = android.content.res.ColorStateList.valueOf(p.rose)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { shown.text = format(progress + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { change((s?.progress ?: 0) + min) }
            })
        }
        target().addView(bar)
    }

    fun value(name: String, value: String, sub: String? = null, onClick: (() -> Unit)? = null): TextView {
        val v = a.text(value, 14f, p.cyan).apply { maxWidth = a.dp(210); gravity = Gravity.END }
        val box = line(name, v, sub)
        if (onClick != null) { box.isClickable = true; box.setOnClickListener { onClick() } }
        return v
    }

    fun field(name: String, hint: String, value: String, secret: Boolean = false, action: String = "OK", submit: (String) -> Unit): EditText {
        target().addView(a.text(name, 15f, p.rose).apply { setPadding(0, a.dp(8), 0, a.dp(4)) })
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val edit = EditText(a).apply {
            setText(value)
            this.hint = hint
            setHintTextColor(p.faint)
            setTextColor(p.text)
            typeface = a.font
            textSize = 14f
            isSingleLine = true
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = Plate(p.field, p.edge, a.dp(6).toFloat(), a.dp(1).toFloat())
            setPadding(a.dp(10), a.dp(10), a.dp(10), a.dp(10))
            setOnEditorActionListener { v, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_DONE) { submit(v.text.toString().trim()); true } else false }
        }
        row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(a.button(action) { submit(edit.text.toString().trim()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = a.dp(8) })
        target().addView(row)
        return edit
    }

    fun buttons(vararg items: Pair<String, () -> Unit>, accent: Int = p.cyan) {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, go) in items) row.addView(a.button(label, accent) { go() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = a.dp(8) })
        target().addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row); setPadding(0, a.dp(6), 0, a.dp(4)) })
    }

    fun space(h: Int) { target().addView(View(a), LinearLayout.LayoutParams(1, a.dp(h))) }

    fun add(view: View) { target().addView(view) }
}
