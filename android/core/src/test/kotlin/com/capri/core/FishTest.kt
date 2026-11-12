package com.capri.core

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URL

class FishTest {
    // Stands in for api.fish.audio: keeps what it was asked, answers what the test tells it to.
    private class FakeFish(val status: Int, val reply: ByteArray) {
        @Volatile var method = ""
        @Volatile var path = ""
        @Volatile var auth: String? = null
        @Volatile var model: String? = null
        @Volatile var type: String? = null
        @Volatile var body = ""
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        init {
            server.createContext("/") { ex ->
                method = ex.requestMethod
                path = ex.requestURI.path
                auth = ex.requestHeaders.getFirst("Authorization")
                model = ex.requestHeaders.getFirst("model")
                type = ex.requestHeaders.getFirst("Content-Type")
                body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                // Length 0 means chunked, the way Fish Audio sends its audio.
                ex.sendResponseHeaders(status, 0)
                ex.responseBody.use { it.write(reply) }
            }
            server.start()
        }
        val base get() = "http://127.0.0.1:" + server.address.port
        fun stop() = server.stop(0)
    }

    // The real Http client sends the call, only the host is swapped for the fake.
    private fun send(fake: FakeFish, call: Fish.Call): HttpReply = Http().request("POST", fake.base + URL(call.url).path, call.headers, call.body, 5000)

    private fun sent(call: Fish.Call): Map<String, Any?> = Json.parse(String(call.body, Charsets.UTF_8)).obj()!!

    @Test fun theKeyTheVoiceAndTheModelReallyTravel() {
        val audio = ByteArray(40000) { (it % 251).toByte() }
        val fake = FakeFish(200, audio)
        try {
            val call = Fish.call("fa-test-key-0123456789", "Salut choom.", "https://fish.audio/m/802E3BC2B27E49C2995D23EF70E6AC89/?utm=x", "", 150)
            assertEquals("https://api.fish.audio/v1/tts", call.url)
            val reply = send(fake, call)
            assertEquals(200, reply.status)
            assertTrue("the whole chunked sound comes back", audio.contentEquals(reply.body))
            assertEquals("POST", fake.method)
            assertEquals("/v1/tts", fake.path)
            assertEquals("Bearer fa-test-key-0123456789", fake.auth)
            assertEquals("s2.1-pro-free", fake.model)
            assertTrue(fake.type!!.startsWith("application/json"))
            val asked = Json.parse(fake.body).obj()!!
            assertEquals("Salut choom.", asked["text"])
            assertEquals("802e3bc2b27e49c2995d23ef70e6ac89", asked["reference_id"])
            assertEquals("mp3", asked["format"])
            assertEquals(1.5, (asked["prosody"].obj()!!["speed"] as Number).toDouble(), 0.0001)
            assertFalse(asked.containsKey("mp3_bitrate"))
        } finally { fake.stop() }
    }

    @Test fun changingTheVoiceTheModelOrTheSpeedChangesWhatIsAsked() {
        val paid = Fish.call(" key-b \n", "Hi", "voice-b", " s2-pro ")
        assertEquals("s2-pro", paid.headers["model"])
        assertEquals("s2-pro", paid.model)
        assertEquals("Bearer key-b", paid.headers["Authorization"])
        assertEquals("voice-b", sent(paid)["reference_id"])
        assertFalse("100 % asks for no speed change", sent(paid).containsKey("prosody"))

        val plain = sent(Fish.call("k", "Hi", "  ", null))
        assertFalse("no voice picked: Fish Audio uses its own", plain.containsKey("reference_id"))

        assertNull(Fish.speed(100))
        assertEquals(0.5, Fish.speed(20)!!, 0.0001)
        assertEquals(0.8, Fish.speed(80)!!, 0.0001)
        assertEquals(2.0, Fish.speed(300)!!, 0.0001)
        assertEquals(0.5, (sent(Fish.call("k", "Hi", speedPercent = 10))["prosody"].obj()!!["speed"] as Number).toDouble(), 0.0001)

        assertEquals(64, (sent(Fish.call("k", "Hi", light = true))["mp3_bitrate"] as Number).toInt())
        assertEquals("s2.1-pro-free", Fish.modelName(null))
        assertEquals("s1", Fish.modelName(" s1 "))
        assertTrue(Fish.Known.contains(Fish.Free))
    }

