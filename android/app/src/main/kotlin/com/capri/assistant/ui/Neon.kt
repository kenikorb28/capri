package com.capri.assistant.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
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
        soft = CapriApp.capri(context).prefs.look == 0
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (theme == 2 || (theme == 0 && !night)) Light else Dark
    }

    // Néon 2.0 (rounded glass plates, a halo on the edges, motion and touch feedback) or her first look (clipped corners).
    // Chosen in Settings > BULLE > Style and read again by palette() each time a screen or her panel is built.
    @Volatile var soft = true

    fun blend(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * k).toInt()
        return Color.argb(mix(Color.alpha(from), Color.alpha(to)), mix(Color.red(from), Color.red(to)), mix(Color.green(from), Color.green(to)), mix(Color.blue(from), Color.blue(to)))
    }

    // A plate that answers the finger with a ripple in its accent (Néon 2.0 only).
    fun plate(fill: Int, edge: Int, cut: Float, stroke: Float, edge2: Int? = null, touch: Int? = null): Drawable {
        val plate = Plate(fill, edge, cut, stroke, edge2)
        return if (!soft || touch == null) plate else RippleDrawable(ColorStateList.valueOf((touch and 0x00FFFFFF) or (0x44 shl 24)), plate, Plate(Color.WHITE, 0, cut, 0f))
    }

    // A light tap under the finger. Android only plays it when the phone's own touch feedback is on.
    fun tick(view: View, on: Boolean? = null) {
        if (!soft) return
        try {
            view.performHapticFeedback(if (on != null && Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF) else HapticFeedbackConstants.CONTEXT_CLICK)
        } catch (e: Exception) { }
    }

    // Buttons give a little under the finger and spring back.
    fun springy(view: View) {
        if (!soft) return
        fun to(scale: Float, ms: Long) = ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat(View.SCALE_X, scale), PropertyValuesHolder.ofFloat(View.SCALE_Y, scale)).setDuration(ms)
        view.stateListAnimator = StateListAnimator().apply { addState(intArrayOf(android.R.attr.state_pressed), to(0.96f, 90L)); addState(intArrayOf(), to(1f, 160L)) }
    }

    fun dp(context: Context, v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
}

fun Context.dp(v: Int): Int = Neon.dp(this, v.toFloat())

