package com.capri.assistant.system

import android.Manifest
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.capri.assistant.assist.NotificationWatcher
import com.capri.core.Lang
import com.capri.core.Reminders
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Calendar

// Starting things on their phone. Every result says what really happened, in their language, so her brain (or the
// offline reply) never claims more than the phone did.
object Phone {
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun has(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

// Installed apps by the name on their home screen, with the nicknames people use.
object Apps {
    class App(val label: String, val pkg: String)

    @Volatile private var cache: List<App> = emptyList()
    @Volatile private var cachedAt = 0L

    private val nick = mapOf("insta" to "instagram", "ig" to "instagram", "yt" to "youtube", "ff" to "free fire", "freefire" to "free fire", "wa" to "whatsapp", "whats" to "whatsapp", "watsap" to "whatsapp",
        "fb" to "facebook", "messenger" to "messenger", "tiktok" to "tiktok", "tik tok" to "tiktok", "snap" to "snapchat", "maps" to "maps", "gmail" to "gmail", "chrome" to "chrome", "cod" to "call of duty",
        "pubg" to "pubg", "réglages" to "paramètres", "settings" to "paramètres", "parametres" to "paramètres", "caméra" to "appareil photo", "camera" to "appareil photo", "photos" to "galerie", "gallery" to "galerie",
        "horloge" to "horloge", "clock" to "horloge", "calculette" to "calculatrice", "calculator" to "calculatrice", "téléphone" to "téléphone", "phone" to "téléphone", "contacts" to "contacts", "messages" to "messages", "sms" to "messages")

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    fun all(context: Context): List<App> {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - cachedAt < 60_000) return cache
        val pm = context.packageManager
        val found = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
        cache = found
        cachedAt = now
        return found
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val t = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = t }
        }
        return d[b.length]
    }

    fun find(context: Context, name: String): App? {
        val asked = norm(name).removePrefix("l ").removePrefix("app ").trim()
        if (asked.isEmpty()) return null
        val want = norm(nick[asked] ?: asked)
        val apps = all(context).map { it to norm(it.label) }
        apps.firstOrNull { it.second == want }?.let { return it.first }
        apps.firstOrNull { it.second.replace(" ", "") == want.replace(" ", "") }?.let { return it.first }
        apps.firstOrNull { it.second.startsWith(want) }?.let { return it.first }
        apps.filter { it.second.contains(want) }.minByOrNull { it.second.length }?.let { return it.first }
        apps.firstOrNull { it.first.pkg.contains(want.replace(" ", "")) }?.let { return it.first }
        if (want.length >= 5) apps.map { it to distance(it.second, want) }.filter { it.second <= 2 }.minByOrNull { it.second }?.let { return it.first.first }
        return null
    }

    fun open(context: Context, name: String): String {
        val app = find(context, name) ?: return Lang.pick("That didn't work: no app called $name is installed on their phone.", "Ça n'a pas marché : aucune app « $name » n'est installée sur ton téléphone.")
        val launch = context.packageManager.getLaunchIntentForPackage(app.pkg) ?: return Lang.pick("That didn't work: ${app.label} can't be opened from outside.", "Ça n'a pas marché : ${app.label} ne peut pas être ouverte de l'extérieur.")
        return if (Phone.start(context, launch)) Lang.pick("Opened ${app.label}.", "J'ai ouvert ${app.label}.") else Lang.pick("That didn't work: Android didn't let me open ${app.label}.", "Ça n'a pas marché : Android m'a empêchée d'ouvrir ${app.label}.")
    }

    fun check(context: Context, name: String): String = find(context, name)?.let { Lang.pick("${it.label} is installed.", "${it.label} est installée.") }
        ?: Lang.pick("No app called $name is installed.", "Aucune app « $name » n'est installée.")

    fun installed(context: Context, pkg: String): Boolean = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }

    fun label(context: Context, pkg: String?): String? {
        if (pkg == null) return null
        return try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }
    }
}

