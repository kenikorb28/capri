package com.capri.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Capri Sync's lock, the same on the phone and in the Windows program (pc-sync/CapriSync.cs): a pairing code shown on
// the PC becomes a key by PBKDF2-HMAC-SHA256, and every message is AES-256-CBC then HMAC-SHA256 (encrypt-then-MAC), with
// a timestamp inside so an old message can't be played again. Only things available in .NET Framework 4.8 and Android.
object SyncCrypto {
    private val random = SecureRandom()
    const val Iterations = 20000

    fun code(text: String): String = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (p in parts) mac.update(p)
        return mac.doFinal()
    }

    // One 32-byte PBKDF2 block, written out so both sides compute it the same way.
    fun derive(code: String, salt: ByteArray, iterations: Int = Iterations): ByteArray {
        val password = code(code).toByteArray(Charsets.UTF_8)
        var u = hmac(password, salt, byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = hmac(password, u)
            for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
        }
        return t
    }

    // A fresh random IV every time; the iv parameter is only for the shared test vector the PC program checks itself against.
    fun seal(key: ByteArray, plain: ByteArray, aad: String, iv: ByteArray = ByteArray(16).also { random.nextBytes(it) }): String {
        require(iv.size == 16)
        val enc = hmac(key, "capri-enc".toByteArray())
        val macKey = hmac(key, "capri-mac".toByteArray())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"), IvParameterSpec(iv))
        val body = cipher.doFinal(plain)
        val tag = hmac(macKey, aad.toByteArray(Charsets.UTF_8), iv, body)
        return Base64.getEncoder().encodeToString(iv + body + tag)
    }

    fun open(key: ByteArray, sealed: String, aad: String): ByteArray {
        val all = try { Base64.getDecoder().decode(sealed.trim()) } catch (e: IllegalArgumentException) { throw SecurityException("Not a Capri Sync message.") }
        if (all.size < 16 + 16 + 32) throw SecurityException("Message too short.")
        val iv = all.copyOfRange(0, 16)
        val body = all.copyOfRange(16, all.size - 32)
        val tag = all.copyOfRange(all.size - 32, all.size)
        val enc = hmac(key, "capri-enc".toByteArray())
        val macKey = hmac(key, "capri-mac".toByteArray())
        if (!MessageDigest.isEqual(tag, hmac(macKey, aad.toByteArray(Charsets.UTF_8), iv, body))) throw SecurityException("Wrong key or a changed message.")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(enc, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(body)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format("%02x", it) }
    fun unhex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    fun randomKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }
}

class SyncLink(val address: String, val key: ByteArray, val phoneId: String)

// The phone's side of Capri Sync, over the home Wi-Fi only.
class SyncClient(private val http: Http) {
    companion object {
        const val Request = "capri-sync-v1 request"
        const val Reply = "capri-sync-v1 reply"
        const val Port = 47917

        fun base(address: String): String? {
            var a = address.trim().trimEnd('/')
            if (a.isEmpty()) return null
            if (!a.startsWith("http://")) a = "http://$a"
            val u = try { java.net.URL(a) } catch (e: Exception) { return null }
            if (!Http.isPrivateHost(u.host)) return null
            return u.protocol + "://" + u.host + ":" + (if (u.port > 0) u.port else Port)
        }
    }

    fun hello(address: String): Map<String, Any?> {
        val base = base(address) ?: throw BrainError(Lang.pick("Use your PC's address on your Wi-Fi, like 192.168.1.20.", "Mets l'adresse de ton PC sur ton Wi-Fi, comme 192.168.1.20."), true)
        val reply = http.request("GET", "$base/hello", emptyMap(), null, 6000)
        if (reply.status != 200) throw BrainError("Capri Sync: HTTP " + reply.status, true)
        return Json.parseOrNull(reply.text).obj() ?: throw BrainError(Lang.pick("That isn't Capri Sync.", "Ce n'est pas Capri Sync."), true)
    }

