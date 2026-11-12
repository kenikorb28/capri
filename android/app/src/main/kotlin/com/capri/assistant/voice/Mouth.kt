package com.capri.assistant.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.capri.assistant.engine.Capri
import com.capri.assistant.engine.Line
import com.capri.core.Http
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.Voice
import java.io.File
import java.util.Locale
import kotlin.math.log10

// Her voice: the phone's own text-to-speech (works offline once the voice is downloaded), or a cloud voice they pick
// (ElevenLabs, Fish Audio, or any OpenAI-style speech server such as Kokoro on their PC), which only gets the words she
// says. If a cloud voice fails she says it with the phone's voice and tells them why once.
class Mouth(private val capri: Capri) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    @Volatile private var done: ((Boolean) -> Unit)? = null
    @Volatile private var talk = 0
    private val barge = BargeIn(capri)
    private var focus: AudioFocusRequest? = null
    private var warned = false
    // Set once a paid Fish Audio model was refused for lack of credit: her next lines go straight to the free one.
    @Volatile private var fishFell = false

    val speaking: Boolean get() = done != null

    // A test, or a changed voice setting, makes her say again what goes wrong instead of only the first time.
    fun retell() { warned = false; fishFell = false }

    private fun locale(): Locale = when (capri.prefs.language) {
        "French" -> Locale.FRANCE; "English" -> Locale.US; "Spanish" -> Locale("es", "ES"); "German" -> Locale.GERMANY; "Portuguese" -> Locale("pt", "BR"); "Polish" -> Locale("pl", "PL"); "Italian" -> Locale.ITALY; else -> Locale.getDefault()
    }

    fun configure() {
        if (capri.prefs.voice != "system") return
        val t = tts
        if (t == null) { start(); return }
        if (ready) tune(t)
    }

    private fun start(then: (() -> Unit)? = null) {
        pending = then
        ready = false
        tts = TextToSpeech(capri.app) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.let { tune(it) }
            val go = pending; pending = null
            if (ready) go?.invoke() else if (go != null) { capri.line(Line.SYS, Lang.pick("The phone's voice isn't working. Check Text-to-speech in Android settings.", "La voix du téléphone ne marche pas. Vérifie « Synthèse vocale » dans les réglages Android.")); finished(talk) }
        }
    }

    private fun tune(t: TextToSpeech) {
        try {
            val loc = locale()
            t.language = loc
            val name = capri.prefs.systemVoice
            if (name.isNotEmpty()) t.voices?.firstOrNull { it.name == name }?.let { t.voice = it }
            t.setPitch(0.7f + 0.1f * capri.prefs.pitch.coerceIn(0, 10))
            t.setSpeechRate(capri.prefs.speed.coerceIn(50, 200) / 100f)
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        } catch (_: Exception) { }
    }

    fun voices(): List<String> = try { tts?.voices?.filter { it.locale.language == locale().language }?.map { it.name }?.sorted() ?: emptyList() } catch (e: Exception) { emptyList() }

    // Speaks, and calls back once: false when she finished, true when she was cut off.
    fun say(text: String, onDone: (Boolean) -> Unit) {
        quiet()
        val id = ++talk
        done = onDone
        val kind = capri.prefs.voice
        val words = Voice.sayable(if (kind == "fish") text else Voice.spoken(text, false)).trim()
        if (words.isEmpty()) { finished(id); return }
        duck(true)
        if (capri.prefs.bargeIn) barge.start()
        if (kind == "system" || kind == "off") system(words, id) else cloud(words, id, kind)
    }

    private fun system(words: String, id: Int) {
        val t = tts
        if (t == null || !ready) { start { system(words, id) }; return }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == "capri-$id-last") capri.main.post { finished(id) } }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { capri.main.post { finished(id) } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {}
        })
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, capri.prefs.volume.coerceIn(0, 100) / 100f) }
        val pieces = if (words.length > 3000) Voice.pieces(words, 1500) else listOf(words)
        for ((i, p) in pieces.withIndex()) t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, if (i == pieces.size - 1) "capri-$id-last" else "capri-$id-$i")
    }

    private fun cloud(words: String, id: Int, kind: String) {
        capri.io.execute {
            val file = try { File(capri.app.cacheDir, "voice-$id.mp3").apply { writeBytes(fetch(words, kind)) } } catch (e: Exception) {
                capri.log("voice", kind + ": " + (e.message ?: "?"))
                capri.main.post {
                    if (id != talk) return@post
                    if (!warned) { warned = true; capri.line(Line.SYS, Lang.pick("My cloud voice failed: ", "Ma voix cloud a échoué : ") + (e.message ?: "?").trim().trimEnd('.') + Lang.pick(". I'm using the phone's voice.", ". J'utilise la voix du téléphone.")) }
                    system(words, id)
                }
                return@execute
            }
            capri.main.post {
                if (id != talk) { file.delete(); return@post }
                try {
                    val mp = MediaPlayer()
                    player = mp
                    mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    mp.setDataSource(file.path)
                    val v = capri.prefs.volume.coerceIn(0, 100) / 100f
                    mp.setVolume(v, v)
                    mp.setOnCompletionListener { file.delete(); finished(id) }
                    mp.setOnErrorListener { _, _, _ -> file.delete(); finished(id); true }
                    mp.setOnPreparedListener { if (id == talk) it.start() }
                    mp.prepareAsync()
                } catch (e: Exception) { file.delete(); system(words, id) }
            }
        }
    }

    private fun fetch(words: String, kind: String): ByteArray {
        val p = capri.prefs
        val http = capri.http
        fun check(r: com.capri.core.HttpReply): ByteArray {
            if (r.status >= 400) throw IllegalStateException("HTTP " + r.status + (if (r.status == 401) Lang.pick(", key refused", ", clé refusée") else ""))
            if (r.body.size < 200) throw IllegalStateException(Lang.pick("no sound came back", "aucun son reçu"))
            return r.body
        }
        when (kind) {
            "elevenlabs" -> {
                val key = p.elevenKey ?: throw IllegalStateException(Lang.pick("no ElevenLabs key", "pas de clé ElevenLabs"))
                val voice = p.elevenVoice.ifEmpty { throw IllegalStateException(Lang.pick("no ElevenLabs voice picked", "aucune voix ElevenLabs choisie")) }
                // The saver profile asks for a lighter stream: smaller downloads and less decoding.
                val format = if (p.power == "saver") "mp3_22050_32" else "mp3_44100_128"
                val url = "https://api.elevenlabs.io/v1/text-to-speech/" + java.net.URLEncoder.encode(voice, "UTF-8") + "?output_format=" + format
                val headers = mapOf("xi-api-key" to key, "Content-Type" to "application/json", "Accept" to "audio/mpeg")
                val r = http.request("POST", url, headers, Json.write(mapOf("text" to words, "model_id" to p.elevenModel.ifEmpty { "eleven_v4_turbo" })).toByteArray(), 45000)
                // A model this account can't use shouldn't cost her her voice, the desktop's way.
                if (r.status == 400 || r.status == 404 || r.status == 422) return check(http.request("POST", url, headers, Json.write(mapOf("text" to words, "model_id" to "eleven_flash_v2_5")).toByteArray(), 45000))
                return check(r)
            }
            "fish" -> {
                val key = p.fishKey ?: throw IllegalStateException(Lang.pick("no Fish Audio key", "pas de clé Fish Audio"))
                // The request is built by her engine (core/Fish.kt): the same call on every device, and the one her tests send.
                fun ask(model: String): com.capri.core.HttpReply {
                    val call = com.capri.core.Fish.call(key, words, p.fishVoice, model, p.speed, p.power == "saver")
                    return http.request("POST", call.url, call.headers, call.body, 60000)
                }
                var model = if (fishFell) com.capri.core.Fish.Free else com.capri.core.Fish.modelName(p.fishModel)
                var r = ask(model)
                // A paid model on an account with no credit shouldn't cost her her voice: the free one says the line, and she tells them once.
                if (com.capri.core.Fish.retryFree(r.status, model)) {
                    val paid = model
                    model = com.capri.core.Fish.Free
                    r = ask(model)
                    if (r.status < 400 && !fishFell) {
                        fishFell = true
                        capri.main.post { capri.line(Line.SYS, Lang.pick("Fish Audio wants credit for $paid, so I'm speaking with its free model (${com.capri.core.Fish.Free}).", "Fish Audio demande du crédit pour $paid, alors je parle avec son modèle gratuit (${com.capri.core.Fish.Free}).")) }
                    }
                }
                if (r.status >= 400) throw IllegalStateException(com.capri.core.Fish.problem(r.status, r.body, model, key))
                return check(r)
            }
            else -> {
                val base = p.speechUrl.trim().trimEnd('/').let { if (it.endsWith("/audio/speech")) it else if (Regex("/v\\d+$").containsMatchIn(it)) "$it/audio/speech" else "$it/v1/audio/speech" }
                val key = p.speechKey
                if (key != null && !Http.safeForKey(base)) throw IllegalStateException(Lang.pick("a key only goes over https or to your own network", "une clé ne part qu'en https ou vers ton propre réseau"))
                val headers = HashMap<String, String>().apply { put("Content-Type", "application/json"); if (key != null) put("Authorization", "Bearer $key") }
                val body = mapOf("model" to p.speechModel.ifEmpty { "gpt-4o-mini-tts" }, "voice" to p.speechVoice.ifEmpty { "marin" }, "input" to words, "response_format" to "mp3")
                return check(http.request("POST", base, headers, Json.write(body).toByteArray(), 60000))
            }
        }
    }

    private fun duck(on: Boolean) {
        val am = capri.app.getSystemService(AudioManager::class.java)
        if (on) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
            focus = r
            am.requestAudioFocus(r)
        } else focus?.let { am.abandonAudioFocusRequest(it); focus = null }
    }

    private fun finished(id: Int) {
        if (id != talk) return
        barge.stop()
        duck(false)
        try { player?.release() } catch (_: Exception) { }
        player = null
        val d = done; done = null
        d?.invoke(false)
    }

    // Stops her voice without calling anyone back: a new line is about to start.
    private fun quiet() {
        talk++
        try { tts?.stop() } catch (_: Exception) { }
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        barge.stop()
        duck(false)
    }

    fun stop() {
        val d = done
        done = null
        quiet()
        d?.invoke(true)
    }
}

