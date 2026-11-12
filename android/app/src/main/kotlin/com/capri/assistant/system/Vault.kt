package com.capri.assistant.system

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.capri.assistant.BuildConfig
import com.capri.assistant.engine.Capri
import com.capri.core.Backup
import com.capri.core.BackupError
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.arr
import com.capri.core.num
import com.capri.core.obj
import com.capri.core.str
import java.io.File

// SAUVEGARDE, the phone's side: what goes into the encrypted file (core/Backup.kt seals it) and how it comes back.
// In: everything in her folder (notes, memories, chats, projects, reminders, Hey Capri, usage) and her settings; their
// API keys only if they say so. Out: this phone's Capri Link pairing and its protections, which belong to the phone in hand.
object Vault {
    private val keyNames = listOf("mediumKey", "maxKey", "elevenKey", "fishKey", "speechKey", "whisperKey")

    private fun local(pref: String) = pref.startsWith("link") || pref == "syncPhoneId" || pref == "gate" || pref == "halted" || pref == "capNoted"
    private fun skipped(path: String) = path.startsWith("link/") || path.endsWith(".new") || path.endsWith(".link-new") || path == "errors.txt"
    private fun text(bytes: ByteArray?): String? = bytes?.toString(Charsets.UTF_8)

    fun collect(capri: Capri, keys: Boolean): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val root = capri.folder
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val path = f.relativeTo(root).invariantSeparatorsPath
            if (!skipped(path) && Backup.safeName("capri/$path")) files["capri/$path"] = f.readBytes()
        }
        val prefs = LinkedHashMap<String, Any?>()
        for ((k, v) in capri.prefs.sp.all) {
            if (local(k)) continue
            when (v) {
                is String -> prefs[k] = listOf("s", v)
                is Int -> prefs[k] = listOf("i", v)
                is Boolean -> prefs[k] = listOf("b", v)
                is Long -> prefs[k] = listOf("l", v)
                is Float -> prefs[k] = listOf("f", v.toDouble())
                else -> { }
            }
        }
        files["prefs.json"] = Json.write(prefs).toByteArray(Charsets.UTF_8)
        if (keys) {
            val secrets = LinkedHashMap<String, Any?>()
            for (name in keyNames) capri.prefs.secrets.get(name)?.let { secrets[name] = it }
            files["secrets.json"] = Json.write(secrets).toByteArray(Charsets.UTF_8)
        }
        files["manifest.json"] = Json.write(linkedMapOf<String, Any?>("format" to 1, "app" to BuildConfig.VERSION_NAME, "at" to System.currentTimeMillis(),
            "device" to (Build.MANUFACTURER + " " + Build.MODEL), "keys" to keys)).toByteArray(Charsets.UTF_8)
        return files
    }

    fun save(capri: Capri, resolver: ContentResolver, uri: Uri, password: String, keys: Boolean): String {
        val files = collect(capri, keys)
        val sealed = Backup.pack(files, password)
        (resolver.openOutputStream(uri, "wt") ?: throw BackupError(Lang.pick("Couldn't write to that file.", "Impossible d'écrire dans ce fichier."))).use { it.write(sealed) }
        val size = "" + files.size + Lang.pick(" files, ", " fichiers, ") + (sealed.size / 1024 + 1) + Lang.pick(" KB", " Ko")
        return Lang.pick("Backup made: $size", "Sauvegarde créée : $size") + if (keys) Lang.pick(", your keys included.", ", clés comprises.") else Lang.pick(", without your keys.", ", sans tes clés.")
    }

    fun open(resolver: ContentResolver, uri: Uri, password: String): Map<String, ByteArray> {
        val sealed = (resolver.openInputStream(uri) ?: throw BackupError(Lang.pick("Couldn't read that file.", "Impossible de lire ce fichier."))).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > Backup.MaxBytes + 65536) throw BackupError(Lang.pick("That file is too big to be a Capri backup.", "Ce fichier est trop gros pour être une sauvegarde Capri."))
            }
            out.toByteArray()
        }
        val files = Backup.unpack(sealed, password)
        if (Json.parseOrNull(text(files["manifest.json"])).obj()?.get("format").num()?.toInt() != 1)
            throw BackupError(Lang.pick("That backup was made by a newer Capri.", "Cette sauvegarde vient d'une Capri plus récente."))
        return files
    }

    // What they read before saying yes.
    fun describe(files: Map<String, ByteArray>): String {
        val m = Json.parseOrNull(text(files["manifest.json"])).obj() ?: emptyMap()
        val made = m["at"].num()?.toLong()?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).format(java.util.Date(it)) } ?: "?"
        val count = files.keys.count { it.startsWith("capri/") }
        val device = m["device"].str() ?: "?"
        val version = m["app"].str() ?: "?"
        val keys = files.containsKey("secrets.json")
        return Lang.pick("Made on $made, on a $device (Capri $version): $count files", "Faite le $made, sur un $device (Capri $version) : $count fichiers") +
            (if (keys) Lang.pick(", with API keys.", ", avec les clés API.") else Lang.pick(", no API keys.", ", sans clés API.")) + "\n\n" +
            Lang.pick("Her memory and her settings on this phone will be replaced. What is here now is kept aside once, in case. Capri Link will need pairing again.",
                "Sa mémoire et ses réglages sur ce téléphone seront remplacés. Ce qu'il y a maintenant est gardé de côté une fois, au cas où. Il faudra refaire l'appairage Capri Link.")
    }

    fun apply(capri: Capri, files: Map<String, ByteArray>) {
        val root = capri.folder
        val aside = File(root.parentFile, "capri-before-restore")
        aside.deleteRecursively()
        root.copyRecursively(aside, overwrite = true)
        root.listFiles()?.forEach { it.deleteRecursively() }
        val base = root.canonicalPath + File.separator
        for ((name, bytes) in files) {
            if (!name.startsWith("capri/")) continue
            val path = name.removePrefix("capri/")
            if (skipped(path) || !Backup.safeName(path)) continue
            val target = File(root, path)
            if (!target.canonicalPath.startsWith(base)) continue
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        // Settings: theirs from the backup; the pairing goes, so the first sync after a restore starts clean.
        val sp = capri.prefs.sp
        val edit = sp.edit()
        for (k in sp.all.keys) if (k.startsWith("link") || !local(k)) edit.remove(k)
        val saved = Json.parseOrNull(text(files["prefs.json"])).obj() ?: emptyMap()
        for ((k, v) in saved) {
            if (local(k)) continue
            val pair = v.arr() ?: continue
            val value = pair.getOrNull(1)
            when (pair.getOrNull(0).str()) {
                "s" -> value.str()?.let { edit.putString(k, it) }
                "i" -> value.num()?.let { edit.putInt(k, it.toInt()) }
                "b" -> (value as? Boolean)?.let { edit.putBoolean(k, it) }
                "l" -> value.num()?.let { edit.putLong(k, it.toLong()) }
                "f" -> value.num()?.let { edit.putFloat(k, it.toFloat()) }
                else -> { }
            }
        }
        edit.commit()
        capri.prefs.linkKey = null
        Json.parseOrNull(text(files["secrets.json"])).obj()?.let { secrets -> for (name in keyNames) secrets[name].str()?.let { capri.prefs.secrets.put(name, it) } }
    }

    // Everything she holds in memory was read before the restore: starting again is the sure way to read it all anew.
    fun restart(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.component
        if (launch != null) activity.startActivity(Intent.makeRestartActivityTask(launch))
        Runtime.getRuntime().exit(0)
    }
}
