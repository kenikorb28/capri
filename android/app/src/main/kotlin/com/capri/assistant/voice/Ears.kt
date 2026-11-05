package com.capri.assistant.voice

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.capri.assistant.engine.Capri
import com.capri.assistant.engine.Line
import com.capri.assistant.engine.Mood
import com.capri.core.Http
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.obj
import com.capri.core.str
import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

// The phone's speech recognizers, without hers: when Capri is the default assistant, Android points the default
// recognizer at Capri's own service, which forwards to these, so asking for the default here would loop.
object Recognizers {
    fun others(context: Context): List<ComponentName> = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }.filter { it.packageName != context.packageName }

    fun best(context: Context): ComponentName? {
        val all = others(context)
        return all.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" } ?: all.firstOrNull { it.packageName == "com.google.android.tts" } ?: all.firstOrNull()
    }

    fun create(context: Context, onDevice: Boolean): SpeechRecognizer? {
        if (onDevice && android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val c = best(context) ?: return null
        return SpeechRecognizer.createSpeechRecognizer(context, c)
    }
}

// Her ears: the phone's recognizer (or on-device only), or an OpenAI-style transcription server like Whisper on their PC.
// They only ever open when they ask her to listen, say "Hey Capri", or keep talking in a voice chat.
class Ears(private val capri: Capri) {
    private var recognizer: SpeechRecognizer? = null
    @Volatile var listening = false
        private set
    private var misses = 0
    @Volatile private var recording = false

    private fun tag(): String = when (capri.prefs.language) {
        "French" -> "fr-FR"; "English" -> "en-US"; "Spanish" -> "es-ES"; "German" -> "de-DE"; "Portuguese" -> "pt-BR"; "Polish" -> "pl-PL"; "Italian" -> "it-IT"; "Arabic" -> "ar"; else -> java.util.Locale.getDefault().toLanguageTag()
    }

    // Main thread.
    fun listen() {
        cancel()
        if (!capri.hasMic()) { fail(Lang.pick("I don't have permission to use the microphone. Allow it in Capri Security.", "Je n'ai pas la permission d'utiliser le micro. Autorise-le dans Capri Sécurité.")); return }
        capri.wake.pause()
        if (capri.prefs.ears == "whisper") { whisper(); return }
        val sr = Recognizers.create(capri.app, capri.prefs.ears == "device")
        if (sr == null) { fail(Lang.pick("No speech recognizer on this phone. Install Google's app or pick Whisper ears.", "Aucune reconnaissance vocale sur ce téléphone. Installe l'appli Google ou choisis les oreilles Whisper.")); return }
        recognizer = sr
        listening = true
        capri.setMood(Mood.LISTENING)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { capri.setMood(Mood.LISTENING) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { capri.level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { capri.setMood(Mood.THINKING) }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { capri.bubble("partial", it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                finish()
                if (text.isEmpty()) { nothing(); return }
                misses = 0
                capri.heard(text)
            }
            override fun onError(error: Int) {
                finish()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> nothing()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(Lang.pick("I don't have permission to use the microphone.", "Je n'ai pas la permission d'utiliser le micro."))
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                        fail(Lang.pick("Speech recognition needs the internet right now. Try on-device ears in my settings.", "La reconnaissance vocale a besoin d'internet là. Essaie les oreilles « sur l'appareil » dans mes réglages."))
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        fail(Lang.pick("This phone's recognizer doesn't have that language yet. Download it in Google's voice settings.", "La reconnaissance de ce téléphone n'a pas encore cette langue. Télécharge-la dans les réglages vocaux de Google."))
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> fail(Lang.pick("The microphone is busy with another app.", "Le micro est occupé par une autre app."))
                    SpeechRecognizer.ERROR_CLIENT -> capri.setMood(Mood.IDLE)
                    else -> fail(Lang.pick("I couldn't hear that (error $error).", "Je n'ai pas pu entendre (erreur $error)."))
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, capri.app.packageName)
        if (capri.prefs.ears == "device") i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try { sr.startListening(i) } catch (e: Exception) { finish(); fail(Lang.pick("The microphone didn't start: ", "Le micro n'a pas démarré : ") + (e.message ?: "?")) }
    }

    private fun nothing() {
        capri.setMood(Mood.IDLE)
        // In a voice chat, two silences in a row end it, so the microphone doesn't stay on by itself.
        if (capri.voiceChat && ++misses < 2) capri.main.postDelayed({ if (capri.voiceChat && !capri.busy) capri.listen() }, 300)
        else { misses = 0; capri.voiceChat = false }
    }

    private fun fail(said: String) {
        listening = false
        capri.voiceChat = false
        capri.line(Line.SYS, said)
        capri.setMood(Mood.ERROR, said)
        capri.wake.resume()
    }

    private fun finish() {
        listening = false
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        capri.wake.resume()
    }

    fun cancel() {
        recording = false
        if (recognizer != null) { try { recognizer?.cancel() } catch (_: Exception) { }; finish() }
        listening = false
    }

    // ---- Whisper-style ears: their own speech server, which gets only these few seconds of sound

    @SuppressLint("MissingPermission")
    private fun whisper() {
        listening = true
        recording = true
        capri.setMood(Mood.LISTENING)
        capri.io.execute {
            val pcm = try { record() } catch (e: Exception) { null }
            capri.main.post { listening = false; capri.wake.resume() }
            if (pcm == null || pcm.isEmpty()) { capri.main.post { nothing() }; return@execute }
            val text = try { transcribe(pcm) } catch (e: Exception) {
                capri.log("whisper", e.message ?: "?")
                capri.main.post { fail(Lang.pick("My speech server didn't answer: ", "Mon serveur de transcription n'a pas répondu : ") + (e.message ?: "?")) }
                return@execute
            }
            capri.main.post { if (text.isBlank()) nothing() else { misses = 0; capri.heard(text) } }
        }
    }

    // Up to 15 s, ending after 1.2 s of quiet once they've started talking; nothing is kept once it's been sent.
    @SuppressLint("MissingPermission")
    private fun record(): ShortArray {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200) * 2)
        val out = ArrayList<Short>()
        val buf = ShortArray(1600)
        var floor = -50.0; var spoke = false; var quiet = 0
        try {
            rec.startRecording()
            val until = System.currentTimeMillis() + 15000
            while (recording && System.currentTimeMillis() < until) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                var e = 0.0
                for (i in 0 until n) { out.add(buf[i]); e += (buf[i] / 32768.0) * (buf[i] / 32768.0) }
                val db = 10 * log10(e / n + 1e-12)
                capri.level(((db + 60) / 50).toFloat().coerceIn(0f, 1f))
                if (db > floor + 12) { spoke = true; quiet = 0 } else { quiet++; if (!spoke) floor = 0.9 * floor + 0.1 * db }
                if (spoke && quiet >= 12) break
                if (!spoke && out.size > rate * 6) break
            }
        } finally { try { rec.stop() } catch (_: Exception) { }; rec.release() }
        return if (spoke) out.toShortArray() else ShortArray(0)
    }

