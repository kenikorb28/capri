package com.capri.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// "Hey Capri" with no model to download and no account: they say it three times, she keeps how it sounds (13 MFCCs every
// 10 ms), and on the phone a light loop compares the start of each burst of speech with those three by dynamic time
// warping. Speech is found by energy against a noise floor that follows the room, so silence costs almost nothing.
class Mfcc(val rate: Int = 16000) {
    val frame = rate * 25 / 1000
    val hop = rate / 100
    private val nfft = 512
    private val nMel = 26
    val coefs = 13
    private val window = DoubleArray(frame) { 0.54 - 0.46 * cos(2 * PI * it / (frame - 1)) }
    private val mel = Array(nMel) { DoubleArray(nfft / 2 + 1) }
    private val dct = Array(coefs) { i -> DoubleArray(nMel) { j -> cos(PI * i * (j + 0.5) / nMel) * sqrt(2.0 / nMel) } }
    private val re = DoubleArray(nfft)
    private val im = DoubleArray(nfft)
    private val logMel = DoubleArray(nMel)
    private val rev = IntArray(nfft)
    private val cosT = DoubleArray(nfft / 2) { cos(-2 * PI * it / nfft) }
    private val sinT = DoubleArray(nfft / 2) { kotlin.math.sin(-2 * PI * it / nfft) }

    init {
        fun toMel(f: Double) = 2595 * log10(1 + f / 700)
        fun toHz(m: Double) = 700 * (Math.pow(10.0, m / 2595) - 1)
        val lo = toMel(100.0); val hi = toMel(min(7600.0, rate / 2.0 - 200))
        val bins = IntArray(nMel + 2) { (((nfft + 1) * toHz(lo + (hi - lo) * it / (nMel + 1))) / rate).toInt() }
        for (m in 0 until nMel) {
            val a = bins[m]; val b = bins[m + 1]; val c = bins[m + 2]
            for (k in a until b) if (b > a) mel[m][k] = (k - a).toDouble() / (b - a)
            for (k in b until c) if (c > b) mel[m][k] = (c - k).toDouble() / (c - b)
        }
        val bitsN = Integer.numberOfTrailingZeros(nfft)
        for (i in 0 until nfft) rev[i] = Integer.reverse(i) ushr (32 - bitsN)
    }

    private fun fft() {
        for (i in 0 until nfft) { val j = rev[i]; if (j > i) { val t = re[i]; re[i] = re[j]; re[j] = t; val u = im[i]; im[i] = im[j]; im[j] = u } }
        var size = 2
        while (size <= nfft) {
            val half = size / 2; val step = nfft / size
            var start = 0
            while (start < nfft) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]; val wi = sinT[k * step]
                    val a = start + k; val b = a + half
                    val tr = re[b] * wr - im[b] * wi; val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                }
                start += size
            }
            size *= 2
        }
    }

    // The frame's loudness in dB full scale; out gets its coefficients.
    fun features(x: FloatArray, start: Int, out: DoubleArray): Double {
        var energy = 0.0
        var prev = if (start > 0) x[start - 1].toDouble() else 0.0
        for (i in 0 until nfft) {
            if (i < frame) {
                val s = x[start + i].toDouble()
                energy += s * s
                re[i] = (s - 0.97 * prev) * window[i]
                prev = s
            } else re[i] = 0.0
            im[i] = 0.0
        }
        fft()
        for (m in 0 until nMel) {
            var e = 0.0
            val f = mel[m]
            for (k in 0..nfft / 2) if (f[k] != 0.0) e += f[k] * (re[k] * re[k] + im[k] * im[k]) / nfft
            logMel[m] = Math.log(max(e, 1e-10))
        }
        for (i in 0 until coefs) { var c = 0.0; for (j in 0 until nMel) c += dct[i][j] * logMel[j]; out[i] = c }
        return 10 * log10(energy / frame + 1e-12)
    }
}

// Speech by loudness against the room's noise floor, which drifts up slowly and falls fast.
class Vad(var margin: Double = 9.0, var floorDb: Double = -55.0) {
    var noise = -60.0
        private set
    fun speech(db: Double): Boolean {
        val loud = db > noise + margin && db > floorDb
        noise = if (db < noise) 0.7 * noise + 0.3 * db else if (!loud) 0.985 * noise + 0.015 * db else noise + 0.0005 * (db - noise)
        return loud
    }
}