// A plate: her bubbles, buttons and fields. Néon 2.0 rounds it, lights its edge with a soft halo and lays a faint sheen
// on top, like glass; her first look keeps the clipped corners. Shapes and shaders are built once per size, not per frame.
class Plate(private val fill: Int, private val edge: Int, private val cut: Float, private val stroke: Float, private val edge2: Int? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private var line: Shader? = null
    private var sheen: Shader? = null
    private var soft = Neon.soft
    private var shaped = false

    override fun onBoundsChange(bounds: Rect) { shaped = false }

    private fun shape() {
        val b = bounds
        soft = Neon.soft
        shaped = true
        path.reset()
        line = if (edge2 != null) LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), edge, edge2, Shader.TileMode.CLAMP) else null
        sheen = null
        if (soft) {
            val inset = if (stroke > 0) stroke * 1.5f else 0f
            box.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            val r = maxOf(0f, minOf(cut * 1.75f, box.height() / 2f, box.width() / 2f))
            path.addRoundRect(box, r, r, Path.Direction.CW)
            if (Color.alpha(fill) > 0 && Color.luminance(fill) < 0.5f) sheen = LinearGradient(0f, box.top, 0f, box.top + box.height() * 0.6f, Color.argb(22, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        } else {
            val c = minOf(cut, b.height() / 2f, b.width() / 2f)
            val h = stroke / 2f
            path.moveTo(b.left + c, b.top + h)
            path.lineTo(b.right - h, b.top + h)
            path.lineTo(b.right - h, b.bottom - c)
            path.lineTo(b.right - c, b.bottom - h)
            path.lineTo(b.left + h, b.bottom - h)
            path.lineTo(b.left + h, b.top + c)
            path.close()
        }
    }

    override fun draw(canvas: Canvas) {
        if (!shaped || soft != Neon.soft) shape()
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = fill
        canvas.drawPath(path, paint)
        sheen?.let { paint.shader = it; canvas.drawPath(path, paint); paint.shader = null }
        if (stroke > 0) {
            paint.style = Paint.Style.STROKE
            if (soft) {
                // The halo: the same edge again, wider and faint.
                paint.strokeWidth = stroke * 3f
                paint.shader = line
                if (line == null) paint.color = edge
                paint.alpha = 46
                canvas.drawPath(path, paint)
                paint.alpha = 255
            }
            paint.strokeWidth = stroke
            paint.shader = line
            if (line == null) paint.color = edge
            canvas.drawPath(path, paint)
            paint.shader = null
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// What sits behind a group of settings. Néon 2.0: a rounded card you can see her ground through, a hairline all round
// and her teal line fading out along the top. Her first look: a flat band with the thin teal line on top.
class Band(private val fill: Int, private val rule: Int, private val ruleWidth: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var top: Shader? = null

    override fun onBoundsChange(bounds: Rect) { top = null }

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        paint.shader = null
        if (!Neon.soft) {
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawRect(b, paint)
            paint.color = rule
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + ruleWidth, paint)
            return
        }
        val r = ruleWidth * 18f
        val h = ruleWidth / 2f
        box.set(b.left + h, b.top + h, b.right - h, b.bottom - h)
        paint.style = Paint.Style.FILL
        paint.color = (fill and 0x00FFFFFF) or (0xD9 shl 24)
        canvas.drawRoundRect(box, r, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ruleWidth
        paint.color = (rule and 0x00FFFFFF) or (0x40 shl 24)
        canvas.drawRoundRect(box, r, r, paint)
        if (box.width() > r * 2f) {
            if (top == null) top = LinearGradient(box.left + r, 0f, box.right - r, 0f, rule, rule and 0x00FFFFFF, Shader.TileMode.CLAMP)
            paint.shader = top
            paint.strokeWidth = ruleWidth * 2f
            canvas.drawLine(box.left + r, box.top + ruleWidth, box.right - r, box.top + ruleWidth, paint)
            paint.shader = null
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Néon 2.0's ground: her near-black (or daylight) colour with two soft lights, wine at the top left and teal at the
// bottom right.
class Backdrop(private val p: Palette) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var warm: Shader? = null
    private var cool: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        warm = RadialGradient(bounds.left + w * 0.1f, bounds.top + h * 0.04f, w * 1.1f, (p.glow and 0x00FFFFFF) or (0xE6 shl 24), p.glow and 0x00FFFFFF, Shader.TileMode.CLAMP)
        cool = RadialGradient(bounds.left + w, bounds.top + h * 0.96f, w * 0.9f, (p.rule and 0x00FFFFFF) or (0x38 shl 24), p.rule and 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(p.ground)
        warm?.let { paint.shader = it; canvas.drawRect(bounds, paint) }
        cool?.let { paint.shader = it; canvas.drawRect(bounds, paint) }
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

// Néon 2.0's switch: a track that fills with cyan and a thumb that slides across it.
class NeonSwitch(context: Context, private val p: Palette, on: Boolean) : View(context) {
    var on: Boolean = on
        private set
    private var at = if (on) 1f else 0f
    private var slide: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Neon.dp(context, 1f).toFloat()

    init { isClickable = true; isFocusable = true; contentDescription = if (on) "ON" else "OFF" }

    fun set(value: Boolean) {
        on = value
        contentDescription = if (value) "ON" else "OFF"
        slide?.cancel()
        slide = ValueAnimator.ofFloat(at, if (value) 1f else 0f).apply {
            duration = 170L
            interpolator = DecelerateInterpolator()
            addUpdateListener { at = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(Neon.dp(context, 52f), Neon.dp(context, 30f)) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Neon.blend(p.field, p.cyan, at * 0.9f)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = line
        paint.color = Neon.blend(p.edge, p.cyan, at)
        canvas.drawRoundRect(line / 2f, line / 2f, w - line / 2f, h - line / 2f, r, r, paint)
        val x = r + (w - 2f * r) * at
        paint.style = Paint.Style.FILL
        if (at > 0f) { paint.color = Color.argb((at * 60f).toInt(), 255, 255, 255); canvas.drawCircle(x, r, r * 0.95f, paint) }
        paint.color = Neon.blend(p.faint, p.onAccent, at)
        canvas.drawCircle(x, r, r * 0.68f, paint)
    }
}

// Screens built in code with her look. Every screen pads itself for the status and navigation bars (edge-to-edge is
// always on for apps targeting Android 16).
open class NeonActivity : Activity() {
    lateinit var p: Palette
    lateinit var font: Typeface
    val capri get() = CapriApp.capri(this)
    // True while a screen draws itself for the first time: Néon 2.0 lets its cards rise into place once, not at every rebuild.
    var firstBuild = false

    // Her screens in front are what Android counts for the microphone rule.
    override fun onResume() { super.onResume(); capri.inFront(true) }
    override fun onPause() { capri.inFront(false); super.onPause() }

    fun neonStart() {
        p = Neon.palette(this)
        font = Neon.font(this)
        // Néon 2.0: her ground with its two soft lights, screens that fade in, and cards that rise once.
        window.decorView.background = if (Neon.soft) Backdrop(p) else android.graphics.drawable.ColorDrawable(p.ground)
        if (Neon.soft) {
            try { if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out) } catch (e: Exception) { }
            firstBuild = true
            window.decorView.post { firstBuild = false }
        }
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
        val side = dp(if (Neon.soft) 18 else 16)
        gravity = Gravity.CENTER
        isAllCaps = true
        letterSpacing = 0.06f
        setPadding(side, dp(11), side, dp(11))
        // Néon 2.0: a pill with a ripple, an edge that runs from its accent towards her pink, and a little give under the finger.
        background = Neon.plate(if (filled) accent else p.field, accent, dp(if (Neon.soft) 14 else 8).toFloat(), dp(1).toFloat() + 0.5f, if (Neon.soft && !filled) Neon.blend(accent, p.pink, 0.55f) else null, accent)
        isClickable = true
        isFocusable = true
        minHeight = dp(44)
        Neon.springy(this)
        setOnClickListener { Neon.tick(this); onClick() }
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
        // Néon 2.0: a short bar of her two colours under the title.
        if (Neon.soft) column.addView(View(a).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(p.pink, p.cyan)).apply { cornerRadius = a.dp(2).toFloat() } },
            LinearLayout.LayoutParams(a.dp(56), a.dp(3)).apply { topMargin = a.dp(6); bottomMargin = a.dp(4) })
        if (sub != null) column.addView(a.text(sub, 13f, p.faint).apply { setPadding(0, a.dp(2), 0, a.dp(8)) })
    }

    fun section(name: String, right: String? = null): LinearLayout {
        val pad = a.dp(if (Neon.soft) 16 else 12)
        val b = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Band(p.band, p.rule, a.dp(1).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(a.text(name, 15f, p.cyan, true).apply { letterSpacing = 0.14f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) head.addView(a.text(right, 12f, p.faint))
        b.addView(head)
        column.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = a.dp(if (Neon.soft) 16 else 14) })
        // Néon 2.0: the first time a screen shows, its cards rise into place one after the other.
        if (Neon.soft && a.firstBuild) {
            b.alpha = 0f
            b.translationY = a.dp(14).toFloat()
            b.animate().alpha(1f).translationY(0f).setStartDelay(minOf(column.childCount, 8) * 35L).setDuration(240L).start()
        }
        band = b
        return b
    }

    fun end() { band = null }

    fun note(text: CharSequence, color: Int = p.faint, size: Float = 13f): TextView {
        val t = a.text(text, size, color).apply { setPadding(0, a.dp(4), 0, a.dp(4)) }
        // Néon 2.0: long explanations read better in the phone's own sans-serif; names and values keep her mono.
        if (Neon.soft) { t.typeface = Typeface.SANS_SERIF; t.setLineSpacing(0f, 1.2f) }
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
        if (Neon.soft) {
            // Néon 2.0: a switch that slides and lights up, with a tap under the finger.
            val sw = NeonSwitch(a, p, value)
            fun flip() { val now = !sw.on; sw.set(now); Neon.tick(sw, now); change(now) }
            sw.setOnClickListener { flip() }
            line(name, sw, sub).getChildAt(0).setOnClickListener { flip() }
            return
        }
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
        left.setOnClickListener { if (options.isNotEmpty()) { at = (at - 1 + options.size) % options.size; value.text = options[at]; Neon.tick(value); change(at) } }
        right.setOnClickListener { if (options.isNotEmpty()) { at = (at + 1) % options.size; value.text = options[at]; Neon.tick(value); change(at) } }
        val box = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(left); addView(value); addView(right) }
        // Néon 2.0: the choice sits in its own rounded chip.
        if (Neon.soft) { box.background = Plate(p.field, p.edge, a.dp(11).toFloat(), a.dp(1).toFloat()); box.setPadding(a.dp(2), a.dp(3), a.dp(2), a.dp(3)) }
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
        // Néon 2.0: the field being typed in lights up in her two colours.
        if (Neon.soft) edit.setOnFocusChangeListener { v, has -> v.background = Plate(p.field, if (has) p.cyan else p.edge, a.dp(6).toFloat(), a.dp(1).toFloat(), if (has) p.pink else null) }
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