// While she talks: if they start talking over her (louder than her own voice coming back through the echo canceller,
// for about a third of a second), her voice stops and she listens. Off when the microphone isn't allowed here.
class BargeIn(private val capri: Capri) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !capri.micHere() || !AcousticEchoCanceler.isAvailable()) return
        running = true
        capri.wake.pause()
        thread = Thread({
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try { AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 1280) * 2) } catch (e: Exception) { running = false; return@Thread }
            val aec = try { AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true } } catch (e: Exception) { null }
            val buf = ShortArray(320)
            var floor = -50.0; var frames = 0; var loud = 0
            try {
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) break
                    val db = 20 * log10(Wave.rms(buf, n) + 1e-9)
                    frames++
                    if (frames < 15) { floor = maxOf(floor, db); continue }
                    if (db > floor + 14) loud++ else { loud = maxOf(0, loud - 1); floor = 0.97 * floor + 0.03 * db }
                    if (loud >= 15) { running = false; capri.main.post { capri.talkedOver() }; break }
                }
            } catch (_: Exception) {
            } finally {
                try { rec.stop() } catch (_: Exception) { }
                rec.release(); aec?.release()
            }
        }, "capri-barge").apply { isDaemon = true; start() }
    }

    fun stop() {
        if (!running && thread == null) return
        running = false
        thread = null
        capri.wake.resume()
    }
}

object Earcon {
    fun ping(context: Context) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        } catch (_: Exception) { }
    }
}
