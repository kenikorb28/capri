package com.capri.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.capri.core.Persona
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// API keys, encrypted with a key that never leaves the phone's Android Keystore. Like the desktop, a key is never written
// in plain text, never logged, and only its last four characters are ever shown.
class Secrets(context: Context) {
    private val sp = context.getSharedPreferences("capri_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrBlank()) { sp.edit().remove(name).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        sp.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val saved = sp.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    fun has(name: String) = sp.contains(name)

    companion object { private const val ALIAS = "capri_secrets_v1" }
}

// Her settings, with the desktop's defaults where there is one (Trusted security, Memory Short, replies Short).
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("capri", Context.MODE_PRIVATE)
    val secrets = Secrets(context)

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun put(k: String, v: Any?) {
        val e = sp.edit()
        when (v) { null -> e.remove(k); is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Boolean -> e.putBoolean(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v) }
        e.apply()
    }

    // BRAIN MODE: local (only Ollama or a server on their PC, plus her offline commands), hybrid (their PC first, a cloud
    // key when the PC doesn't answer), cloud (a free or paid key). Nothing is ever called "offline" unless it is.
    var brainMode: String get() = s("brainMode", "cloud"); set(v) = put("brainMode", v)
    // Which cloud key: 1 MEDIUM INTELLIGENCE (free keys like NVIDIA or Gemini), 2 MAX INTELLIGENCE.
    var tier: Int get() = i("tier", 1).coerceIn(1, 2); set(v) = put("tier", v)
    var server: String get() = s("server", ""); set(v) = put("server", v)
    var serverModel: String get() = s("serverModel", ""); set(v) = put("serverModel", v)
    var mediumKey: String? get() = secrets.get("mediumKey"); set(v) = secrets.put("mediumKey", v)
    var mediumModel: String get() = s("mediumModel", ""); set(v) = put("mediumModel", v)
    var maxKey: String? get() = secrets.get("maxKey"); set(v) = secrets.put("maxKey", v)
    var maxModel: String get() = s("maxModel", ""); set(v) = put("maxModel", v)
    var stayOnModel: Boolean get() = b("stayOnModel", false); set(v) = put("stayOnModel", v)
    var wordiness: Int get() = i("wordiness", 0); set(v) = put("wordiness", v)
    var recall: Int get() = i("recall", 0); set(v) = put("recall", v)
    var replyLength: Int get() = i("replyLength", 0); set(v) = put("replyLength", v)
    var followUps: Boolean get() = b("followUps", false); set(v) = put("followUps", v)
    var ownSteps: Boolean get() = b("ownSteps", true); set(v) = put("ownSteps", v)

    // VOICE: off, system, elevenlabs, fish, openai (any OpenAI-style speech server, Kokoro-FastAPI on their PC included).
    var voice: String get() = s("voice", "system"); set(v) = put("voice", v)
    var systemVoice: String get() = s("systemVoice", ""); set(v) = put("systemVoice", v)
    var pitch: Int get() = i("pitch", 3); set(v) = put("pitch", v)
    var speed: Int get() = i("speed", 110); set(v) = put("speed", v)
    var volume: Int get() = i("volume", 80); set(v) = put("volume", v)
    var elevenKey: String? get() = secrets.get("elevenKey"); set(v) = secrets.put("elevenKey", v)
    var elevenVoice: String get() = s("elevenVoice", ""); set(v) = put("elevenVoice", v)
    var elevenModel: String get() = s("elevenModel", ""); set(v) = put("elevenModel", v)
    var fishKey: String? get() = secrets.get("fishKey"); set(v) = secrets.put("fishKey", v)
    var fishVoice: String get() = s("fishVoice", ""); set(v) = put("fishVoice", v)
    var fishModel: String get() = s("fishModel", "s2.1-pro-free"); set(v) = put("fishModel", v)
    var speechUrl: String get() = s("speechUrl", "https://api.openai.com/v1"); set(v) = put("speechUrl", v)
    var speechKey: String? get() = secrets.get("speechKey"); set(v) = secrets.put("speechKey", v)
    var speechModel: String get() = s("speechModel", "gpt-4o-mini-tts"); set(v) = put("speechModel", v)
    var speechVoice: String get() = s("speechVoice", "marin"); set(v) = put("speechVoice", v)
    var cues: Boolean get() = b("cues", true); set(v) = put("cues", v)
    var greeting: Boolean get() = b("greeting", true); set(v) = put("greeting", v)

    // EARS: system (the phone's recognizer), device (on-device only, Android 12+), whisper (an OpenAI-style transcription server).
    var ears: String get() = s("ears", "system"); set(v) = put("ears", v)
    var whisperUrl: String get() = s("whisperUrl", "https://api.openai.com/v1"); set(v) = put("whisperUrl", v)
    var whisperKey: String? get() = secrets.get("whisperKey"); set(v) = secrets.put("whisperKey", v)
    var whisperModel: String get() = s("whisperModel", "whisper-1"); set(v) = put("whisperModel", v)
    var keepListening: Boolean get() = b("keepListening", true); set(v) = put("keepListening", v)
    // Saying "attends" or just talking over her stops her voice (needs the phone's echo canceller to hear past her).
    var bargeIn: Boolean get() = b("bargeIn", true); set(v) = put("bargeIn", v)

