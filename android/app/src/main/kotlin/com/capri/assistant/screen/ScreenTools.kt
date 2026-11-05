package com.capri.assistant.screen

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import com.capri.assistant.R
import com.capri.assistant.system.Notify
import com.capri.core.Lang
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ScreenTools {
    // A picture small enough for any seeing brain: the long side at most 1280 px, JPEG quality 70, as base64.
    fun jpeg(bitmap: Bitmap, maxSide: Int = 1280, quality: Int = 70): String {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // Words in a picture, read on the phone itself by ML Kit's bundled Latin model (no internet, nothing sent).
    fun ocr(bitmap: Bitmap): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 12, TimeUnit.SECONDS)
            text?.textBlocks?.joinToString("\n") { it.text }?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null } finally { try { recognizer.close() } catch (_: Exception) { } }
    }
}

// The screen capture Android asks about each time (MediaProjection), for when the accessibility service is off: a
// see-through screen shows Android's own question, then one frame is taken and the capture stops at once.
object Capture {
    private class Waiting { val latch = CountDownLatch(1); @Volatile var bitmap: Bitmap? = null; @Volatile var error: String? = null }
    @Volatile private var waiting: Waiting? = null

    // Off the main thread only.
    fun shot(context: Context, timeoutMs: Long = 45_000): Pair<Bitmap?, String?> {
        val w = Waiting()
        synchronized(this) { if (waiting != null) return null to "busy"; waiting = w }
        try {
            context.startActivity(Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            if (!w.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null to Lang.pick("the capture question went unanswered", "la question de capture est restée sans réponse")
            return w.bitmap to w.error
        } finally { synchronized(this) { if (waiting === w) waiting = null } }
    }

    fun done(bitmap: Bitmap?, error: String?) { val w = waiting ?: return; w.bitmap = bitmap; w.error = error; w.latch.countDown() }
}

class CaptureActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (saved != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION") startActivityForResult(mpm.createScreenCaptureIntent(), 9)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java).putExtra("code", resultCode).putExtra("data", data))
        } else Capture.done(null, Lang.pick("I don't have permission to see your screen: the capture was refused", "je n'ai pas la permission d'accéder à ton écran : la capture a été refusée"))
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    @Volatile private var delivered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notification.Builder(this, Notify.WORK).setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle(Lang.pick("Capri is looking at your screen", "Capri regarde ton écran")).setContentText(Lang.pick("One picture, then it stops.", "Une seule image, puis ça s'arrête.")).build()
        try { startForeground(43, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) } catch (e: Exception) { finishWith(null, e.message ?: "foreground refused"); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION") val data: Intent? = intent?.getParcelableExtra("data")
        if (data == null) { finishWith(null, "no data"); return START_NOT_STICKY }
        val t = HandlerThread("capri-capture").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        try {
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
            if (mp == null) { finishWith(null, Lang.pick("Android refused the capture", "Android a refusé la capture")); return START_NOT_STICKY }
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { finishWith(null, Lang.pick("the capture was stopped", "la capture a été arrêtée")) } }, handler)
            val m = resources.displayMetrics
            val w = m.widthPixels; val h = m.heightPixels
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            reader = r
            val asked = System.currentTimeMillis()
            r.setOnImageAvailableListener({ ir ->
                val image = try { ir.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                try {
                    // The first frames can still show Android's question fading out.
                    if (System.currentTimeMillis() - asked < 450 || delivered) return@setOnImageAvailableListener
                    val plane = image.planes[0]
                    val rowPad = (plane.rowStride - plane.pixelStride * w) / plane.pixelStride
                    val raw = Bitmap.createBitmap(w + rowPad, h, Bitmap.Config.ARGB_8888)
                    raw.copyPixelsFromBuffer(plane.buffer)
                    finishWith(if (rowPad > 0) Bitmap.createBitmap(raw, 0, 0, w, h) else raw, null)
                } finally { image.close() }
            }, handler)
            display = mp.createVirtualDisplay("capri-look", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
            handler.postDelayed({ finishWith(null, Lang.pick("no picture came", "aucune image n'est arrivée")) }, 5000)
        } catch (e: Exception) { finishWith(null, e.message ?: "capture failed") }
        return START_NOT_STICKY
    }

    @Synchronized private fun finishWith(bitmap: Bitmap?, error: String?) {
        if (delivered) return
        delivered = true
        Capture.done(bitmap, error)
        try { display?.release() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        thread?.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
