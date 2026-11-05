package com.capri.assistant.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.capri.assistant.CapriApp
import com.capri.assistant.R
import com.capri.assistant.engine.Line
import com.capri.assistant.overlay.OverlayService
import com.capri.assistant.ui.MainActivity
import com.capri.core.Reminder

object Notify {
    const val BUBBLE = "capri_bubble"
    const val REMIND = "capri_reminders"
    const val WORK = "capri_work"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(BUBBLE, "Bulle Capri", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Reste affichée tant que la bulle de Capri est active. Android l'exige pour un service au premier plan."
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(REMIND, "Rappels", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Les rappels et minuteurs que tu demandes à Capri."
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(WORK, "Capri agit sur l'écran", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Affichée quand Capri lit ton écran, en fait une capture ou touche une app pour toi."
            setShowBadge(false)
        })
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun reminder(context: Context, id: Int, text: String) {
        if (!allowed(context)) return
        val n = Notification.Builder(context, REMIND)
            .setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle("Rappel de Capri")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1000 + (id % 100000), n)
    }

    // While she reads the screen or touches an app, so it never happens out of sight.
    fun working(context: Context, what: String): Int {
        if (!allowed(context)) return 0
        val stop = PendingIntent.getService(context, 7, Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_STOP_JOB), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, WORK)
            .setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle("Capri agit sur ton écran")
            .setContentText(what)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(42, n)
        return 42
    }

    fun doneWorking(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(42)
}

// Her reminders on the phone's alarm clock: on time when exact alarms are allowed, within ten minutes otherwise.
object Alarms {
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(context: Context, r: Reminder): PendingIntent = PendingIntent.getBroadcast(
        context, r.id, Intent(context, ReminderReceiver::class.java).putExtra("id", r.id).putExtra("text", r.text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, r)
        try {
            if (exactAllowed(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.due, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, r.due, 10 * 60_000L, pi)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, r.due, 10 * 60_000L, pi)
        }
    }

    fun cancel(context: Context, r: Reminder) = context.getSystemService(AlarmManager::class.java).cancel(pending(context, r))

    fun rescheduleAll(context: Context) {
        val capri = CapriApp.capri(context)
        for (r in capri.reminders.waiting()) schedule(context, r)
    }

    fun askExact(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val capri = CapriApp.capri(context)
        val id = intent.getIntExtra("id", -1)
        val due = capri.reminders.take(id)
        val text = due?.text ?: intent.getStringExtra("text") ?: return
        Notify.reminder(context, id, text)
        // With her bubble up, she says it too.
        if (OverlayService.running) capri.main.post {
            val line = (if (capri.prefs.language == "French") "Rappel : " else "Reminder: ") + text
            capri.line(Line.CAPRI, line)
            capri.speak(line, voice = false)
            capri.bubble("glitch")
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.rescheduleAll(context)
        val capri = CapriApp.capri(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && capri.prefs.startOnBoot && capri.prefs.bubbleOn && Settings.canDrawOverlays(context)) {
            try { OverlayService.start(context, fromBackground = true) } catch (_: Exception) { }
        }
    }
}