    // The code on the PC becomes a one-time key; the PC answers with a long key only this phone and it hold.
    fun pair(address: String, code: String, phoneName: String, phoneId: String): SyncLink {
        val base = base(address)!!
        val hello = hello(address)
        val salt = SyncCrypto.unhex(hello["salt"].str() ?: throw BrainError("Capri Sync sent no salt.", true))
        val codeKey = SyncCrypto.derive(code, salt)
        val body = Json.write(mapOf("op" to "pair", "ts" to System.currentTimeMillis(), "id" to phoneId, "data" to mapOf("phone" to phoneName)))
        val reply = http.request("POST", "$base/pair", mapOf("Content-Type" to "text/plain"), SyncCrypto.seal(codeKey, body.toByteArray(Charsets.UTF_8), Request).toByteArray(Charsets.UTF_8), 15000)
        if (reply.status == 403) throw BrainError(Lang.pick("Wrong code. Check the code in Capri Sync on your PC.", "Code incorrect. Vérifie le code affiché par Capri Sync sur ton PC."), true)
        if (reply.status != 200) throw BrainError("Capri Sync: HTTP " + reply.status, true)
        val answer = Json.parse(String(SyncCrypto.open(codeKey, reply.text, Reply), Charsets.UTF_8)).obj() ?: throw BrainError("Capri Sync: bad reply", true)
        val key = Base64.getDecoder().decode(answer.at("data", "key").str() ?: throw BrainError("Capri Sync refused.", true))
        return SyncLink(base, key, phoneId)
    }

    fun call(link: SyncLink, op: String, data: Map<String, Any?> = emptyMap(), timeout: Int = 20000): Map<String, Any?> {
        val body = Json.write(mapOf("op" to op, "ts" to System.currentTimeMillis(), "id" to link.phoneId, "data" to data))
        val reply = http.request("POST", link.address + "/api", mapOf("Content-Type" to "text/plain"), SyncCrypto.seal(link.key, body.toByteArray(Charsets.UTF_8), Request).toByteArray(Charsets.UTF_8), timeout)
        if (reply.status == 401 || reply.status == 403) throw BrainError(Lang.pick("Your PC doesn't know this phone anymore. Pair again.", "Ton PC ne reconnaît plus ce téléphone. Refais l'appairage."), true)
        if (reply.status != 200) throw BrainError("Capri Sync: HTTP " + reply.status, true)
        val answer = Json.parse(String(SyncCrypto.open(link.key, reply.text, Reply), Charsets.UTF_8)).obj() ?: throw BrainError("Capri Sync: bad reply", true)
        if (answer["ok"].bool() != true) throw BrainError("Capri Sync: " + (answer["error"].str() ?: "refused"), true)
        val ts = answer["ts"].num()?.toLong() ?: 0L
        if (Math.abs(System.currentTimeMillis() - ts) > 10 * 60_000L) throw BrainError(Lang.pick("Your PC's clock and your phone's are too far apart.", "L'horloge du PC et celle du téléphone sont trop différentes."), true)
        return answer["data"].obj() ?: emptyMap()
    }
}

object SyncMerge {
    // Her notes on both sides, every line kept once, in the order this side had them, the other side's new ones after.
    fun notes(mine: String, theirs: String): String {
        val head = mine.lines().takeWhile { !it.trim().startsWith("- ") }.joinToString("\n").ifBlank { Memory.NotesHead.trimEnd() }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (line in mine.lines() + theirs.lines()) {
            val t = line.trim()
            if (!t.startsWith("- ")) continue
            if (seen.add(t.lowercase())) out.add(t)
        }
        return head.trimEnd() + "\n\n" + out.joinToString("\n") + "\n"
    }

    // A transcript line written on the other device, tagged with where it came from so it's never sent back.
    fun tagged(line: String, from: String): String? {
        val m = Regex("^\\*\\*(\\d\\d:\\d\\d) ([^*]+?):\\*\\* (.*)$").find(line) ?: return null
        val who = m.groupValues[2]
        if (who.contains("(")) return null
        return "**" + m.groupValues[1] + " " + who + " (" + from + "):** " + m.groupValues[3]
    }

    fun own(line: String): Boolean = Regex("^\\*\\*\\d\\d:\\d\\d [^*(]+:\\*\\* ").containsMatchIn(line)
}