    private fun transcribe(pcm: ShortArray): String {
        val base = capri.prefs.whisperUrl.trim().trimEnd('/').let { if (it.endsWith("/audio/transcriptions")) it else if (Regex("/v\\d+$").containsMatchIn(it)) "$it/audio/transcriptions" else "$it/v1/audio/transcriptions" }
        val key = capri.prefs.whisperKey
        if (key != null && !Http.safeForKey(base)) throw IllegalStateException(Lang.pick("a key only goes over https or to your own network", "une clé ne part qu'en https ou vers ton propre réseau"))
        val wav = Wave.wav(pcm, 16000)
        val boundary = "capri" + System.nanoTime()
        val body = ByteArrayOutputStream()
        fun part(name: String, value: String) { body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n").toByteArray()) }
        part("model", capri.prefs.whisperModel.ifEmpty { "whisper-1" })
        part("language", tag().substringBefore('-'))
        body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capri.wav\"\r\nContent-Type: audio/wav\r\n\r\n").toByteArray())
        body.write(wav)
        body.write(("\r\n--$boundary--\r\n").toByteArray())
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "multipart/form-data; boundary=$boundary"
        if (key != null) headers["Authorization"] = "Bearer $key"
        val reply = capri.http.request("POST", base, headers, body.toByteArray(), 60000)
        if (reply.status >= 400) throw IllegalStateException("HTTP " + reply.status)
        return Json.parseOrNull(reply.text).obj()?.get("text").str()?.trim() ?: reply.text.trim()
    }
}

object Wave {
    fun wav(pcm: ShortArray, rate: Int): ByteArray {
        val data = pcm.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun i32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
        out.write("RIFF".toByteArray()); i32(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }

    fun rms(buf: ShortArray, n: Int): Double { var e = 0.0; for (i in 0 until n) { val x = buf[i] / 32768.0; e += x * x }; return sqrt(e / maxOf(1, n)) }
}
