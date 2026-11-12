package com.capri.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.capri.assistant.engine.Mood
import com.capri.core.Eyes
import com.capri.core.Pose
import com.capri.core.Reel

// Capri moving on the app's own screens, drawn with Capri Mobile's art (credited in NOTICE.md): her intro when the app
// opens, Capri herself on the home screen, and her eyes at the top of the chat. What shows when is decided in core
// (Reel.kt), where it is tested off the phone; these views only load her pictures and draw them.
object Motion {
    private const val IntroRest = 60_000L
    private var introAt = 0L
    private val strips = HashMap<String, Bitmap>()
    private val eyes = HashMap<String, Bitmap>()

    // Her intro plays when the app is opened, but not twice within a minute.
    @Synchronized
    fun introDue(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (introAt != 0L && now - introAt < IntroRest) return false
        introAt = now
        return true
    }

    private fun load(context: Context, path: String): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }) }
    } catch (e: Exception) { null }

    // Her full-size strips (assets/hero), apart from the bubble's half-size ones.
    fun strip(context: Context, name: String): Bitmap? = synchronized(strips) { strips[name] ?: load(context, "hero/$name.png")?.also { strips[name] = it } }

    fun eye(context: Context, name: String): Bitmap? = synchronized(eyes) { eyes[name] ?: load(context, "eyes/$name.webp")?.also { eyes[name] = it } }

    // The full-size strips are only worth their memory while the home screen shows.
    fun release() { synchronized(strips) { strips.clear() } }
}

// Capri on the home screen: she idles and blinks, her mouth moves while her voice does, she sleeps and wakes with the
// bubble, and she glitches when something breaks or when she is tapped.
@SuppressLint("ViewConstructor")
class HeroView(context: Context, private val p: Palette) : View(context) {
    private val pose = Pose(System.currentTimeMillis())
    // With her animations switched off in the settings, or in the battery saver profile, she stands still.
    var animated = true
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val light = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()

    init {
        isClickable = true
        setOnClickListener { if (animated && pose.tap(System.currentTimeMillis())) { Neon.tick(this); invalidate() } }
    }

    fun mood(m: Mood) { pose.mood(m.name, System.currentTimeMillis()); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Neon.dp(context, 200f)) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        light.shader = RadialGradient(w / 2f, h * 0.93f, (h * 0.5f).coerceAtLeast(1f), (p.pink and 0x00FFFFFF) or 0x66000000, p.pink and 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val cell = if (animated) pose.cell(now) else 0
        val clip = if (animated) pose.clip else Pose.Idle
        val w = width.toFloat()
        val h = height.toFloat()
        // The light she stands in: a flat pool of her pink under her feet.
        canvas.save()
        canvas.scale(1.5f, 0.2f, w / 2f, h * 0.93f)
        canvas.drawCircle(w / 2f, h * 0.93f, h * 0.5f, light)
        canvas.restore()
        val strip = Motion.strip(context, clip.name)
        if (strip != null) {
            val wide = strip.width / clip.count
            val across = h * wide / strip.height
            src.set(cell * wide, 0, (cell + 1) * wide, strip.height)
            dst.set(w / 2f - across / 2f, 0f, w / 2f + across / 2f, h)
            canvas.drawBitmap(strip, src, dst, paint)
            val mouth = if (animated) pose.mouth(now) else -1
            val lips = if (mouth >= 0) Motion.strip(context, "mouth") else null
            if (lips != null) {
                val one = lips.width / Pose.MouthCells
                src.set(mouth * one, 0, (mouth + 1) * one, lips.height)
                canvas.drawBitmap(lips, src, dst, paint)
            }
        }
        if (animated) postInvalidateDelayed(pose.pause())
    }
}

// Her intro, over the whole window when the app opens: Capri Mobile's pictures, each placed where intro/index.txt says
// on the intro's own canvas, which is fitted inside the screen. A tap skips it, and it takes itself away at the end.
@SuppressLint("ViewConstructor")
class IntroView(context: Context, private val ground: Int) : View(context) {
    private class Shot(val picture: Bitmap, val box: Reel.Box, val intro: Reel.Intro)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val veil = Paint()
    private val dst = RectF()
    @Volatile private var shot: Shot? = null
    @Volatile private var stopped = false
    private var leaving = false