    // HEY CAPRI: their own voice, compared on the phone only. Off until they record it; screen-on only by default.
    var heyCapri: Boolean get() = b("heyCapri", false); set(v) = put("heyCapri", v)
    var heySensitivity: Int get() = i("heySensitivity", 50); set(v) = put("heySensitivity", v)
    var heyScreenOff: Boolean get() = b("heyScreenOff", false); set(v) = put("heyScreenOff", v)
    // After "Hey Capri" she says "Oui ?" before listening, instead of only a short beep.
    var wakeSays: Boolean get() = b("wakeSays", false); set(v) = put("wakeSays", v)

    // YOU
    var language: String get() = s("language", Persona.systemLanguage(Locale.getDefault())); set(v) = put("language", v)
    var nickname: String get() = s("nickname", ""); set(v) = put("nickname", v)

    // BUBBLE
    var bubbleStyle: String get() = s("bubbleStyle", "face"); set(v) = put("bubbleStyle", v)
    var bubbleSize: Int get() = i("bubbleSize", 1); set(v) = put("bubbleSize", v)
    var bubbleOpacity: Int get() = i("bubbleOpacity", 92); set(v) = put("bubbleOpacity", v)
    var bubbleX: Int get() = i("bubbleX", -1); set(v) = put("bubbleX", v)
    var bubbleY: Int get() = i("bubbleY", -1); set(v) = put("bubbleY", v)
    var panelHeight: Int get() = i("panelHeight", 0); set(v) = put("panelHeight", v)
    var sleepAfter: Int get() = i("sleepAfter", 2); set(v) = put("sleepAfter", v)
    var animations: Int get() = i("animations", 0); set(v) = put("animations", v)
    var startOnBoot: Boolean get() = b("startOnBoot", false); set(v) = put("startOnBoot", v)
    var dimFullscreen: Boolean get() = b("dimFullscreen", true); set(v) = put("dimFullscreen", v)
    var quiet: Boolean get() = b("quiet", false); set(v) = put("quiet", v)
    var bubbleOn: Boolean get() = b("bubbleOn", false); set(v) = put("bubbleOn", v)

    // MODES and the power profile (performance, normal, saver)
    var mode: String get() = s("mode", "normal"); set(v) = put("mode", v)
    var studyLevel: String get() = s("studyLevel", "normal"); set(v) = put("studyLevel", v)
    var gamingHud: Boolean get() = b("gamingHud", true); set(v) = put("gamingHud", v)
    var power: String get() = s("power", "normal"); set(v) = put("power", v)
    var panelFull: Boolean get() = b("panelFull", false); set(v) = put("panelFull", v)
    // VISION: how big a picture of the screen or a photo is when it goes to a seeing brain (low 960, normal 1280, high 1600 px).
    var visionQuality: String get() = s("visionQuality", "normal"); set(v) = put("visionQuality", v)
    // CAPRI DEVICE: she warns once when the battery gets low or storage nearly full (checked at most once a day).
    var deviceAlerts: Boolean get() = b("deviceAlerts", true); set(v) = put("deviceAlerts", v)
    var storageCheckedAt: Long get() = sp.getLong("storageCheckedAt", 0L); set(v) = put("storageCheckedAt", v)
    // MEMORY: saving what they say about themselves without being asked. Off: only "souviens-toi…" saves.
    var autoLearn: Boolean get() = b("autoLearn", false); set(v) = put("autoLearn", v)
    var clipOffer: Boolean get() = b("clipOffer", true); set(v) = put("clipOffer", v)

    // NOTIFICATIONS: what she keeps (in memory only, never sent anywhere on its own), and whether codes stay hidden.
    var notifKeep: Boolean get() = b("notifKeep", true); set(v) = put("notifKeep", v)
    var notifCodes: Boolean get() = b("notifCodes", false); set(v) = put("notifCodes", v)

    // CAPRI URGENCE: off until they set it up.
    var emergencyOn: Boolean get() = b("emergencyOn", false); set(v) = put("emergencyOn", v)
    var emergencyName: String get() = s("emergencyName", ""); set(v) = put("emergencyName", v)
    var emergencyNumber: String get() = s("emergencyNumber", ""); set(v) = put("emergencyNumber", v)
    var emergencyText: String get() = s("emergencyText", "J'ai besoin d'aide."); set(v) = put("emergencyText", v)
    // What she shows when they ask for their emergency info (only what they type here, kept on the phone).
    var emergencyInfo: String get() = s("emergencyInfo", ""); set(v) = put("emergencyInfo", v)

    // CAPRI SYNC: the PC's address on their Wi-Fi and the key both sides got when they paired.
    var syncAddress: String get() = s("syncAddress", ""); set(v) = put("syncAddress", v)
    var syncKey: String? get() = secrets.get("syncKey"); set(v) = secrets.put("syncKey", v)
    var syncPcName: String get() = s("syncPcName", ""); set(v) = put("syncPcName", v)
    var syncedAt: Long get() = sp.getLong("syncedAt", 0L); set(v) = put("syncedAt", v)
    val syncPhoneId: String get() = s("syncPhoneId", "").ifEmpty { java.util.UUID.randomUUID().toString().also { put("syncPhoneId", it) } }

    // Her watches (battery, Wi-Fi, a notification), as JSON.
    var watches: String get() = s("watches", "[]"); set(v) = put("watches", v)

    // SECURITY, MEMORY, LOOK
    var security: String get() = s("security", "11111110000"); set(v) = put("security", v)
    var keeping: Boolean get() = b("keeping", true); set(v) = put("keeping", v)
    var digging: Boolean get() = b("digging", true); set(v) = put("digging", v)
    var theme: Int get() = i("theme", 0); set(v) = put("theme", v)
    var onboarded: Boolean get() = b("onboarded", false); set(v) = put("onboarded", v)
}