// Websites and searches, opened in their browser for them to read.
object Links {
    private val searches = mapOf(
        "youtube" to "https://www.youtube.com/results?search_query=", "google" to "https://www.google.com/search?q=", "wikipedia" to "https://fr.wikipedia.org/w/index.php?search=",
        "maps" to "https://www.google.com/maps/search/?api=1&query=", "spotify" to "https://open.spotify.com/search/", "tiktok" to "https://www.tiktok.com/search?q=",
        "amazon" to "https://www.amazon.fr/s?k=", "facebook" to "https://www.facebook.com/search/top?q=", "x" to "https://x.com/search?q=", "twitter" to "https://x.com/search?q=")
    private val homes = mapOf("youtube" to "https://www.youtube.com", "google" to "https://www.google.com", "wikipedia" to "https://fr.wikipedia.org", "gmail" to "https://mail.google.com")

    fun url(site: String?, search: String?): String {
        val s = (site ?: "google").trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        val key = s.substringBefore('.')
        if (!search.isNullOrBlank()) {
            searches[key]?.let { return it + Phone.enc(search) }
            return "https://www.google.com/search?q=" + Phone.enc((if (s.contains('.')) "site:$s " else "$s ") + search)
        }
        homes[s]?.let { return it }
        return if (s.contains('.')) "https://$s" else "https://www.google.com/search?q=" + Phone.enc(s)
    }

    fun open(context: Context, site: String?, search: String?): String {
        val u = url(site, search)
        return if (Phone.start(context, Intent(Intent.ACTION_VIEW, Uri.parse(u)))) Lang.pick("Opened $u in their browser (they read it, not you).", "J'ai ouvert $u dans ton navigateur.")
        else Lang.pick("That didn't work: no browser opened $u.", "Ça n'a pas marché : aucun navigateur n'a ouvert $u.")
    }
}

// Music: Spotify and YouTube Music play straight from a search (the same request Google's assistant uses); YouTube opens
// its search, since it can't start a video on its own.
object Music {
    const val Spotify = "com.spotify.music"
    const val YtMusic = "com.google.android.apps.youtube.music"
    const val YouTube = "com.google.android.youtube"

    fun play(context: Context, song: String, kind: String?, app: String?): String {
        val pkg = when (app) { "spotify" -> Spotify; "youtube_music" -> YtMusic; "youtube" -> YouTube; else -> listOf(Spotify, YtMusic, YouTube).firstOrNull { Apps.installed(context, it) } }
        if (pkg == null || !Apps.installed(context, pkg)) {
            val u = "https://www.youtube.com/results?search_query=" + Phone.enc(song)
            return if (Phone.start(context, Intent(Intent.ACTION_VIEW, Uri.parse(u)))) Lang.pick("No music app here, so I opened a YouTube search for $song in their browser.", "Pas d'app de musique ici : j'ai ouvert une recherche YouTube pour « $song » dans ton navigateur.")
            else Lang.pick("That didn't work: no music app or browser.", "Ça n'a pas marché : pas d'app de musique ni de navigateur.")
        }
        val name = when (pkg) { Spotify -> "Spotify"; YtMusic -> "YouTube Music"; else -> "YouTube" }
        if (pkg == YouTube) {
            val i = Intent(Intent.ACTION_SEARCH).setPackage(YouTube).putExtra(SearchManager.QUERY, song)
            return if (Phone.start(context, i)) Lang.pick("Opened a YouTube search for $song; a video only starts when they tap one.", "J'ai ouvert une recherche YouTube pour « $song » ; une vidéo démarre quand tu en touches une.")
            else Lang.pick("That didn't work: YouTube didn't open.", "Ça n'a pas marché : YouTube ne s'est pas ouvert.")
        }
        val focus = when (kind) { "artist" -> MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE; "album" -> MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE; "playlist" -> "vnd.android.cursor.item/playlist"; else -> "vnd.android.cursor.item/*" }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, song).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, focus)
        return if (Phone.start(context, i)) Lang.pick("Asked $name to play $song.", "J'ai demandé à $name de jouer « $song ».")
        else if (Phone.start(context, context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent())) Lang.pick("$name opened but didn't take the search; they can pick $song there.", "$name s'est ouvert mais n'a pas pris la recherche ; tu peux choisir « $song » dedans.")
        else Lang.pick("That didn't work: $name didn't open.", "Ça n'a pas marché : $name ne s'est pas ouvert.")
    }
}

