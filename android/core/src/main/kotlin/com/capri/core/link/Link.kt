package com.capri.core.link

import com.capri.core.BrainError
import com.capri.core.Http
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.SyncClient
import com.capri.core.SyncCrypto
import com.capri.core.bool
import com.capri.core.num
import com.capri.core.obj
import com.capri.core.str
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

// Capri Link v2's lock (contract/link-v2.md). Pairing mixes an ECDH P-256 exchange with the code shown on the PC: someone who
// records it on the Wi-Fi gets nothing to guess the code from, unlike Capri Sync v1. Messages then use v1's seal unchanged:
// AES-256-CBC then HMAC-SHA256, each with its own label so a reply can't pass for a request.
object LinkCrypto {
    const val PairRequest = "capri-link-v2 pair request"
    const val PairReply = "capri-link-v2 pair reply"
    const val Request = "capri-link-v2 request"
    const val Reply = "capri-link-v2 reply"
    private val random = SecureRandom()

    val curve: ECParameterSpec by lazy { (newPair().public as ECPublicKey).params }

    fun newPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    // A public key as both apps send it: 0x04, then X and Y, 32 bytes each.
    fun encode(key: PublicKey): ByteArray {
        val w = (key as ECPublicKey).w
        return byteArrayOf(4) + fixed(w.affineX) + fixed(w.affineY)
    }

    fun decode(point: ByteArray): PublicKey {
        if (point.size != 65 || point[0] != 4.toByte()) throw SecurityException("Not a P-256 public key.")
        val spec = ECPublicKeySpec(ECPoint(BigInteger(1, point.copyOfRange(1, 33)), BigInteger(1, point.copyOfRange(33, 65))), curve)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    // Only for the shared test vector: a private key from its 32 bytes.
    fun privateKey(d: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, d), curve))

    // The X coordinate of the shared point, 32 bytes, the input the PC's ECDiffieHellmanCng hashes with SHA-256.
    fun sharedX(mine: PrivateKey, theirs: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(mine)
        agreement.doPhase(theirs, true)
        val z = agreement.generateSecret()
        return if (z.size == 32) z else fixed(BigInteger(1, z))
    }

    // K = HMAC-SHA256(SHA-256(Z), "capri-link-v2 pair" || PBKDF2(code) || PC key || phone key).
    fun pairKey(sharedX: ByteArray, code: String, salt: ByteArray, pcPublic: ByteArray, phonePublic: ByteArray): ByteArray {
        val hashed = MessageDigest.getInstance("SHA-256").digest(sharedX)
        return SyncCrypto.hmac(hashed, "capri-link-v2 pair".toByteArray(Charsets.UTF_8), SyncCrypto.derive(code, salt), pcPublic, phonePublic)
    }

    fun longKey(pairKey: ByteArray): ByteArray = SyncCrypto.hmac(pairKey, "capri-link-v2 long-term".toByteArray(Charsets.UTF_8))

    fun seal(key: ByteArray, json: String, label: String): String = SyncCrypto.seal(key, json.toByteArray(Charsets.UTF_8), label)

    fun open(key: ByteArray, sealed: String, label: String): String = String(SyncCrypto.open(key, sealed, label), Charsets.UTF_8)

    fun sha(text: String): String = SyncCrypto.hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    fun nonce(): String = SyncCrypto.hex(ByteArray(12).also { random.nextBytes(it) })

    fun fixed(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }
}

// The PC this phone is paired with: where it was last found, the long-term key, and who both sides are.
class LinkPc(val address: String, val key: ByteArray, val phoneId: String, val pcId: String, val pcName: String) {
    fun at(address: String) = LinkPc(address, key, phoneId, pcId, pcName)
}

// The PC no longer knows this phone (it was forgotten there): only pairing again helps.
class LinkForgotten(message: String) : BrainError(message, true)

// A PC found on the Wi-Fi by its answer to "CAPRI-LINK?".
class LinkFound(val address: String, val id: String, val name: String, val pairing: Boolean)

// The phone's side of Capri Link v2, over the home Wi-Fi or Tailscale (addresses on their own network only).
class LinkClient(private val http: Http) {
    companion object {
        const val Port = 47917
        const val Probe = "CAPRI-LINK?"

        // Only addresses on their own network or Tailscale: the same rule as Capri Sync.
        fun base(address: String): String? = SyncClient.base(address)
    }

    fun hello(address: String, timeout: Int = 4000): Map<String, Any?> {
        val base = base(address) ?: throw BrainError(Lang.pick("Use your PC's address on your Wi-Fi, like 192.168.1.20.", "Mets l'adresse de ton PC sur ton Wi-Fi, comme 192.168.1.20."), true)
        val reply = http.request("GET", "$base/hello", emptyMap(), null, timeout)
        if (reply.status != 200) throw BrainError("Capri Link: HTTP " + reply.status, true)
        val hello = Json.parseOrNull(reply.text).obj()
        if (hello == null || hello["app"].str() != "Capri Link") throw BrainError(Lang.pick("That isn't Capri on your PC, or it's older than 2.0.", "Ce n'est pas Capri sur ton PC, ou une version antérieure à 2.0."), true)
        return hello
    }