object Dtw {
    fun normalize(frames: List<DoubleArray>): Array<DoubleArray> {
        if (frames.isEmpty()) return emptyArray()
        val n = frames[0].size
        val mean = DoubleArray(n)
        for (f in frames) for (i in 0 until n) mean[i] += f[i] / frames.size
        return Array(frames.size) { k -> DoubleArray(n) { i -> frames[k][i] - mean[i] } }
    }

    private fun d(a: DoubleArray, b: DoubleArray): Double { var s = 0.0; for (i in 1 until a.size) { val x = a[i] - b[i]; s += x * x }; return sqrt(s) }

    // How far the start of a segment is from a template, with the segment free to end anywhere from 0.6 to 1.6 times its length.
    fun openEnd(t: Array<DoubleArray>, s: Array<DoubleArray>): Double {
        val n = t.size
        val m = min(s.size, (n * 1.6).toInt() + 1)
        if (n == 0 || m < (n * 0.6).toInt()) return Double.MAX_VALUE
        val band = max(10, n / 2)
        val inf = Double.MAX_VALUE / 4
        var prev = DoubleArray(m + 1) { inf }
        var cur = DoubleArray(m + 1) { inf }
        prev[0] = 0.0
        for (i in 1..n) {
            java.util.Arrays.fill(cur, inf)
            val lo = max(1, i - band); val hi = min(m, i + band)
            for (j in lo..hi) {
                val best = min(prev[j], min(cur[j - 1], prev[j - 1]))
                if (best < inf) cur[j] = d(t[i - 1], s[j - 1]) + best
            }
            val swap = prev; prev = cur; cur = swap
        }
        var best = Double.MAX_VALUE
        for (j in max(1, (n * 0.6).toInt())..m) if (prev[j] < inf) best = min(best, prev[j] / (n + j))
        return best
    }
}

class WakeModel(val templates: List<Array<DoubleArray>>, val threshold: Double) {
    fun toJson(): String = Json.write(mapOf("version" to 1, "threshold" to threshold, "templates" to templates.map { t -> t.map { f -> f.map { Math.round(it * 1000) / 1000.0 } } }))

    companion object {
        fun parse(json: String?): WakeModel? {
            val o = Json.parseOrNull(json).obj() ?: return null
            val th = o["threshold"].num() ?: return null
            val ts = o["templates"].arr()?.map { t -> (t.arr() ?: emptyList()).map { f -> (f.arr() ?: emptyList()).map { it.num() ?: 0.0 }.toDoubleArray() }.toTypedArray() } ?: return null
            return if (ts.size >= 2) WakeModel(ts, th) else null
        }

        // Frames of their "Hey Capri" with the silence around it cut off, or null when it's too short, too long or too quiet.
        fun template(pcm: ShortArray, rate: Int = 16000): Array<DoubleArray>? {
            val mfcc = Mfcc(rate)
            val x = FloatArray(pcm.size) { pcm[it] / 32768f }
            val frames = ArrayList<DoubleArray>()
            val loud = ArrayList<Double>()
            var at = 0
            while (at + mfcc.frame <= x.size) { val f = DoubleArray(mfcc.coefs); loud.add(mfcc.features(x, at, f)); frames.add(f); at += mfcc.hop }
            if (frames.size < 20) return null
            val quiet = loud.sorted()[loud.size / 10]
            val peak = loud.maxOrNull() ?: return null
            if (peak < -45 || peak - quiet < 12) return null
            val gate = quiet + max(6.0, (peak - quiet) * 0.3)
            var first = loud.indexOfFirst { it > gate }
            var last = loud.indexOfLast { it > gate }
            if (first < 0 || last <= first) return null
            first = max(0, first - 3); last = min(frames.size - 1, last + 3)
            val len = last - first + 1
            if (len < 30 || len > 200) return null
            return Dtw.normalize(frames.subList(first, last + 1))
        }

        // The threshold from how their own takes differ: looser with sensitivity up, never below what their takes needed.
        fun train(takes: List<Array<DoubleArray>>, sensitivity: Double = 0.5): WakeModel? {
            if (takes.size < 2) return null
            val ds = ArrayList<Double>()
            for (i in takes.indices) for (j in takes.indices) if (i != j) ds.add(Dtw.openEnd(takes[i], takes[j]))
            val good = ds.filter { it < Double.MAX_VALUE }
            if (good.isEmpty()) return null
            val mean = good.average()
            val worst = good.maxOrNull() ?: mean
            val th = max(worst * 1.05, mean * (1.25 + 0.5 * (sensitivity.coerceIn(0.0, 1.0) - 0.5)))
            return WakeModel(takes, th)
        }
    }
}

