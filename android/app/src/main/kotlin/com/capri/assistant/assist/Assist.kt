package com.capri.assistant.assist

import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.capri.assistant.CapriApp
import com.capri.assistant.engine.ScreenShot
import com.capri.assistant.overlay.OverlayService
import com.capri.assistant.screen.ScreenTools
import com.capri.assistant.ui.ChatActivity
import com.capri.assistant.ui.ListenActivity
import com.capri.assistant.ui.MainActivity
import com.capri.assistant.voice.Recognizers
import com.capri.core.Lang
import com.capri.core.Notice

// Capri as the phone's default digital assistant (Android's ASSISTANT role, which they choose in Settings > Apps >
// Default apps > Digital assistant). Long-pressing home or power opens her session: if they allowed "Use text from screen"
// and "Use screenshot" there, Android hands her what's on screen for that one request; she keeps it a minute, only for
// what they ask next.
class CapriVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        val capri = CapriApp.capri(this)
        // The assistant may start her bubble (and its microphone) when they had it on.
        if (capri.prefs.bubbleOn && Settings.canDrawOverlays(this) && !OverlayService.running) try { OverlayService.start(this) } catch (_: Exception) { }
    }
}

class CapriSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = CapriSession(this)
}

class CapriSession(context: Context) : VoiceInteractionSession(context) {
    companion object { @Volatile var showing = false; private set }

    private val capri = CapriApp.capri(context)
    private var words: String? = null
    private var picture: String? = null
    private var app: String? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        showing = true
        words = null; picture = null
        // Her listening starts right away; the screen's words and picture arrive a moment later if allowed.
        capri.main.postDelayed({ capri.listen(conversation = capri.prefs.keepListening) }, 250)
        if (Settings.canDrawOverlays(context)) {
            if (!OverlayService.running) try { OverlayService.start(context) } catch (_: Exception) { }
            capri.main.postDelayed({ OverlayService.showPanel() }, 400)
        } else {
            context.startActivity(Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onHandleAssist(state: AssistState) {
        val structure = state.assistStructure ?: return
        app = structure.activityComponent?.packageName
        val out = StringBuilder()
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode, out, 0)
        words = out.toString().trim().ifEmpty { null }
        keep()
    }

    private fun walk(n: AssistStructure.ViewNode?, out: StringBuilder, depth: Int) {
        if (n == null || depth > 40 || out.length > 7000) return
        // Password fields stay out, like everywhere else in Capri.
        val variation = n.inputType and 0xff0
        if ((n.inputType and 0xf) == 1 && (variation == 0x80 || variation == 0x90 || variation == 0xe0) || (n.inputType and 0xf) == 2 && variation == 0x10) return
        val t = n.text?.toString()?.trim().orEmpty().ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
        if (t.isNotEmpty()) out.append(t.replace('\n', ' ')).append('\n')
        for (i in 0 until n.childCount) walk(n.getChildAt(i), out, depth + 1)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (screenshot != null) picture = try { ScreenTools.jpeg(screenshot) } catch (e: Exception) { null }
        keep()
    }

    private fun keep() { if (words != null || picture != null) capri.assist = ScreenShot(words, picture, app) }

    override fun onHide() {
        super.onHide()
        showing = false
    }

    // Her own windows show everything; the session's window stays empty and out of the way.
    override fun onCreateContentView(): android.view.View? = null
}

// When Android asks Capri's recognizer to listen (it becomes the default one with the assistant role), it passes the job
// to the phone's own recognizer, so voice typing in other apps keeps working exactly as before.
class CapriRecognitionService : RecognitionService() {
    private var inner: SpeechRecognizer? = null

