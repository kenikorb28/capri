package com.capri.assistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.TextView
import com.capri.core.Lang
import com.capri.core.WakeModel
import com.capri.core.WakeWord

// Recording their "Hey Capri": three takes of two and a half seconds, kept only as sound features in wake.json on the
// phone (no audio is saved), then a test take to check she hears it.
class WakeTrainActivity : NeonActivity() {
    private val takes = ArrayList<Array<DoubleArray>>()
    private lateinit var status: TextView
    private lateinit var f: Form
    @Volatile private var recording = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        neonStart()
        capri.wake.pause()
        build()
        if (!capri.hasMic()) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onDestroy() { capri.wake.resume(); super.onDestroy() }

    private fun build() {
        f = Form(this)
        f.title("HEY CAPRI", Lang.pick("Your voice, on this phone only.", "Ta voix, sur ce téléphone uniquement."))
        f.section(Lang.pick("RECORD", "ENREGISTRER"), "${takes.size}/3")
        f.note(Lang.pick("In a quiet room, tap the button and say \"Hey Capri\" the way you'll say it every day. Three times.",
            "Dans un endroit calme, touche le bouton et dis « Hey Capri » comme tu le diras tous les jours. Trois fois."), p.text)
        status = f.note(if (takes.size < 3) Lang.pick("Take ${takes.size + 1} of 3.", "Prise ${takes.size + 1} sur 3.") else Lang.pick("Saved. Test it below.", "Enregistré. Teste-le ci-dessous."), p.cyan, 15f)
        if (takes.size < 3) f.buttons(Lang.pick("🎙 Say Hey Capri", "🎙 Dis Hey Capri") to { take() })
        else f.buttons(Lang.pick("🎙 Test", "🎙 Tester") to { test() }, Lang.pick("Start over", "Recommencer") to { takes.clear(); build() })
        f.note(Lang.pick("Someone else saying \"Hey Capri\" may wake her too: this compares sounds, it doesn't recognize who you are.",
            "Quelqu'un d'autre qui dit « Hey Capri » peut aussi la réveiller : ça compare des sons, ça ne reconnaît pas qui tu es."))
        setContentView(f.scroll)
        padForBars(f.scroll)
    }

    @SuppressLint("MissingPermission")
    private fun record(ms: Int): ShortArray {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400) * 2)
        val out = ShortArray(rate * ms / 1000)
        var at = 0
        try {
            rec.startRecording()
            while (at < out.size) { val n = rec.read(out, at, minOf(1600, out.size - at)); if (n <= 0) break; at += n }
        } finally { try { rec.stop() } catch (_: Exception) { }; rec.release() }
        return out.copyOf(at)
    }

    private fun take() {
        if (recording) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        recording = true
        status.text = Lang.pick("Listening… say it now.", "J'écoute… dis-le maintenant.")
        capri.io.execute {
            val t = try { WakeModel.template(record(2500)) } catch (e: Exception) { null }
            runOnUiThread {
                recording = false
                if (t == null) { status.text = Lang.pick("I didn't catch it clearly (too quiet, too short or too noisy). Try again.", "Je ne l'ai pas bien entendu (trop bas, trop court ou trop de bruit). Réessaie."); return@runOnUiThread }
                takes.add(t)
                if (takes.size == 3) {
                    val model = WakeModel.train(takes, capri.prefs.heySensitivity / 100.0)
                    if (model == null) { takes.clear(); build(); status.text = Lang.pick("Those takes didn't match each other. Let's redo them.", "Ces prises ne se ressemblaient pas. On recommence."); return@runOnUiThread }
                    capri.wake.save(model)
                    capri.prefs.heyCapri = true
                    capri.apply()
                    capri.wake.pause()
                }
                build()
            }
        }
    }

    private fun test() {
        val model = capri.wake.model ?: return
        if (recording) return
        recording = true
        status.text = Lang.pick("Say \"Hey Capri\"…", "Dis « Hey Capri »…")
        capri.io.execute {
            val pcm = try { record(3500) } catch (e: Exception) { ShortArray(0) }
            val w = WakeWord(model).apply { sensitivityScale = 0.75 + 0.5 * capri.prefs.heySensitivity / 100.0 }
            var hit = false
            var i = 0
            while (i < pcm.size) { val n = minOf(640, pcm.size - i); if (w.accept(pcm.copyOfRange(i, i + n), n)) hit = true; i += n }
            // A burst still going at the end of the take gets judged too.
            val tail = ShortArray(16000 / 2)
            if (!hit) hit = w.accept(tail, tail.size)
            runOnUiThread {
                recording = false
                status.text = if (hit) Lang.pick("Heard you! (score %.2f, needs %.2f or less)".format(w.lastScore, model.threshold * w.sensitivityScale), "Je t'ai entendu ! (score %.2f, il faut %.2f ou moins)".format(w.lastScore, model.threshold * w.sensitivityScale))
                    else Lang.pick("Not that time (score %.2f, needs %.2f). Try again, or raise the sensitivity.".format(w.lastScore, model.threshold * w.sensitivityScale), "Pas cette fois (score %.2f, il faut %.2f). Réessaie, ou monte la sensibilité.".format(w.lastScore, model.threshold * w.sensitivityScale))
            }
        }
    }
}
