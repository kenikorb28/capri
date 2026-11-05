package com.capri.assistant.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.capri.assistant.engine.Mood
import com.capri.assistant.ui.Neon
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// Her art, cut the way the desktop cuts it (app/Capri.cs Clip): each strip is a row of equal cells, played at its own speed.
class Clip(val name: String, val count: Int, val fps: Double, val loop: Boolean)

object Sprites {
    val Idle = Clip("idle", 16, 8.0, true)
    val Blink = Clip("blink", 4, 8.0, false)
    val Grin = Clip("grin", 16, 8.0, false)
    val Glitch = Clip("glitch", 10, 15.0, false)
    val Sleep = Clip("sleep", 16, 6.0, true)
    val Wake = Clip("wake", 13, 15.0, false)
    val Arrive = Clip("arrive", 14, 15.0, false)
    val Leave = Clip("leave", 12, 15.0, false)
    val Mouth = Clip("mouth", 5, 12.0, true)
    val Dance = Clip("dance", 16, 10.0, true)

    private val cache = HashMap<String, Bitmap>()

    fun strip(context: Context, clip: Clip): Bitmap? = synchronized(cache) {
        cache[clip.name] ?: try {
            context.assets.open("sprites/" + clip.name + ".png").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }) }?.also { cache[clip.name] = it }
        } catch (e: Exception) { null }
    }

    // In the battery saver profile only her idle strip and mouth stay loaded.
    fun trim(keepAll: Boolean) = synchronized(cache) { if (!keepAll) cache.keys.retainAll(setOf("idle", "mouth")) }

    fun cell(strip: Bitmap, clip: Clip, i: Int): Rect { val w = strip.width / clip.count; val k = i.coerceIn(0, clip.count - 1); return Rect(k * w, 0, (k + 1) * w, strip.height) }
}

// The bubble: her, in a round plate with a pink-to-cyan ring that shows her mood. Drag it anywhere (it settles on the
// nearest edge), tap for the panel, hold to talk, double-tap to have her look at the screen.
@SuppressLint("ViewConstructor")
class BubbleView(context: Context, private val events: Events) : View(context) {
    interface Events { fun tap(); fun hold(); fun doubleTap(); fun moveBy(dx: Int, dy: Int); fun dropped() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clipPath = Path()
    private var clip = Sprites.Arrive
    private var frame = 0
    private var started = 0L
    private var mood = Mood.IDLE
    private var level = 0f
    var animated = true
    var asleep = false
        private set
    private var dancing = false
    private var nextBlink = System.currentTimeMillis() + 5000
    private val pal = Neon.palette(context)

    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var moved = false; private var downAt = 0L; private var lastTapAt = 0L
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val holdCheck = Runnable { if (!moved) { held = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); events.hold() } }
    private var held = false
    private val singleTap = Runnable { events.tap() }

    init { play(Sprites.Arrive) }

    fun play(c: Clip) { clip = c; frame = 0; started = System.currentTimeMillis(); if (c == Sprites.Dance) dancing = true; invalidate() }
    fun stopDancing() { dancing = false; play(Sprites.Idle) }

    fun mood(m: Mood) {
        val was = mood
        mood = m
        if (m == Mood.ASLEEP) { asleep = true; play(Sprites.Sleep) }
        else if (asleep) { asleep = false; play(Sprites.Wake) }
        else if (m == Mood.ERROR && was != Mood.ERROR) play(Sprites.Glitch)
        invalidate()
    }