    // The code shown on the PC, valid five minutes and five tries. Nothing secret travels: a recorded pairing can't be cracked later.
    fun pair(address: String, code: String, phoneName: String, phoneId: String): LinkPc {
        val base = base(address) ?: throw BrainError(Lang.pick("Use your PC's address on your Wi-Fi, like 192.168.1.20.", "Mets l'adresse de ton PC sur ton Wi-Fi, comme 192.168.1.20."), true)
        val hello = hello(address)
        if (hello["pairing"].bool() != true) throw BrainError(Lang.pick("On the PC, open Capri's tray menu > Capri Link > Add a phone first.", "Sur le PC, ouvre d'abord le menu de Capri > Capri Link > Ajouter un téléphone."), true)
        val pcPublic = Base64.getDecoder().decode(hello["pub"].str() ?: throw BrainError("Capri Link sent no key.", true))
        val salt = SyncCrypto.unhex(hello["salt"].str() ?: throw BrainError("Capri Link sent no salt.", true))
        val mine = LinkCrypto.newPair()
        val phonePublic = LinkCrypto.encode(mine.public)
        val key = LinkCrypto.pairKey(LinkCrypto.sharedX(mine.private, LinkCrypto.decode(pcPublic)), code, salt, pcPublic, phonePublic)
        val ask = Json.write(mapOf("op" to "pair", "ts" to System.currentTimeMillis(), "id" to phoneId, "name" to phoneName))
        val body = Json.write(mapOf("pub" to Base64.getEncoder().encodeToString(phonePublic), "box" to LinkCrypto.seal(key, ask, LinkCrypto.PairRequest)))
        val reply = http.request("POST", "$base/pair", mapOf("Content-Type" to "application/json"), body.toByteArray(Charsets.UTF_8), 15000)
        if (reply.status == 403) throw BrainError(Lang.pick("Wrong or expired code. Check the code Capri Link shows on your PC.", "Code incorrect ou expiré. Vérifie le code affiché par Capri Link sur ton PC."), true)
        if (reply.status != 200) throw BrainError("Capri Link: HTTP " + reply.status, true)
        val answer = try { Json.parse(LinkCrypto.open(key, reply.text, LinkCrypto.PairReply)).obj() } catch (e: Exception) { null }
            ?: throw BrainError(Lang.pick("Capri Link sent an answer that doesn't open.", "Capri Link a envoyé une réponse illisible."), true)
        if (answer["ok"].bool() != true) throw BrainError("Capri Link: " + (answer["error"].str() ?: "refused"), true)
        return LinkPc(base, LinkCrypto.longKey(key), phoneId, answer["id"].str() ?: hello["id"].str() ?: "pc", answer["name"].str() ?: hello["name"].str() ?: "PC")
    }

    fun call(pc: LinkPc, op: String, data: Map<String, Any?> = emptyMap(), timeout: Int = 20000): Map<String, Any?> {
        val ask = Json.write(mapOf("op" to op, "ts" to System.currentTimeMillis(), "id" to pc.phoneId, "n" to LinkCrypto.nonce(), "data" to data))
        val reply = http.request("POST", pc.address + "/api", mapOf("Content-Type" to "text/plain"), LinkCrypto.seal(pc.key, ask, LinkCrypto.Request).toByteArray(Charsets.UTF_8), timeout)
        if (reply.status == 401 || reply.status == 403) throw LinkForgotten(Lang.pick("Your PC doesn't know this phone anymore. Pair again.", "Ton PC ne reconnaît plus ce téléphone. Refais l'appairage."))
        if (reply.status != 200) throw BrainError("Capri Link: HTTP " + reply.status, true)
        val answer = try { Json.parse(LinkCrypto.open(pc.key, reply.text, LinkCrypto.Reply)).obj() } catch (e: Exception) { null }
            ?: throw BrainError(Lang.pick("Capri Link sent an answer that doesn't open.", "Capri Link a envoyé une réponse illisible."), true)
        val ts = answer["ts"].num()?.toLong() ?: 0L
        if (Math.abs(System.currentTimeMillis() - ts) > 10 * 60_000L) throw BrainError(Lang.pick("Your PC's clock and your phone's are too far apart.", "L'horloge du PC et celle du téléphone sont trop différentes."), true)
        if (answer["ok"].bool() != true) throw BrainError("Capri Link: " + (answer["error"].str() ?: "refused"), true)
        return answer["data"].obj() ?: emptyMap()
    }

    // "CAPRI-LINK?" to the Wi-Fi's broadcast address: every Capri 2.x PC answers with its id, name and whether a code is showing.
    fun discover(targets: List<InetAddress> = listOf(InetAddress.getByName("255.255.255.255")), waitMs: Int = 1500): List<LinkFound> {
        val found = LinkedHashMap<String, LinkFound>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val probe = Probe.toByteArray(Charsets.US_ASCII)
            for (target in targets) try { socket.send(DatagramPacket(probe, probe.size, target, Port)) } catch (_: Exception) { }
            val until = System.currentTimeMillis() + waitMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < until) {
                val packet = DatagramPacket(buffer, buffer.size)
                try { socket.receive(packet) } catch (e: SocketTimeoutException) { continue }
                val host = packet.address?.hostAddress ?: continue
                if (!Http.isPrivateHost(host)) continue
                val o = Json.parseOrNull(String(packet.data, 0, packet.length, Charsets.UTF_8)).obj() ?: continue
                if (o["app"].str() != "Capri Link") continue
                val id = o["id"].str() ?: continue
                found[id] = LinkFound(host, id, o["name"].str() ?: "PC", o["pairing"].bool() == true)
            }
        }
        return found.values.toList()
    }
}