// Whatever is playing and the volume, through the same media keys a headset uses.
object Media {
    private fun key(am: AudioManager, code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun control(context: Context, action: String?, level: String?): String {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        fun now() = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxOf(1, max)
        return try {
            when (action) {
                "play_pause" -> { key(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); Lang.pick("Pressed play/pause.", "J'ai appuyé sur lecture/pause.") }
                "pause" -> { key(am, KeyEvent.KEYCODE_MEDIA_PAUSE); Lang.pick("Paused what was playing.", "J'ai mis en pause.") }
                "resume" -> { key(am, KeyEvent.KEYCODE_MEDIA_PLAY); Lang.pick("Pressed play.", "J'ai relancé la lecture.") }
                "next" -> { key(am, KeyEvent.KEYCODE_MEDIA_NEXT); Lang.pick("Skipped to the next one.", "Je suis passée au suivant.") }
                "previous" -> { key(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); Lang.pick("Went back one.", "Je suis revenue au précédent.") }
                "volume_up" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); Lang.pick("Volume up, now ${now()}%.", "Volume monté, à ${now()} %.") }
                "volume_down" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); Lang.pick("Volume down, now ${now()}%.", "Volume baissé, à ${now()} %.") }
                "volume_set" -> {
                    val pct = (level?.filter { it.isDigit() }?.toIntOrNull() ?: 50).coerceIn(0, 100)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max + 50) / 100, AudioManager.FLAG_SHOW_UI)
                    Lang.pick("Volume set to ${now()}%.", "Volume réglé à ${now()} %.")
                }
                "mute" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); Lang.pick("Muted.", "Son coupé.") }
                "unmute" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); Lang.pick("Sound back on.", "Son remis.") }
                "now_playing" -> nowPlaying(context)
                else -> Lang.pick("That didn't work: unknown media action.", "Ça n'a pas marché : action média inconnue.")
            }
        } catch (e: SecurityException) {
            Lang.pick("That didn't work: Do Not Disturb blocks volume changes right now.", "Ça n'a pas marché : le mode Ne pas déranger bloque le volume pour l'instant.")
        }
    }

    // What's playing needs notification access, the same door Android gives media controls.
    fun nowPlaying(context: Context): String {
        if (!NotificationWatcher.granted(context)) return Lang.pick("Can't see what's playing without notification access (Capri Security).", "Je ne vois pas ce qui joue sans l'accès aux notifications (Capri Sécurité).")
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val sessions = try { msm.getActiveSessions(ComponentName(context, NotificationWatcher::class.java)) } catch (e: SecurityException) { emptyList() }
        val playing = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
            ?: return Lang.pick("Nothing is playing.", "Rien ne joue en ce moment.")
        val md = playing.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val app = Apps.label(context, playing.packageName) ?: playing.packageName
        val state = if (playing.playbackState?.state == PlaybackState.STATE_PLAYING) Lang.pick("playing", "en lecture") else Lang.pick("paused", "en pause")
        return if (title == null) Lang.pick("$app is $state.", "$app est $state.") else "$app ($state): $title" + (if (artist.isNullOrBlank()) "" else " — $artist")
    }
}

// Settings pages and Android's own panels.
object SettingsPages {
    fun intent(context: Context, name: String?): Intent = when (name) {
        "wifi" -> Intent(Settings.Panel.ACTION_WIFI)
        "internet", "mobile_data" -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        "volume", "sound" -> if (name == "volume") Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS)
        "nfc" -> Intent(Settings.Panel.ACTION_NFC)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        "display", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        "notifications" -> Intent("android.settings.NOTIFICATION_SETTINGS")
        "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
        "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
        "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        "hotspot" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
        "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        "default_apps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        "assistant" -> Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        "capri" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        else -> Intent(Settings.ACTION_SETTINGS)
    }

    fun open(context: Context, name: String?): String {
        if (Phone.start(context, intent(context, name)) || Phone.start(context, Intent(Settings.ACTION_SETTINGS)))
            return Lang.pick("Opened their ${name ?: "settings"} settings for them to change.", "J'ai ouvert les réglages ${name ?: ""} pour que tu changes ce que tu veux.").replace("  ", " ")
        return Lang.pick("That didn't work: the settings page didn't open.", "Ça n'a pas marché : la page de réglages ne s'est pas ouverte.")
    }
}