    init {
        setBackgroundColor(ground)
        isClickable = true
        setOnClickListener { leave() }
        Thread({ play() }, "capri-intro").start()
    }

    private fun picture(i: Int, into: Bitmap?): Bitmap? {
        val how = BitmapFactory.Options().apply { inScaled = false; inMutable = true }
        if (into != null) {
            try {
                how.inBitmap = into
                val again = context.assets.open("intro/$i.webp").use { BitmapFactory.decodeStream(it, null, how) }
                if (again != null) return again
            } catch (e: IllegalArgumentException) { }
            how.inBitmap = null
        }
        return context.assets.open("intro/$i.webp").use { BitmapFactory.decodeStream(it, null, how) }
    }

    private fun play() {
        try {
            val intro = Reel.intro(context.assets.open("intro/index.txt").bufferedReader().use { it.readText() }) ?: return
            // Three pictures take turns, so the one being decoded into left the screen two frames ago.
            val turns = arrayOfNulls<Bitmap>(3)
            val start = SystemClock.uptimeMillis()
            for ((i, box) in intro.frames.withIndex()) {
                if (stopped) return
                val next = picture(i, turns[i % 3]) ?: return
                turns[i % 3] = next
                val wait = start + i * FrameMs - SystemClock.uptimeMillis()
                if (wait > 0) Thread.sleep(wait)
                shot = Shot(next, box, intro)
                postInvalidate()
            }
            Thread.sleep(FrameMs)
        } catch (e: Throwable) {
            // A picture that won't load or a phone short on memory: no intro, and the app opens as usual.
        } finally {
            post { leave() }
        }
    }

    private fun leave() {
        if (leaving) return
        leaving = true
        stopped = true
        animate().alpha(0f).setDuration(260L).withEndAction { (parent as? ViewGroup)?.removeView(this) }.start()
    }

    private fun melt(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        veil.shader = LinearGradient(x0, y0, x1, y1, ground, ground and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(left, top, right, bottom, veil)
    }

    override fun onDraw(canvas: Canvas) {
        val s = shot ?: return
        val at = Reel.place(s.intro.wide, s.intro.tall, s.box, width, height)
        dst.set(at[0], at[1], at[2], at[3])
        canvas.drawBitmap(s.picture, null, dst, paint)
        // Where her canvas stops short of the screen, its edge melts into the ground instead of cutting off.
        val all = Reel.place(s.intro.wide, s.intro.tall, Reel.Box(0, 0, s.intro.wide, s.intro.tall), width, height)
        if (all[1] > 1f) {
            val soft = (all[3] - all[1]) * 0.1f
            melt(canvas, all[0], all[1] - 1f, all[2], all[1] + soft, 0f, all[1], 0f, all[1] + soft)
            melt(canvas, all[0], all[3] - soft, all[2], all[3] + 1f, 0f, all[3], 0f, all[3] - soft)
        }
        if (all[0] > 1f) {
            val soft = (all[2] - all[0]) * 0.1f
            melt(canvas, all[0] - 1f, all[1], all[0] + soft, all[3], all[0], 0f, all[0] + soft, 0f)
            melt(canvas, all[2] - soft, all[1], all[2] + 1f, all[3], all[2], 0f, all[2] - soft, 0f)
        }
    }

    override fun onDetachedFromWindow() { stopped = true; super.onDetachedFromWindow() }

    // The intro's own speed isn't written anywhere in its files: 15 pictures a second is the speed of her other one-shot clips.
    private companion object { const val FrameMs = 66L }
}

// Her eyes at the top of the chat. Everything drawn in her eight pictures sits between rows 84 and 357 of their 360, so
// that band is what fills the view: give it 4 wide for 3 tall.
class EyesView(context: Context) : View(context) {
    private val eyes = Eyes(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    fun mood(m: Mood) { eyes.mood(m.name, System.currentTimeMillis()); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val picture = Motion.eye(context, eyes.look(System.currentTimeMillis()))
        if (picture != null) {
            src.set(0, picture.height * 84 / 360, picture.width, picture.height * 357 / 360)
            dst.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(picture, src, dst, paint)
        }
        postInvalidateDelayed(eyes.pause())
    }
}