    fun level(v: Float) { level = v; if (mood == Mood.LISTENING) invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = min(w, h) / 2f
        val cx = w / 2f; val cy = h / 2f
        val stroke = r * 0.09f
        // The plate.
        paint.shader = null
        paint.color = Color.argb(235, Color.red(pal.field), Color.green(pal.field), Color.blue(pal.field))
        canvas.drawCircle(cx, cy, r - stroke / 2, paint)
        // Her current frame, clipped to the plate.
        val now = System.currentTimeMillis()
        val strip = Sprites.strip(context, clip)
        if (strip != null) {
            val elapsed = (now - started) / 1000.0
            var i = (elapsed * clip.fps).toInt()
            if (i >= clip.count) {
                if (clip.loop) i %= clip.count else { val next = if (dancing && clip == Sprites.Dance) Sprites.Dance else if (asleep) Sprites.Sleep else Sprites.Idle; play(next); i = 0 }
            }
            frame = i
            val src = Sprites.cell(Sprites.strip(context, clip) ?: strip, clip, frame)
            val tall = h * 1.18f
            val wide = tall * src.width() / src.height()
            clipPath.reset(); clipPath.addCircle(cx, cy, r - stroke, Path.Direction.CW)
            canvas.save(); canvas.clipPath(clipPath)
            val dst = RectF(cx - wide / 2, cy - tall * 0.40f, cx + wide / 2, cy + tall * 0.60f)
            canvas.drawBitmap(Sprites.strip(context, clip) ?: strip, src, dst, paint)
            if (mood == Mood.SPEAKING && clip != Sprites.Glitch) Sprites.strip(context, Sprites.Mouth)?.let { m ->
                canvas.drawBitmap(m, Sprites.cell(m, Sprites.Mouth, ((now / (1000 / 12)) % 5).toInt()), dst, paint)
            }
            canvas.restore()
        }
        // The ring, which tells her mood.
        ring.strokeWidth = stroke
        val (a, b) = when (mood) {
            Mood.LISTENING -> pal.cyan to pal.cyan
            Mood.THINKING -> pal.pink to pal.cyan
            Mood.SPEAKING -> pal.pink to pal.rose
            Mood.WORKING -> Color.rgb(255, 176, 32) to pal.pink
            Mood.ERROR -> pal.hot to pal.hot
            Mood.ASLEEP -> pal.faint to pal.faint
            else -> pal.pink to pal.cyan
        }
        ring.shader = LinearGradient(0f, 0f, w, h, a, b, Shader.TileMode.CLAMP)
        if (mood == Mood.THINKING || mood == Mood.WORKING) {
            val sweep = ((now / 4) % 360).toFloat()
            ring.alpha = 90; canvas.drawCircle(cx, cy, r - stroke / 2, ring)
            ring.alpha = 255; canvas.drawArc(RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2), sweep, 110f, false, ring)
        } else {
            ring.alpha = if (mood == Mood.LISTENING) (150 + 105 * level).toInt().coerceIn(0, 255) else 255
            canvas.drawCircle(cx, cy, r - stroke / 2 + (if (mood == Mood.LISTENING) level * stroke * 0.6f else 0f), ring)
        }
        // Blinks now and then, like on the desktop.
        if (animated && clip == Sprites.Idle && !asleep && now > nextBlink) { nextBlink = now + 4000 + Random.nextLong(3000); play(Sprites.Blink) }
        // Redrawn only as often as something changes: her clip's own frame rate, or the thinking ring's sweep.
        val next = when {
            mood == Mood.THINKING || mood == Mood.WORKING -> 33L
            mood == Mood.SPEAKING -> 1000L / 12
            animated || clip != Sprites.Idle -> (1000.0 / clip.fps).toLong()
            else -> -1L
        }
        if (next > 0) postInvalidateDelayed(next)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; lastX = downX; lastY = downY; moved = false; held = false; downAt = System.currentTimeMillis()
                postDelayed(holdCheck, 480)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) { moved = true; removeCallbacks(holdCheck) }
                if (moved) { events.moveBy((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt()); lastX = e.rawX; lastY = e.rawY }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(holdCheck)
                if (moved) events.dropped()
                else if (!held) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapAt < 300) { removeCallbacks(singleTap); lastTapAt = 0; events.doubleTap() }
                    else { lastTapAt = now; postDelayed(singleTap, 300) }
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(holdCheck)
        }
        return true
    }

    // Settles on the nearest side after a drag.
    fun settle(fromX: Int, toX: Int, step: (Int) -> Unit) {
        ValueAnimator.ofInt(fromX, toX).apply { duration = 220; addUpdateListener { step(it.animatedValue as Int) }; start() }
    }
}