object Torch {
    fun set(context: Context, on: Boolean): String {
        val cm = context.getSystemService(CameraManager::class.java)
        return try {
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return Lang.pick("That didn't work: this phone has no flashlight.", "Ça n'a pas marché : ce téléphone n'a pas de lampe.")
            cm.setTorchMode(id, on)
            if (on) Lang.pick("Flashlight on.", "Lampe allumée.") else Lang.pick("Flashlight off.", "Lampe éteinte.")
        } catch (e: Exception) { Lang.pick("That didn't work: the camera is busy, so the flashlight can't change.", "Ça n'a pas marché : l'appareil photo est occupé, la lampe ne peut pas changer.") }
    }
}

object Dnd {
    fun on(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    fun run(context: Context, action: String?): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (action == "status") return if (nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) Lang.pick("Do Not Disturb is on.", "Ne pas déranger est activé.") else Lang.pick("Do Not Disturb is off.", "Ne pas déranger est désactivé.")
        if (!nm.isNotificationPolicyAccessGranted) {
            Phone.start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return Lang.pick("Can't change Do Not Disturb yet: they have to allow Capri on the page I opened.", "Je ne peux pas encore changer Ne pas déranger : autorise Capri sur la page que je viens d'ouvrir.")
        }
        nm.setInterruptionFilter(if (action == "on") NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return if (action == "on") Lang.pick("Do Not Disturb on (priority only).", "Ne pas déranger activé (prioritaires seulement).") else Lang.pick("Do Not Disturb off.", "Ne pas déranger désactivé.")
    }
}

// The phone's clock app: alarms and timers go there, so they ring even if Capri is closed.
object Clock {
    private val hm = Regex("(\\d{1,2})\\s*(?:h|:|heures?)\\s*(\\d{1,2})?|(\\d{1,2})\\s*(am|pm)", RegexOption.IGNORE_CASE)

    fun time(text: String?): Pair<Int, Int>? {
        val t = (text ?: "").trim()
        hm.find(t)?.let { m ->
            if (m.groupValues[3].isNotEmpty()) { var h = m.groupValues[3].toInt() % 12; if (m.groupValues[4].equals("pm", true)) h += 12; return h to 0 }
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (Regex("soir|evening|night|pm", RegexOption.IGNORE_CASE).containsMatchIn(t) && h < 12) h += 12
            if (h in 0..23 && min in 0..59) return h to min
        }
        val due = Reminders.parse(t, System.currentTimeMillis()) ?: return null
        val c = Calendar.getInstance().apply { timeInMillis = due }
        return c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
    }

    fun run(context: Context, action: String?, whenText: String?, seconds: String?, label: String?): String = when (action) {
        "set_alarm" -> {
            val t = time(whenText) ?: return Lang.pick("That didn't work: I couldn't read the time \"$whenText\".", "Ça n'a pas marché : je n'ai pas compris l'heure « $whenText ».")
            val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, t.first).putExtra(AlarmClock.EXTRA_MINUTES, t.second).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!label.isNullOrBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
            val shown = String.format("%02d:%02d", t.first, t.second)
            if (Phone.start(context, i)) Lang.pick("Alarm set for $shown in their clock app.", "Réveil réglé à $shown dans ton horloge.") else Lang.pick("That didn't work: no clock app took the alarm.", "Ça n'a pas marché : aucune horloge n'a pris le réveil.")
        }
        "set_timer" -> {
            val s = seconds?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 86399) ?: return Lang.pick("That didn't work: how long?", "Ça n'a pas marché : combien de temps ?")
            val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!label.isNullOrBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
            val shown = if (s >= 60) "${s / 60} min" + (if (s % 60 > 0) " ${s % 60} s" else "") else "$s s"
            if (Phone.start(context, i)) Lang.pick("Timer started for $shown in their clock app.", "Minuteur de $shown lancé dans ton horloge.") else Lang.pick("That didn't work: no clock app took the timer.", "Ça n'a pas marché : aucune horloge n'a pris le minuteur.")
        }
        else -> if (Phone.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))) Lang.pick("Opened their alarms.", "J'ai ouvert tes réveils.") else Lang.pick("That didn't work: no clock app.", "Ça n'a pas marché : pas d'horloge.")
    }
}