    override fun onStartListening(intent: Intent, listener: Callback) {
        val c: ComponentName? = Recognizers.best(this)
        if (c == null) { try { listener.error(SpeechRecognizer.ERROR_CLIENT) } catch (_: RemoteException) { }; return }
        val sr = SpeechRecognizer.createSpeechRecognizer(this, c)
        inner = sr
        sr.setRecognitionListener(object : RecognitionListener {
            fun safe(block: () -> Unit) = try { block() } catch (_: RemoteException) { }
            override fun onReadyForSpeech(params: Bundle?) = safe { listener.readyForSpeech(params ?: Bundle()) }
            override fun onBeginningOfSpeech() = safe { listener.beginningOfSpeech() }
            override fun onRmsChanged(rmsdB: Float) = safe { listener.rmsChanged(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = safe { if (buffer != null) listener.bufferReceived(buffer) }
            override fun onEndOfSpeech() = safe { listener.endOfSpeech() }
            override fun onError(error: Int) = safe { listener.error(error) }
            override fun onResults(results: Bundle?) = safe { listener.results(results ?: Bundle()) }
            override fun onPartialResults(partialResults: Bundle?) = safe { listener.partialResults(partialResults ?: Bundle()) }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(intent)
    }

    override fun onStopListening(listener: Callback) { inner?.stopListening() }
    override fun onCancel(listener: Callback) { inner?.cancel(); inner?.destroy(); inner = null }
    override fun onDestroy() { inner?.destroy(); inner = null; super.onDestroy() }
}

// Long-press home when Capri is the assist app without the voice service: she listens, over whatever was open.
class AssistActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        startActivity(Intent(this, ListenActivity::class.java).putExtra("conversation", CapriApp.capri(this).prefs.keepListening).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

// "Expliquer avec Capri" in any app's text selection menu.
class ProcessTextActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            val capri = CapriApp.capri(this)
            capri.say(Lang.pick("Explain this simply, and translate it if it isn't in my language: ", "Explique-moi ça simplement, et traduis-le si ce n'est pas dans ma langue : ") + "« " + text.take(4000) + " »",
                echo = Lang.pick("Explain: ", "Explique : ") + text.take(80))
            open(this)
        }
        finish()
    }

    companion object {
        fun open(a: Activity) {
            if (Settings.canDrawOverlays(a)) { if (!OverlayService.running) OverlayService.start(a); CapriApp.capri(a).main.postDelayed({ OverlayService.showPanel() }, 350) }
            else a.startActivity(Intent(a, ChatActivity::class.java))
        }
    }
}

// Share a text, a link or a picture with Capri.
class ShareActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val capri = CapriApp.capri(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        @Suppress("DEPRECATION") val image: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (image != null && (intent.type ?: "").startsWith("image/")) {
            capri.io.execute {
                val bmp = try { contentResolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
                if (bmp == null) return@execute
                val jpeg = ScreenTools.jpeg(bmp)
                capri.main.post {
                    if (capri.brain.sees()) capri.say(Lang.pick("Look at this picture I shared and tell me what matters in it.", "Regarde l'image que je t'envoie et dis-moi ce qui compte dedans.") + (text?.let { " $it" } ?: ""), images = listOf(jpeg))
                    else capri.io.execute { val words = ScreenTools.ocr(bmp); capri.main.post { capri.say(Lang.pick("Here are the words in a picture I shared: ", "Voici les mots d'une image que je t'envoie : ") + (words ?: "(aucun texte)")) } }
                }
            }
        } else if (!text.isNullOrEmpty()) {
            capri.say(Lang.pick("I'm sharing this with you, tell me what it is and what you think: ", "Je te partage ça, dis-moi ce que c'est et ce que tu en penses : ") + "« " + text.take(4000) + " »", echo = text.take(120))
        }
        ProcessTextActivity.open(this)
        finish()
    }
}

// Her quick settings tile: tap to talk, long-press for her app.
class CapriTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = if (OverlayService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Capri"; updateTile() }
    }

    override fun onClick() {
        val i = Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 21, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

// Notification access, when they turn it on: she keeps the last day of notifications in memory on the phone (never on
// disk, never sent anywhere by itself), for when they ask "qu'est-ce que j'ai reçu ?" and for their watches.
class NotificationWatcher : NotificationListenerService() {
    companion object {
        @Volatile var connected = false
            private set

        fun granted(context: Context): Boolean = context.getSystemService(android.app.NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, NotificationWatcher::class.java))
    }

    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val capri = CapriApp.capri(this)
        if (!capri.prefs.notifKeep || n.packageName == packageName || n.isOngoing || (n.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString() } catch (e: Exception) { n.packageName }
        val notice = Notice(app, n.packageName, title, text.take(600), n.postTime, n.notification.category)
        capri.notices.add(notice)
        capri.watches.notice(notice)
    }
}

// Opens her app's home from anywhere.
object Home { fun open(context: Context) = context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
