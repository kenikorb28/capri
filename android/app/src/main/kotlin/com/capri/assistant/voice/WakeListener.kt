package com.capri.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.capri.assistant.engine.Capri
import com.capri.assistant.overlay.OverlayService
import com.capri.core.WakeModel
import com.capri.core.WakeWord
import java.io.File

// "Hey Capri", with their own voice and on the phone only: the microphone runs inside her bubble's foreground service
// (so Android shows its microphone dot the whole time), the sound is compared with their three takes as it comes in and
// is never stored or sent. It runs only when they turned it on, recorded it, and the screen is on (unless they chose
// otherwise), never in the battery saver profile, and steps aside whenever she listens or speaks.
class WakeListener(private val capri: Capri) {
    private val file get() = File(capri.folder, "wake.json")
    @Volatile var model: WakeModel? = null
        private set
    @Volatile private var paused = false
    @Volatile private var screenOn = true
    @Volatile private var running = false
    @Volatile private var gen = 0
    private var thread: Thread? = null
    @Volatile var lastScore = 0.0
        private set
    @Volatile var hits = 0
        private set
    // Its real cost on this phone: processor time used by the listening thread for each second it runs.
    @Volatile var cpuShare = 0.0
        private set

    fun configure() {
        model = try { WakeModel.parse(if (file.exists()) file.readText() else null) } catch (e: Exception) { null }
        if (wanted()) ensure() else halt()
    }

    fun save(m: WakeModel) { file.writeText(m.toJson()); model = m; configure() }
    fun forget() { file.delete(); model = null; halt() }
    val trained: Boolean get() = model != null

    fun wanted(): Boolean = capri.prefs.heyCapri && model != null && capri.hasMic() && OverlayService.micReady &&
        capri.prefs.power != "saver" && (screenOn || capri.prefs.heyScreenOff)

    // Why it isn't listening right now, in their words, for the settings and Capri Device pages.
    fun why(): String? = when {
        !capri.prefs.heyCapri -> "désactivé"
        model == null -> "pas encore enregistré (3 prises)"
        !capri.hasMic() -> "le micro n'est pas autorisé"
        !OverlayService.micReady -> "la bulle doit être lancée depuis l'app pour avoir le micro"
        capri.prefs.power == "saver" -> "coupé en profil Économie"
        !screenOn && !capri.prefs.heyScreenOff -> "écran éteint"
        paused -> "en pause pendant qu'elle écoute ou parle"
        else -> null
    }

    fun screen(on: Boolean) { screenOn = on; if (wanted()) ensure() else halt() }
    fun pause() { paused = true; halt() }
    fun resume() { paused = false; if (wanted()) ensure() }

    private fun ensure() {
        if (paused || running) return
        val m = model ?: return
        running = true
        val my = ++gen
        thread = Thread({ loop(m, my) }, "capri-hey").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    }

    private fun halt() { running = false; gen++; thread = null }

    @SuppressLint("MissingPermission")
    private fun loop(m: WakeModel, my: Int) {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400) * 2) }
            catch (e: Exception) { running = false; capri.log("hey", e.message ?: "AudioRecord"); return }
        val detector = WakeWord(m).apply { sensitivityScale = 0.75 + 0.5 * capri.prefs.heySensitivity.coerceIn(0, 100) / 100.0 }
        val buf = ShortArray(640)
        val cpu0 = android.os.SystemClock.currentThreadTimeMillis()
        val wall0 = android.os.SystemClock.elapsedRealtime()
        var measured = wall0
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { capri.log("hey", "microphone refused"); return }
            while (running && gen == my) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) { Thread.sleep(50); continue }
                if (detector.accept(buf, n)) {
                    lastScore = detector.lastScore
                    hits++
                    if (gen == my) running = false
                    capri.main.post { capri.wakeHeard() }
                    break
                }
                lastScore = detector.lastScore
                val wall = android.os.SystemClock.elapsedRealtime()
                if (wall - measured > 10_000) { measured = wall; cpuShare = (android.os.SystemClock.currentThreadTimeMillis() - cpu0).toDouble() / maxOf(1L, wall - wall0) }
            }
        } catch (e: Exception) {
            capri.log("hey", e.message ?: e.javaClass.simpleName)
        } finally {
            try { rec.stop() } catch (_: Exception) { }
            rec.release()
            if (gen == my) running = false
        }
    }
}