// Listens to a stream of 16 kHz samples and says true once when the start of a burst of speech sounds like their "Hey Capri".
// Each burst is cut where its loud part starts, the same way their takes were, and its coefficients are centred on the
// same stretch a template covers, so a command said right after "Hey Capri" doesn't throw the comparison off.
class WakeWord(private val model: WakeModel, private val rate: Int = 16000) {
    private val mfcc = Mfcc(rate)
    private val vad = Vad()
    private val samples = FloatArray(mfcc.frame + 4096)
    private var filled = 0
    private val recent = ArrayDeque<DoubleArray>()
    private val recentDb = ArrayDeque<Double>()
    private var speechRun = 0
    private var silenceRun = 0
    private var inSpeech = false
    private var segment = ArrayList<DoubleArray>()
    private var segmentDb = ArrayList<Double>()
    private var quietAtStart = -60.0
    private var judged = false
    private val longest = model.templates.maxOf { it.size }
    @Volatile var lastScore = 0.0
        private set
    var sensitivityScale = 1.0

    fun reset() { filled = 0; recent.clear(); recentDb.clear(); segment.clear(); segmentDb.clear(); inSpeech = false; judged = false; speechRun = 0; silenceRun = 0 }

    fun accept(pcm: ShortArray, count: Int): Boolean {
        var hit = false
        var i = 0
        while (i < count) {
            val room = samples.size - filled
            val take = min(room, count - i)
            for (k in 0 until take) samples[filled + k] = pcm[i + k] / 32768f
            filled += take
            i += take
            var at = 0
            while (at + mfcc.frame <= filled) {
                val f = DoubleArray(mfcc.coefs)
                val db = mfcc.features(samples, at, f)
                if (frame(f, db)) hit = true
                at += mfcc.hop
            }
            System.arraycopy(samples, at, samples, 0, filled - at)
            filled -= at
        }
        return hit
    }

    private fun frame(f: DoubleArray, db: Double): Boolean {
        recent.addLast(f); recentDb.addLast(db)
        if (recent.size > 6) { recent.removeFirst(); recentDb.removeFirst() }
        val before = vad.noise
        val loud = vad.speech(db)
        if (loud) { speechRun++; silenceRun = 0 } else { silenceRun++; if (silenceRun > 2) speechRun = 0 }
        if (!inSpeech && speechRun >= 3) {
            inSpeech = true
            judged = false
            quietAtStart = before
            segment = ArrayList(recent)
            segmentDb = ArrayList(recentDb)
        } else if (inSpeech) {
            segment.add(f); segmentDb.add(db)
            val enough = segment.size >= (longest * 1.6).toInt() + 10
            val ended = silenceRun >= 22
            if (!judged && (enough || ended)) {
                judged = true
                if (judge()) { inSpeech = false; segment = ArrayList(); segmentDb = ArrayList(); return true }
            }
            if (ended || segment.size > 400) { inSpeech = false; segment = ArrayList(); segmentDb = ArrayList() }
        }
        return false
    }

    private fun judge(): Boolean {
        if (segment.size < 25) return false
        val peak = segmentDb.maxOrNull() ?: return false
        val gate = quietAtStart + max(6.0, (peak - quietAtStart) * 0.3)
        var first = segmentDb.indexOfFirst { it > gate }
        if (first < 0) return false
        first = max(0, first - 3)
        var best = Double.MAX_VALUE
        for (t in model.templates) {
            val n = t.size
            val end = min(segment.size, first + (n * 1.6).toInt() + 1)
            if (end - first < (n * 0.6).toInt()) continue
            val upTo = min(end, first + n)
            val mean = DoubleArray(mfcc.coefs)
            for (k in first until upTo) for (c in mean.indices) mean[c] += segment[k][c] / (upTo - first)
            val s = Array(end - first) { k -> DoubleArray(mfcc.coefs) { c -> segment[first + k][c] - mean[c] } }
            best = min(best, Dtw.openEnd(t, s))
        }
        if (best < Double.MAX_VALUE) lastScore = best
        return best <= model.threshold * sensitivityScale
    }
}