// Their calendar app opens with the event filled in; they save it themselves.
object Agenda {
    fun run(context: Context, action: String?, title: String?, whenText: String?, minutes: Int?): String {
        if (action == "open") {
            val u = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
            return if (Phone.start(context, Intent(Intent.ACTION_VIEW, u))) Lang.pick("Opened their calendar.", "J'ai ouvert ton agenda.") else Lang.pick("That didn't work: no calendar app.", "Ça n'a pas marché : pas d'app d'agenda.")
        }
        val start = Reminders.parse(whenText ?: "", System.currentTimeMillis())
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title ?: "")
        if (start != null) i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60_000L * (minutes ?: 60))
        return if (Phone.start(context, i)) Lang.pick("Their calendar opened with \"$title\" filled in; they press save.", "Ton agenda s'est ouvert avec « $title » prérempli ; appuie sur Enregistrer.")
        else Lang.pick("That didn't work: no calendar app.", "Ça n'a pas marché : pas d'app d'agenda.")
    }
}

// Calls, texts, emails, shares and directions: the phone's own app opens ready, and they make the last tap.
object Contacts {
    class Found(val name: String, val number: String?, val email: String?)

    fun lookup(context: Context, who: String): Found? {
        if (!Phone.has(context, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(who))
        context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            if (c.moveToFirst()) return Found(c.getString(0) ?: who, c.getString(1), null)
        }
        val mail = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI, Uri.encode(who))
        context.contentResolver.query(mail, arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { c ->
            if (c.moveToFirst()) return Found(c.getString(0) ?: who, null, c.getString(1))
        }
        return null
    }

    private fun isNumber(s: String) = Regex("^[+\\d][\\d\\s().-]{2,}$").matches(s.trim())

    fun run(context: Context, action: String?, target: String?, text: String?): String {
        val t = (target ?: "").trim()
        fun number(): Pair<String?, String> {
            if (isNumber(t)) return t.filter { it.isDigit() || it == '+' } to t
            val f = lookup(context, t) ?: return null to t
            return f.number to f.name
        }
        val noContacts = Lang.pick("Can't find $t: Capri needs Contacts permission (Capri Security), or tell me the number.", "Je ne trouve pas $t : autorise Contacts dans Capri Sécurité, ou donne-moi le numéro.")
        return when (action) {
            "call" -> {
                val (n, name) = number()
                if (n == null) return if (!Phone.has(context, Manifest.permission.READ_CONTACTS)) noContacts else Lang.pick("No number for $t in their contacts.", "Pas de numéro pour $t dans tes contacts.")
                if (Phone.start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))) Lang.pick("Their dialer is open with $name's number; they press call.", "Ton téléphone est prêt avec le numéro de $name : appuie sur Appeler.")
                else Lang.pick("That didn't work: no dialer.", "Ça n'a pas marché : pas d'app Téléphone.")
            }
            "sms" -> {
                val (n, name) = number()
                if (n == null) return if (!Phone.has(context, Manifest.permission.READ_CONTACTS)) noContacts else Lang.pick("No number for $t in their contacts.", "Pas de numéro pour $t dans tes contacts.")
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))).putExtra("sms_body", text ?: "")
                if (Phone.start(context, i)) Lang.pick("Their messages app is open with a text to $name ready; they press send.", "Ton SMS pour $name est prêt : appuie sur Envoyer.") else Lang.pick("That didn't work: no messages app.", "Ça n'a pas marché : pas d'app de messages.")
            }
            "email" -> {
                val address = if (t.contains('@')) t else lookup(context, t)?.email
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (address ?: ""))).putExtra(Intent.EXTRA_TEXT, text ?: "")
                if (Phone.start(context, i)) Lang.pick("Their email app is open with the email ready; they press send.", "Ton e-mail est prêt dans ta messagerie : appuie sur Envoyer.") else Lang.pick("That didn't work: no email app.", "Ça n'a pas marché : pas d'app d'e-mail.")
            }
            "share" -> {
                val i = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text ?: t), null)
                if (Phone.start(context, i)) Lang.pick("Opened the share menu; they pick where it goes.", "J'ai ouvert le menu de partage : choisis où l'envoyer.") else Lang.pick("That didn't work.", "Ça n'a pas marché.")
            }
            "navigate" -> {
                if (Phone.start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(t))))) Lang.pick("Opened directions to $t in their maps app.", "J'ai ouvert l'itinéraire vers $t dans ton app de cartes.")
                else Links.open(context, "maps", t)
            }
            else -> if (Phone.start(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))) Lang.pick("Opened their contacts.", "J'ai ouvert tes contacts.") else Lang.pick("That didn't work.", "Ça n'a pas marché.")
        }
    }
}
