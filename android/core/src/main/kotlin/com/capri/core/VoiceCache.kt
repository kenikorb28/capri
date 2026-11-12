package com.capri.core

import java.io.File
import java.security.MessageDigest

// CACHE VOCAL: the sound of short lines a cloud voice has already said, kept on the phone. The same line with the same
// voice plays again with no call, no wait and no network. Only short lines are kept (the ones she repeats), the oldest
// leave when the folder is full, and the folder lives in the app's cache, which Android may empty by itself.
class VoiceCache(val folder: File, private val maxBytes: Long = 24L * 1024 * 1024, private val maxChars: Int = 160) {
    fun worth(text: String): Boolean = text.isNotBlank() && text.length <= maxChars

    // What makes two lines sound the same: the service, its voice settings (mark) and the words.
    fun key(kind: String, mark: String, text: String): String =
        MessageDigest.getInstance("SHA-256").digest((kind + "\u0001" + mark + "\u0001" + text.trim()).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun fileOf(key: String) = File(folder, "$key.mp3")

    fun get(key: String): File? = synchronized(this) {
        val f = fileOf(key)
        if (!f.isFile || f.length() < 200) return null
        f.setLastModified(System.currentTimeMillis())
        f
    }

    fun put(key: String, sound: ByteArray): File? = synchronized(this) {
        if (sound.size < 200 || sound.size > maxBytes / 4) return null
        try {
            folder.mkdirs()
            val fresh = File(folder, "$key.new")
            fresh.writeBytes(sound)
            val f = fileOf(key)
            if (f.exists()) f.delete()
            if (!fresh.renameTo(f)) { fresh.delete(); return null }
            trim(f)
            f
        } catch (e: Exception) { null }
    }

    private fun kept(): List<File> = (folder.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".mp3") }

    private fun trim(keep: File) {
        val files = kept().sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (total > maxBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            if (oldest == keep) continue
            total -= oldest.length()
            oldest.delete()
        }
    }

    fun count(): Int = synchronized(this) { kept().size }
    fun bytes(): Long = synchronized(this) { kept().sumOf { it.length() } }
    fun clear(): Int = synchronized(this) { val files = kept(); files.forEach { it.delete() }; files.size }
}