    @Test fun voiceIdsAreReadFromLinksTheDesktopWay() {
        val id = "802e3bc2b27e49c2995d23ef70e6ac89"
        assertEquals(id, Fish.voiceId(id))
        assertEquals(id, Fish.voiceId("  $id  "))
        assertEquals(id, Fish.voiceId("https://fish.audio/m/$id"))
        assertEquals(id, Fish.voiceId("https://fish.audio/m/$id/"))
        assertEquals(id, Fish.voiceId("https://fish.audio/fr/m/" + id.uppercase() + "/?utm_source=share#top"))
        assertEquals(id, Fish.voiceId("fish.audio/m/$id"))
        assertEquals("my-voice", Fish.voiceId("https://example.org/voices/my-voice/?x=1"))
        assertEquals("", Fish.voiceId("Capri Default"))
        assertEquals("", Fish.voiceId(null))
    }

    @Test fun aPaidModelWithNoCreditFallsBackToTheFreeOne() {
        val fake = FakeFish(402, "{\"status\":402,\"message\":\"Insufficient balance\",\"reason\":null}".toByteArray())
        val french = Lang.french
        try {
            Lang.french = false
            val call = Fish.call("k", "Hi", null, "s2-pro")
            val reply = send(fake, call)
            assertEquals(402, reply.status)
            assertTrue(Fish.retryFree(reply.status, call.model))
            val why = Fish.problem(reply.status, reply.body, call.model, "k")
            assertTrue(why, why.contains("s2-pro") && why.contains("402") && why.contains(Fish.Free))
            // A name Fish Audio doesn't know becomes its paid default, so a typo is rescued the same way.
            assertTrue(Fish.retryFree(402, "s3-typo"))
            assertFalse("the free model has nothing to fall back to", Fish.retryFree(402, Fish.Free))
            assertFalse(Fish.retryFree(402, ""))
            assertFalse("a refused key is not a credit problem", Fish.retryFree(401, "s2-pro"))
            val free = Fish.problem(402, reply.body, "", "k")
            assertTrue(free, free.contains("free model") && free.contains("Insufficient balance"))
        } finally { Lang.french = french; fake.stop() }
    }

    @Test fun errorsSayWhatToDoAndNeverShowTheKey() {
        val key = "0123456789abcdef0123456789abcdef"
        val french = Lang.french
        try {
            Lang.french = true
            val refused = Fish.problem(401, "{\"status\":401,\"message\":\"Invalid token $key\"}".toByteArray(), "", key)
            assertTrue(refused, refused.contains("clé") && refused.contains("401"))
            assertFalse(refused.contains(key))
            assertTrue(Fish.problem(404, null, "", key).contains("voix"))
            assertTrue(Fish.problem(429, ByteArray(0), "", key).contains("429"))
            assertTrue(Fish.problem(503, "{\"status\":503,\"message\":\"High load\"}".toByteArray(), "", key).contains("503"))

            val other = Fish.problem(400, "{\"status\":400,\"message\":\"Reference  not found\\nfor token $key\",\"reason\":null}".toByteArray(), "", key)
            assertTrue(other, other.contains("HTTP 400") && other.contains("Reference not found"))
            assertFalse("the key is cut out of what Fish Audio said", other.contains(key))

            val bearer = Fish.problem(400, "bad header Authorization: Bearer abc.def-123".toByteArray(), "", null)
            assertFalse(bearer, bearer.contains("abc.def-123"))

            assertEquals("Fish Audio a répondu HTTP 418.", Fish.problem(418, "<html><body>teapot</body></html>".toByteArray(), "", key))
            val long = Fish.problem(400, ("x".repeat(500)).toByteArray(), "", key)
            assertTrue(long.length < 200 && long.endsWith("…"))

            Lang.french = false
            assertTrue(Fish.problem(403, null, "", key).contains("turned down the key"))
        } finally { Lang.french = french }
    }
}
