package com.capri.assistant.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.capri.assistant.CapriApp
import com.capri.core.Lang
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// "Capri — mains sur l'écran". It does nothing on its own: it only remembers which app is in front, and reads, taps,
// types or takes a picture when one of her tools asks, which only happens when they ask her. Password fields are never
// read or typed, and windows an app marks secure (FLAG_SECURE) can't be captured, by Android's design.
class CapriAccessibility : AccessibilityService() {
    companion object {
        @Volatile var instance: CapriAccessibility? = null
            private set
        @Volatile var front: String? = null
            private set
        @Volatile var changedAt = 0L
            private set

        fun on(): Boolean = instance != null

        private val systemUi = setOf("com.android.systemui", "android", "com.transsion.hilauncher", "com.android.launcher3")
    }

    // One thing on the screen her hands can touch, by its number in the list her brain sees.
    class Item(val n: Int, val node: AccessibilityNodeInfo, val label: String, val kind: String, val bounds: Rect)

    @Volatile private var items: List<Item> = emptyList()

    override fun onServiceConnected() { instance = this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        changedAt = System.currentTimeMillis()
        if (pkg != packageName && pkg !in systemUi) front = pkg
    }

    override fun onInterrupt() {}

    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    private fun appWindows(): List<AccessibilityWindowInfo> = try {
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() != packageName }
            .sortedBy { it.layer }.reversed()
    } catch (e: Exception) { emptyList() }

    fun frontPackage(): String? = appWindows().firstOrNull()?.root?.packageName?.toString() ?: front

    // The words on screen, top to bottom, without password fields, their own overlays or hers.
    fun text(most: Int = 7000): String {
        val out = StringBuilder()
        val seen = HashSet<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || out.length > most) return
            if (n.isPassword) { out.append("[").append(Lang.pick("password field", "champ de mot de passe")).append("]\n"); return }
            if (n.isVisibleToUser) {
                val t = (n.text?.toString() ?: "").trim()
                val d = (n.contentDescription?.toString() ?: "").trim()
                val line = if (t.isNotEmpty()) t else d
                if (line.isNotEmpty() && seen.add(line)) out.append(line.replace('\n', ' ')).append('\n')
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        for (w in appWindows()) walk(w.root, 0)
        return out.toString().let { if (it.length > most) it.substring(0, most) + " [...]" else it }.trim()
    }

    fun global(action: String): Boolean {
        val code = when (action) {
            "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS; "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS; "lock_screen" -> GLOBAL_ACTION_LOCK_SCREEN; "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "split_screen" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN; "power_menu" -> GLOBAL_ACTION_POWER_DIALOG; else -> return false
        }
        return performGlobalAction(code)
    }

    // Scrolls the biggest scrollable thing on screen, or swipes if nothing says it scrolls.
    fun scroll(down: Boolean): Boolean {
        var best: AccessibilityNodeInfo? = null
        var area = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40) return
            if (n.isScrollable && n.isVisibleToUser) { val r = Rect(); n.getBoundsInScreen(r); if (r.width() * r.height() > area) { area = r.width() * r.height(); best = n } }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        for (w in appWindows()) walk(w.root, 0)
        best?.let { if (it.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true }
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        return swipe(x, m.heightPixels * (if (down) 0.72f else 0.3f), x, m.heightPixels * (if (down) 0.3f else 0.72f))
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 280): Boolean {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    private fun tapAt(r: Rect): Boolean {
        val p = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    // A numbered list of what can be tapped or typed into on the front app, for a job she's doing for them.
    fun listing(most: Int = 70): String {
        val found = ArrayList<Item>()
        fun label(n: AccessibilityNodeInfo): String {
            val own = listOf(n.text, n.contentDescription, n.hintText).firstOrNull { !it.isNullOrBlank() }?.toString()?.replace('\n', ' ')?.trim()
            if (!own.isNullOrEmpty()) return own
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; val t = listOf(c.text, c.contentDescription).firstOrNull { !it.isNullOrBlank() }; if (t != null) return t.toString().trim() }
            return n.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' ') ?: ""
        }
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || found.size >= most) return
            if (n.isVisibleToUser && !n.isPassword) {
                val editable = n.isEditable
                val clickable = n.isClickable || n.isCheckable || n.isLongClickable
                if (editable || clickable) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    val l = label(n).take(80)
                    if (r.width() > 4 && r.height() > 4 && (l.isNotEmpty() || editable)) {
                        val kind = when { editable -> "field"; n.isCheckable -> if (n.isChecked) "switch:on" else "switch:off"; n.className?.contains("Button") == true -> "button"; else -> "item" }
                        found.add(Item(found.size + 1, n, l, kind, r))
                    }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        for (w in appWindows()) walk(w.root, 0)
        items = found
        val words = text(1800)
        return "Tappable things on screen:\n" + found.joinToString("\n") { "${it.n}. [${it.kind}] ${it.label}" } + "\nText on screen:\n" + words
    }

    fun item(n: Int): Item? = items.firstOrNull { it.n == n }

    fun tap(item: Item): Boolean {
        var n: AccessibilityNodeInfo? = item.node
        while (n != null) { if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; n = n.parent }
        return tapAt(item.bounds)
    }

    fun type(item: Item, text: String): Boolean {
        if (item.node.isPassword) return false
        item.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return item.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // A picture of the screen (Android 11+), or why there isn't one.
    fun shot(timeoutMs: Long = 4000): Pair<Bitmap?, String?> {
        val latch = CountDownLatch(1)
        var bmp: Bitmap? = null
        var why: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hb = result.hardwareBuffer
                    bmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                } catch (e: Exception) { why = e.message }
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) {
                why = when (errorCode) {
                    ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> Lang.pick("this app protects its screen, so Android won't let anyone capture it", "cette app protège son écran : Android interdit de le capturer")
                    ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> Lang.pick("too soon after the last capture, try again in a second", "trop tôt après la dernière capture, réessaie dans une seconde")
                    else -> Lang.pick("Android refused the capture (code $errorCode)", "Android a refusé la capture (code $errorCode)")
                }
                latch.countDown()
            }
        })
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return bmp to (why ?: if (bmp == null) Lang.pick("the capture timed out", "la capture a expiré") else null)
    }

    fun capri() = CapriApp.capri(this)
}
