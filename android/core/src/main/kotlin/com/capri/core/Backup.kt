package com.capri.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupError(message: String) : Exception(message)

// SAUVEGARDE: everything she keeps, in one file that only their password opens. The files are zipped, then sealed with
// AES-256-GCM under a key made from the password (PBKDF2-HMAC-SHA256, a fresh salt each time). A wrong password and a
// damaged file look the same and both are refused: nothing half-read ever comes out.
//   "CAPRIBK1" | version (1 byte) | iterations (4 bytes) | salt (16) | nonce (12) | sealed zip, the header signed with it
object Backup {
    private val Magic = "CAPRIBK1".toByteArray(Charsets.US_ASCII)
    private const val Head = 8 + 1 + 4 + 16 + 12
    const val Iterations = 210_000
    const val MaxBytes = 64L * 1024 * 1024
    const val MaxFiles = 20_000

    fun strong(password: String): Boolean = password.length >= 8

    // A name that can only land inside the folder it is restored into.
    fun safeName(name: String): Boolean = name.isNotEmpty() && name.length <= 300 && !name.startsWith("/") &&
        name.none { it == '\\' || it == ':' || it.code < 32 } && name.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun short() = Lang.pick("The password needs 8 characters or more.", "Il faut un mot de passe de 8 caractères ou plus.")
    private fun refused() = Lang.pick("Wrong password, or the backup file is damaged.", "Mot de passe incorrect, ou fichier de sauvegarde abîmé.")
    private fun foreign() = Lang.pick("That file isn't a Capri backup this version can read.", "Ce fichier n'est pas une sauvegarde Capri lisible par cette version.")
    private fun tooBig() = Lang.pick("That's more than a backup can hold (64 MB).", "C'est plus que ce qu'une sauvegarde peut contenir (64 Mo).")

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }

    fun pack(files: Map<String, ByteArray>, password: String, iterations: Int = Iterations): ByteArray {
        if (files.isEmpty() || files.size > MaxFiles || files.keys.any { !safeName(it) }) throw BackupError(Lang.pick("There's nothing I can put in a backup.", "Il n'y a rien que je puisse mettre dans une sauvegarde."))
        if (files.values.sumOf { it.size.toLong() } > MaxBytes) throw BackupError(tooBig())
        return seal(zip(files), password, iterations)
    }

    fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((name, bytes) in files) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return out.toByteArray()
    }

    fun seal(plain: ByteArray, password: String, iterations: Int = Iterations): ByteArray {
        if (!strong(password)) throw BackupError(short())
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val head = ByteBuffer.allocate(Head).put(Magic).put(1.toByte()).putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, nonce))
        cipher.updateAAD(head)
        return head + cipher.doFinal(plain)
    }

    fun unpack(sealed: ByteArray, password: String): Map<String, ByteArray> {
        if (sealed.size < Head + 16 || !sealed.copyOfRange(0, 8).contentEquals(Magic)) throw BackupError(foreign())
        val head = ByteBuffer.wrap(sealed, 8, Head - 8)
        val version = head.get().toInt()
        val iterations = head.int
        if (version != 1 || iterations !in 10_000..5_000_000) throw BackupError(foreign())
        val salt = ByteArray(16).also { head.get(it) }
        val nonce = ByteArray(12).also { head.get(it) }
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, nonce))
            cipher.updateAAD(sealed, 0, Head)
            cipher.doFinal(sealed, Head, sealed.size - Head)
        } catch (e: GeneralSecurityException) { throw BackupError(refused()) }
        return unzip(plain)
    }

    private fun unzip(plain: ByteArray): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(plain)).use { z ->
                val buffer = ByteArray(16384)
                while (true) {
                    val entry = z.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (!safeName(entry.name) || files.size >= MaxFiles) throw BackupError(refused())
                    val one = ByteArrayOutputStream()
                    while (true) {
                        val n = z.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MaxBytes) throw BackupError(tooBig())
                        one.write(buffer, 0, n)
                    }
                    files[entry.name] = one.toByteArray()
                }
            }
        } catch (e: java.io.IOException) { throw BackupError(refused()) }
        if (files.isEmpty()) throw BackupError(refused())
        return files
    }
}
