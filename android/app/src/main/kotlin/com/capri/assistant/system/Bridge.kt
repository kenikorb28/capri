package com.capri.assistant.system

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Things only a screen can ask for (a file picker, the camera app, the clipboard, a permission), done by a see-through
// screen while her brain thread waits for the answer. Each one is shown to them; nothing happens out of sight.
object Bridge {
    class Result(val uri: Uri? = null, val text: String? = null, val error: String? = null)

    private class Waiting { val latch = CountDownLatch(1); @Volatile var result = Result(error = "timeout") }
    @Volatile private var waiting: Waiting? = null

    const val PICK = "pick"; const val CREATE = "create"; const val CAMERA = "camera"; const val CLIP = "clip"; const val PERMISSION = "permission"

    // Off the main thread only. Returns what the screen got, or an error ("cancelled", "timeout", "busy").
    fun ask(context: Context, kind: String, extras: Bundle = Bundle(), timeoutMs: Long = 120_000): Result {
        val w = Waiting()
        synchronized(this) { if (waiting != null) return Result(error = "busy"); waiting = w }
        try {
            context.startActivity(Intent(context, BridgeActivity::class.java).putExtra("kind", kind).putExtras(extras)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            w.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return w.result
        } catch (e: Exception) {
            return Result(error = e.message ?: "error")
        } finally { synchronized(this) { if (waiting === w) waiting = null } }
    }

    fun done(result: Result) { val w = waiting ?: return; w.result = result; w.latch.countDown() }

    val photo: Uri = Uri.parse("content://com.capri.assistant.photos/photo.jpg")

    fun release(context: Context, uri: Uri) {
        try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {
            try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        }
    }
    fun photoFile(context: Context) = File(context.cacheDir, "camera/photo.jpg").apply { parentFile?.mkdirs() }
}

class BridgeActivity : Activity() {
    private var kind = ""
    private var answered = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        kind = intent.getStringExtra("kind") ?: ""
        if (saved != null) return
        try {
            when (kind) {
                Bridge.PICK -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, intent.getStringArrayExtra("types") ?: arrayOf("*/*")), 1)
                Bridge.CREATE -> startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(intent.getStringExtra("mime") ?: "text/plain").putExtra(Intent.EXTRA_TITLE, intent.getStringExtra("name") ?: "capri.txt"), 2)
                Bridge.CAMERA -> {
                    Bridge.photoFile(this).delete()
                    val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, Bridge.photo)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    i.clipData = ClipData.newRawUri("photo", Bridge.photo)
                    startActivityForResult(i, 3)
                }
                Bridge.PERMISSION -> requestPermissions(intent.getStringArrayExtra("permissions") ?: emptyArray(), 4)
                Bridge.CLIP -> Unit // read once this screen has focus, which Android requires for the clipboard
                else -> finishWith(Bridge.Result(error = "unknown"))
            }
        } catch (e: Exception) {
            finishWith(Bridge.Result(error = if (kind == Bridge.CAMERA) "no camera app" else (e.message ?: "error")))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || kind != Bridge.CLIP || answered) return
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
        finishWith(if (text.isNullOrBlank()) Bridge.Result(error = "empty") else Bridge.Result(text = text))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) { finishWith(Bridge.Result(error = "cancelled")); return }
        when (requestCode) {
            1, 2 -> {
                val uri = data?.data
                // Kept past this screen, for reading (or deleting, once they said yes) right after; released after use.
                val granted = (data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (uri != null) try { contentResolver.takePersistableUriPermission(uri, if (granted != 0) granted else Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                finishWith(if (uri == null) Bridge.Result(error = "cancelled") else Bridge.Result(uri = uri))
            }
            3 -> finishWith(if (Bridge.photoFile(this).length() > 0) Bridge.Result(uri = Uri.fromFile(Bridge.photoFile(this))) else Bridge.Result(error = "no photo"))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        finishWith(Bridge.Result(text = if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) "granted" else "denied"))
    }

    private fun finishWith(r: Bridge.Result) {
        if (answered) return
        answered = true
        Bridge.done(r)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!answered && isFinishing) Bridge.done(Bridge.Result(error = "cancelled"))
    }
}

// Where the camera app writes the photo she looks at: one file in her cache, shared only with the camera app for that shot.
class PhotoProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = Bridge.photoFile(context!!)
        val m = if (mode.contains('w')) ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(f, m)
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = Bridge.photoFile(context!!)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply { addRow(cols.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) "photo.jpg" else null }.toTypedArray()) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
