package com.capri.core

import kotlin.random.Random

// The thinking half of her animations on the app's own screens: which picture shows at a given moment. The Android
// views (app, ui/Motion.kt) only load the pictures and draw what this says, so all of it is tested off the phone.
object Reel {
    // A strip of equal cells played at its own speed, the way the desktop cuts her art (app/Capri.cs Clip).
    class Clip(val name: String, val count: Int, val fps: Double, val loop: Boolean)

    // A place on the intro's canvas.
    class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    class Intro(val wide: Int, val tall: Int, val frames: List<Box>)

    // The cell showing `elapsed` ms into a clip. A loop wraps around; a one-shot answers -1 once its last cell has had its time.
    fun cell(elapsed: Long, fps: Double, count: Int, loop: Boolean): Int {
        if (count <= 0) return -1
        val i = (elapsed.coerceAtLeast(0) / 1000.0 * fps).toInt()
        return if (i < count) i else if (loop) i % count else -1
    }

    // intro/index.txt: its first line is the canvas size, then one "left top right bottom" line a frame, in the order of
    // the pictures 0.webp, 1.webp… A frame keeps its place in that order, so an index with a bad line is refused whole.
    fun intro(index: String?): Intro? {
        val rows = (index ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { row -> row.split(Regex("\\s+")).map { it.toIntOrNull() } }
        if (rows.size < 2) return null
        val wide = rows[0].getOrNull(0) ?: return null
        val tall = rows[0].getOrNull(1) ?: return null
        if (wide <= 0 || tall <= 0) return null
        val frames = ArrayList<Box>()
        for (row in rows.drop(1)) {
            val box = Box(row.getOrNull(0) ?: return null, row.getOrNull(1) ?: return null, row.getOrNull(2) ?: return null, row.getOrNull(3) ?: return null)
            if (box.right <= box.left || box.bottom <= box.top) return null
            frames.add(box)
        }
        return Intro(wide, tall, frames)
    }

    // Where a box of a wide×tall canvas lands on a w×h screen when the whole canvas is fitted inside it, centred:
    // left, top, right, bottom.
    fun place(wide: Int, tall: Int, box: Box, w: Int, h: Int): FloatArray {
        val scale = minOf(w / wide.toFloat(), h / tall.toFloat())
        val left = (w - wide * scale) / 2f
        val top = (h - tall * scale) / 2f
        return floatArrayOf(left + box.left * scale, top + box.top * scale, left + box.right * scale, top + box.bottom * scale)
    }
}

// Capri on the home screen: which strip she is playing and since when. She idles and blinks, sleeps and wakes with her
// mood, glitches once when something breaks or when she is tapped, and her mouth moves while she speaks. Moods are the
// names of the app's Mood values; `gap` is the wait before her next blink.
class Pose(now: Long, private val gap: () -> Long = { 3500 + Random.nextLong(3000) }) {
    var clip: Reel.Clip = Idle
        private set
    var asleep = false
        private set
    private var started = now
    private var mood = "IDLE"
    private var nextBlink = now + gap()

    private fun play(next: Reel.Clip, now: Long) { clip = next; started = now }

    fun mood(name: String, now: Long) {
        val was = mood
        mood = name
        if (name == "ASLEEP") { if (!asleep) { asleep = true; play(Sleep, now) } }
        else if (asleep) { asleep = false; play(Wake, now) }
        else if (name == "ERROR" && was != "ERROR") play(Glitch, now)
    }

    // True when she reacts: a tap makes her glitch (Capri Mobile's full-size art has no grin of its own). Asleep, she doesn't.
    fun tap(now: Long): Boolean {
        if (asleep) return false
        play(Glitch, now)
        return true
    }

    // The cell of `clip` to draw now. A finished one-shot hands back to her idle, or to her sleep.
    fun cell(now: Long): Int {
        if (clip === Idle && !asleep && now >= nextBlink) { nextBlink = now + gap(); play(Blink, now) }
        val i = Reel.cell(now - started, clip.fps, clip.count, clip.loop)
        if (i >= 0) return i
        play(if (asleep) Sleep else Idle, now)
        return 0
    }

    // The cell of her mouth strip to draw over her, or -1 when her mouth is still. The strip is drawn for her standing
    // face, so it only goes over her idle and her blink.
    fun mouth(now: Long): Int = if (mood == "SPEAKING" && (clip === Idle || clip === Blink)) ((now / MouthMs) % MouthCells).toInt() else -1

    // How long until she needs drawing again.
    fun pause(): Long = if (mood == "SPEAKING") MouthMs else (1000.0 / clip.fps).toLong()

    companion object {
        val Idle = Reel.Clip("idle", 16, 8.0, true)
        val Blink = Reel.Clip("blink", 4, 8.0, false)
        val Glitch = Reel.Clip("glitch", 10, 15.0, false)
        val Sleep = Reel.Clip("sleep", 16, 6.0, true)
        val Wake = Reel.Clip("wake", 13, 15.0, false)
        const val MouthMs = 1000L / 12
        const val MouthCells = 5
    }
}

// Her eyes at the top of the chat: which of her pictures shows now. Open with a blink now and then, moving with her
// voice, glancing around while she thinks or works, a glitch then narrowed while something is broken, closed in her sleep.
class Eyes(now: Long, private val gap: () -> Long = { 2600 + Random.nextLong(3200) }) {
    private var mood = "IDLE"
    private var glitchUntil = 0L
    private var nextBlink = now + gap()

    fun mood(name: String, now: Long) {
        if (name == "ERROR" && mood != "ERROR") glitchUntil = now + GlitchMs
        mood = name
    }

    // The name of the picture in assets/eyes.
    fun look(now: Long): String {
        if (now < glitchUntil) return "glitch"
        return when (mood) {
            "SPEAKING" -> if ((now / TalkMs) % 2 == 0L) "talk" else "talk2"
            "THINKING", "WORKING" -> if ((now / GlanceMs) % 2 == 0L) "look_left" else "look_right"
            "ERROR" -> "annoyed"
            "ASLEEP" -> "blink"
            else -> {
                if (now >= nextBlink + BlinkMs) nextBlink = now + gap()
                if (now >= nextBlink) "blink" else "idle"
            }
        }
    }

    // How long until they need drawing again.
    fun pause(): Long = if (mood == "SPEAKING") TalkMs / 2 else 120L

    companion object {
        const val BlinkMs = 140L
        const val GlitchMs = 700L
        const val TalkMs = 160L
        const val GlanceMs = 700L
    }
}
