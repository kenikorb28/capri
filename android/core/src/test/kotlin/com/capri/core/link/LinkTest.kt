package com.capri.core.link

import com.capri.core.BrainError
import com.capri.core.Http
import com.capri.core.Json
import com.capri.core.Lang
import com.capri.core.Memory
import com.capri.core.SyncCrypto
import com.capri.core.arr
import com.capri.core.num
import com.capri.core.obj
import com.capri.core.str
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

private fun temp(name: String): File = Files.createTempDirectory("capri-$name").toFile()

class LinkTest {
    private fun contract(): File {
        val given = System.getProperty("capri.contract")
        val candidates = listOfNotNull(given, "../../contract", "../contract", "contract")
        return candidates.map { File(it) }.firstOrNull { File(it, "vectors/link-v2-crypto.json").exists() } ?: throw AssertionError("contract/ not found; run with -Dcapri.contract=<path>")
    }

    // ---- the shared crypto vector: the C# smoke check in Capri Desktop reproduces the same values (desktop/app/Link.cs)

    @Test fun pairingKeysMatchTheSharedVector() {
        val v = Json.parse(File(contract(), "vectors/link-v2-crypto.json").readText()).obj()!!
        fun hex(k: String) = SyncCrypto.unhex(v[k].str()!!)
        val pcPrivate = LinkCrypto.privateKey(hex("pcPrivate"))
        val phonePrivate = LinkCrypto.privateKey(hex("phonePrivate"))
        val pcPublic = LinkCrypto.decode(hex("pcPublic"))
        val phonePublic = LinkCrypto.decode(hex("phonePublic"))
        assertEquals(v["pcPublic"], SyncCrypto.hex(LinkCrypto.encode(pcPublic)))
        val fromPhone = LinkCrypto.sharedX(phonePrivate, pcPublic)
        val fromPc = LinkCrypto.sharedX(pcPrivate, phonePublic)
        assertEquals(v["sharedX"], SyncCrypto.hex(fromPhone))
        assertEquals(v["sharedX"], SyncCrypto.hex(fromPc))
        assertEquals(v["sharedHash"], SyncCrypto.hex(MessageDigest.getInstance("SHA-256").digest(fromPhone)))
        assertEquals(v["codeKey"], SyncCrypto.hex(SyncCrypto.derive(v["code"].str()!!, hex("salt"))))
        val pair = LinkCrypto.pairKey(fromPhone, v["code"].str()!!, hex("salt"), hex("pcPublic"), hex("phonePublic"))
        assertEquals(v["pairKey"], SyncCrypto.hex(pair))
        val long = LinkCrypto.longKey(pair)
        assertEquals(v["longKey"], SyncCrypto.hex(long))
        assertEquals(v["encKey"], SyncCrypto.hex(SyncCrypto.hmac(long, "capri-enc".toByteArray())))
        assertEquals(v["macKey"], SyncCrypto.hex(SyncCrypto.hmac(long, "capri-mac".toByteArray())))
        val sealed = SyncCrypto.seal(long, v["plaintext"].str()!!.toByteArray(Charsets.UTF_8), v["aad"].str()!!, hex("iv"))
        println("Link v2 sealed vector: $sealed")
        if (v["sealed"] != null) assertEquals(v["sealed"], sealed)
        assertEquals(v["plaintext"], LinkCrypto.open(long, sealed, LinkCrypto.Request))
        assertThrows(SecurityException::class.java) { LinkCrypto.open(long, sealed, LinkCrypto.Reply) }
        assertThrows(SecurityException::class.java) { LinkCrypto.open(pair, sealed, LinkCrypto.Request) }
    }

    // ---- the merges

    @Test fun aNoteForgottenOnOneSideStaysForgottenAndAddedOnesStay() {
        val head = "# What Capri remembers about you\n> She reads this every time you chat. Edit or delete anything here.\n\n"
        val base = head + "- Loves coffee\n- Prefers the evening\n"
        val pc = head + "- Prefers the evening\n- Their Android project is called Capri\n"
        val phone = head + "- Loves coffee\n- Prefers the evening\n- Loves tea\n"
        val merged = Merge.notes(base, phone, pc)
        assertEquals(head + "- Prefers the evening\n- Their Android project is called Capri\n- Loves tea\n", merged)
        assertEquals("before the first sync, both sides' lines are kept, once", head + "- Prefers the evening\n- Their Android project is called Capri\n- Loves coffee\n- Loves tea\n", Merge.notes(null, phone, pc))
        assertEquals("the same note written differently counts once", head + "-  loves  tea\n", Merge.notes(null, head + "- Loves Tea\n", head + "-  loves  tea\n"))
        assertEquals("nothing to change: the PC's file comes back exactly as it was", pc, Merge.notes(pc, pc.replace("\n\n", "\n"), pc))
        assertEquals("with no file on the PC yet, the phone's goes over", head + "- Loves tea\n", Merge.notes(null, head + "- Loves tea\n", ""))
    }

    @Test fun aLabelledLineKeepsOneValue() {
        val head = "# h\n"
        val base = head + "- Pronouns: he/him\n- Name: Kenny\n"
        val pc = head + "- Pronouns: they/them\n- Name: Kenny\n"
        val phone = head + "- Pronouns: he/him\n- Name: Kenny\n"
        assertEquals(pc, Merge.notes(base, phone, pc))
        val both = Merge.notes(base, head + "- Pronouns: she/her\n- Name: Kenny\n", pc, mineIsNewer = true)
        assertTrue(both.contains("she/her") && !both.contains("they/them") && !both.contains("he/him"))
    }

    @Test fun anObsidianNoteKeepsItsShape() {
        val vault = "# Moi\nCe que Capri sait de moi.\n\n## Jeux\n- Joue à Free Fire\n\n## Projets\n- Capri sur PC et Android\n\nÀ relire le dimanche.\n"
        val base = vault
        val phone = Memory.NotesHead + "Ce que Capri sait de moi.\n- Joue à Free Fire\n- Capri sur PC et Android\n- Aime le thé\nÀ relire le dimanche.\n"
        val merged = Merge.notes(base, phone, vault)
        assertEquals(vault.trimEnd() + "\n- Aime le thé\n", merged)
        val forgot = Merge.notes(merged, merged.replace("- Joue à Free Fire\n", ""), merged)
        assertEquals(merged.replace("- Joue à Free Fire\n", ""), forgot)
    }

    @Test fun personalityTravelsInThePcsWordsAndADefaultNeverWins() {
        val s = Setup()
        try {
            val phoneHome = "Now you live inside the user's phone."
            val pcHome = "Now you live inside the user's computer."
            s.sync.personalityToWire = { it.replace(phoneHome, pcHome) }
            s.sync.personalityFromWire = { it.replace(pcHome, phoneHome) }
            s.sync.personalityIsDefault = { it.contains("DEFAULT") }
            val mine = File(s.phoneFolder, "personality.md")
            s.sync.personality = mine
            mine.writeText("DEFAULT " + phoneHome + "\n")
            s.pc.personality.writeText("# Capri\nShe teases them. " + pcHome + "\n")
            s.sync.sync(s.link)
            assertEquals("their own personality on the PC replaced her default here, in the phone's words", "# Capri\nShe teases them. " + phoneHome + "\n", mine.readText())
            mine.writeText("# Capri\nShe teases them a lot. " + phoneHome + "\n")
            s.sync.sync(s.link)
            assertEquals("# Capri\nShe teases them a lot. " + pcHome + "\n", s.pc.personality.readText())
            s.pc.personality.delete()
            s.sync.sync(s.link)
            assertFalse("reset on the PC: her default here too", mine.exists())
            assertTrue(File(s.phoneFolder, "personality.backup.md").readText().contains("a lot"))
        } finally { s.close() }
    }

    @Test fun memoriesMergeByDayInEitherLanguage() {
        assertEquals("2026-03-03", Merge.dayKey("## Tuesday 3 March 2026"))
        assertEquals("2026-03-03", Merge.dayKey("## Mardi 3 mars 2026"))
        assertEquals("2026-11-05", Merge.dayKey("## Thu 5 Nov 2026"))
        assertEquals("earlier", Merge.dayKey("## Earlier"))
        val head = "# What Capri remembers from your chats\n> She writes this herself.\n\n"
        val base = head + "## Tuesday 3 March 2026\n- They started Capri Android\n"
        val pc = head + "## Tuesday 3 March 2026\n- They started Capri Android\n## Wednesday 4 March 2026\n- They fixed the PC build\n"
        val phone = head + "## Mardi 3 mars 2026\n- They started Capri Android\n- They tested the bubble on the TECNO\n## Earlier\n- They met Capri\n"
        val merged = Merge.memories(base, phone, pc)
        assertEquals(head + "## Earlier\n- They met Capri\n## Mardi 3 mars 2026\n- They started Capri Android\n- They tested the bubble on the TECNO\n## Wednesday 4 March 2026\n- They fixed the PC build\n", merged)
        // The PC folds its days into Earlier: the phone follows, and keeps what it added since.
        val folded = head + "## Earlier\n- They met Capri\n- They started Capri Android and fixed the PC build\n"
        val again = Merge.memories(merged, merged + "## Thursday 5 March 2026\n- They paired the phone\n", folded)
        assertEquals(head + "## Earlier\n- They met Capri\n- They started Capri Android and fixed the PC build\n## Thursday 5 March 2026\n- They paired the phone\n", again)
    }

    @Test fun projectsKeepTheirSectionsAndOnlyTheNewestLastTask() {
        val base = "# Projet : Capri\n\n## Le projet\n- Assistant PC et Android\n\n## Dernière tâche\n- 2026-11-05 10:00 : audit\n"
        val pc = "# Projet : Capri\n\n## Le projet\n- Assistant PC et Android\n\n## Dernière tâche\n- 2026-11-05 18:00 : Capri Link\n"
        val phone = "# Projet : Capri\n\n## Le projet\n- Assistant PC et Android\n- Même mémoire partout\n\n## Dernière tâche\n- 2026-11-05 12:00 : tests\n"
        assertEquals("# Projet : Capri\n\n## Le projet\n- Assistant PC et Android\n- Même mémoire partout\n\n## Dernière tâche\n- 2026-11-05 18:00 : Capri Link\n", Merge.project(base, phone, pc))
    }

    @Test fun wholeFilesGoToTheSideThatChangedThem() {
        assertEquals("pc", Merge.whole("old", "old", "pc", true).text)
        assertEquals("phone", Merge.whole("old", "phone", "old", false).text)
        assertEquals(null, Merge.whole("old", null, "old", true).text)
        val both = Merge.whole("old", "phone", "pc", false)
        assertEquals("pc", both.text)
        assertEquals("phone", both.other)
    }

    @Test fun journalLinesAreMarkedOnceAndNeverSentBack() {
        assertEquals("**14:02 You (PC):** salut", Journal.tag("**14:02 You:** salut", "PC"))
        assertEquals("**14:02 You (voice) (PC):** salut", Journal.tag("**14:02 You (voice):** salut", "PC"))
        assertEquals(null, Journal.tag("**14:02 You (phone):** salut", "PC"))
        assertTrue(Journal.own("**14:02 Capri (voice):** oui ?"))
        assertFalse(Journal.own("**14:02 Capri (PC):** oui ?"))
        assertFalse(Journal.own("**14:02 You (téléphone):** salut"))
        assertFalse(Journal.own("*14:02 She looked at your screen: code.*"))
    }

    // ---- a PC that follows contract/link-v2.md the way Capri Desktop's app/Link.cs does, so the phone's side runs for real

    class FakePc(val code: String, val folder: File) {
        val salt = ByteArray(16).also { java.util.Random(7).nextBytes(it) }
        val pairing = LinkCrypto.newPair()
        val phones = LinkedHashMap<String, ByteArray>()
        val seen = HashSet<String>()
        var tries = 0
        @Volatile var context: Map<String, Any?>? = null
        @Volatile var phoneContext: Map<String, Any?>? = null
        // Runs between a pull and its push, like Capri writing a note on the PC at that moment.
        @Volatile var meanwhile: (() -> Unit)? = null
        val notes = File(folder, "me.md")
        val memories = File(folder, "memories.md")
        val personality = File(folder, "personality.md")
        val projects = File(folder, "projects")
        val transcripts = File(folder, "transcripts")
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        fun sha(file: File) = LinkCrypto.sha(if (file.exists()) file.readText() else "")
        fun text(file: File): String? = if (file.exists()) file.readText() else null

        fun rev(): String {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            val files = listOf(notes, memories, personality, File(transcripts, "$day.md")) + (projects.listFiles()?.sortedBy { it.name } ?: emptyList())
            return LinkCrypto.sha(files.joinToString("|") { it.name + ":" + (if (it.exists()) sha(it) else "-") })
        }

        fun ownLines(days: Int): List<Map<String, Any?>> {
            val out = ArrayList<Map<String, Any?>>()
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            for (back in days - 1 downTo 0) {
                val day = format.format(Date(System.currentTimeMillis() - back * 86_400_000L))
                val lines = text(File(transcripts, "$day.md"))?.lines()?.filter { Journal.own(it) } ?: continue
                if (lines.isNotEmpty()) out.add(mapOf("day" to day, "lines" to lines))
            }
            return out
        }

        private fun pull(data: Map<String, Any?>): Map<String, Any?> {
            val days = (data["days"].num()?.toInt() ?: 3).coerceIn(1, 31)
            val reply = mapOf(
                "rev" to rev(),
                "notes" to mapOf("text" to (text(notes) ?: ""), "sha" to sha(notes), "at" to notes.lastModified()),
                "memories" to mapOf("text" to (text(memories) ?: ""), "sha" to sha(memories), "at" to memories.lastModified()),
                "projects" to (projects.listFiles()?.sortedBy { it.name }?.map { mapOf("name" to it.name, "text" to it.readText(), "sha" to sha(it)) } ?: emptyList()),
                "personality" to mapOf("text" to text(personality), "sha" to sha(personality), "at" to personality.lastModified()),
                "journal" to ownLines(days),
                "context" to context)
            meanwhile?.let { meanwhile = null; it() }
            return reply
        }

        private fun push(data: Map<String, Any?>): Map<String, Any?> {
            val expect = data["expect"].obj() ?: emptyMap()
            val conflicts = ArrayList<String>()
            if (data.containsKey("notes") && expect["notes"].str() != sha(notes)) conflicts.add("notes")
            if (data.containsKey("memories") && expect["memories"].str() != sha(memories)) conflicts.add("memories")
            val projectsIn = data["projects"].arr()?.mapNotNull { it.obj() } ?: emptyList()
            for (p in projectsIn) {
                val name = p["name"].str() ?: continue
                val file = File(projects, name)
                if ((p["expect"].str() ?: "") != (if (file.exists()) sha(file) else "")) conflicts.add("project:$name")
            }
            val personalityIn = data["personality"].obj()
            if (personalityIn != null && personalityIn["expect"].str() != sha(personality)) conflicts.add("personality")
            if (conflicts.isNotEmpty()) return mapOf("conflicts" to conflicts, "rev" to rev())
            data["notes"].str()?.let { notes.writeText(it) }
            data["memories"].str()?.let { memories.writeText(it) }
            for (p in projectsIn) {
                val file = File(projects, p["name"].str()!!)
                val t = p["text"].str()
                if (t == null) file.delete() else { projects.mkdirs(); file.writeText(t) }
            }
            if (personalityIn != null) { val t = personalityIn["text"].str(); if (t == null) personality.delete() else personality.writeText(t) }
            val wipe = data["wipe"].num()?.toLong() ?: 0L
            if (wipe > 0) {
                val last = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(wipe))
                transcripts.listFiles()?.filter { Regex("\\d{4}-\\d{2}-\\d{2}\\.md").matches(it.name) && it.name.removeSuffix(".md") <= last }?.forEach { it.delete() }
            }
            for (d in data["journal"].arr()?.mapNotNull { it.obj() } ?: emptyList()) {
                val day = d["day"].str() ?: continue
                val file = File(transcripts, "$day.md")
                transcripts.mkdirs()
                val have = (text(file) ?: "").lines().map { it.trim() }.toHashSet()
                val fresh = d["lines"].arr()?.mapNotNull { it.str()?.let { l -> Journal.tag(l, "phone") } }?.filter { have.add(it) } ?: emptyList()
                if (fresh.isNotEmpty()) file.appendText(fresh.joinToString("") { it + "\n" })
            }
            data["context"].obj()?.let { phoneContext = it }
            return mapOf("conflicts" to emptyList<String>(), "rev" to rev())
        }

        init {
            server.createContext("/") { ex ->
                val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                var status = 200
                val reply: String = try {
                    when (ex.requestURI.path) {
                        "/hello" -> Json.write(mapOf("app" to "Capri Link", "link" to 2, "version" to "2.0.0", "name" to "PC-TEST", "id" to "pc-test", "pairing" to true,
                            "salt" to SyncCrypto.hex(salt), "pub" to Base64.getEncoder().encodeToString(LinkCrypto.encode(pairing.public))))
                        "/pair" -> {
                            val o = Json.parse(body).obj()!!
                            val phonePublic = Base64.getDecoder().decode(o["pub"].str()!!)
                            val key = LinkCrypto.pairKey(LinkCrypto.sharedX(pairing.private, LinkCrypto.decode(phonePublic)), code, salt, LinkCrypto.encode(pairing.public), phonePublic)
                            val msg = try { Json.parse(LinkCrypto.open(key, o["box"].str()!!, LinkCrypto.PairRequest)).obj() } catch (e: SecurityException) { null }
                            if (msg == null) { tries++; status = 403; "" } else {
                                phones[msg["id"].str()!!] = LinkCrypto.longKey(key)
                                LinkCrypto.seal(key, Json.write(mapOf("ok" to true, "ts" to System.currentTimeMillis(), "id" to "pc-test", "name" to "PC-TEST")), LinkCrypto.PairReply)
                            }
                        }
                        "/api" -> {
                            val found = phones.entries.firstNotNullOfOrNull { (id, k) -> try { Triple(id, k, Json.parse(LinkCrypto.open(k, body, LinkCrypto.Request)).obj()!!) } catch (e: SecurityException) { null } }
                            if (found == null) { status = 401; "" } else {
                                val (id, k, msg) = found
                                val fresh = seen.add(msg["n"].str() ?: "") && Math.abs(System.currentTimeMillis() - (msg["ts"].num()?.toLong() ?: 0)) < 600_000 && msg["id"].str() == id
                                val data = msg["data"].obj() ?: emptyMap()
                                val answer = if (!fresh) mapOf("ok" to false, "ts" to System.currentTimeMillis(), "error" to "replayed or stale") else when (msg["op"].str()) {
                                    "ping" -> mapOf("ok" to true, "ts" to System.currentTimeMillis(), "data" to mapOf("name" to "PC-TEST", "version" to "2.0.0", "rev" to rev()))
                                    "pull" -> mapOf("ok" to true, "ts" to System.currentTimeMillis(), "data" to pull(data))
                                    "push" -> mapOf("ok" to true, "ts" to System.currentTimeMillis(), "data" to push(data))
                                    "wait" -> {
                                        val until = System.currentTimeMillis() + 1000L * (data["seconds"].num()?.toLong() ?: 25).coerceIn(1, 25)
                                        while (rev() == data["rev"].str() && System.currentTimeMillis() < until) Thread.sleep(50)
                                        mapOf("ok" to true, "ts" to System.currentTimeMillis(), "data" to mapOf("rev" to rev(), "changed" to (rev() != data["rev"].str())))
                                    }
                                    else -> mapOf("ok" to false, "ts" to System.currentTimeMillis(), "error" to "unknown op")
                                }
                                LinkCrypto.seal(k, Json.write(answer), LinkCrypto.Reply)
                            }
                        }
                        else -> { status = 404; "" }
                    }
                } catch (e: Exception) { status = 500; e.toString() }
                val bytes = reply.toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
            }
            server.executor = java.util.concurrent.Executors.newCachedThreadPool()
            server.start()
        }

        val address get() = "127.0.0.1:" + server.address.port

        // What Capri Desktop's Brain.Note does: one more line in me.md.
        fun note(fact: String) {
            if (!notes.exists()) notes.writeText(Memory.NotesHead)
            notes.appendText("- $fact\n")
        }

        fun say(who: String, text: String) {
            transcripts.mkdirs()
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            File(transcripts, "$day.md").appendText("**" + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date()) + " " + who + ":** " + text + "\n")
        }
    }

    private class Setup(code: String = "K7Q2M9XA") {
        val pc = FakePc(code, temp("pc"))
        val phoneFolder = temp("phone")
        val memory = Memory(phoneFolder)
        val client = LinkClient(Http())
        val sync = MemorySync(memory, client, File(phoneFolder, "link"))
        val link: LinkPc = client.pair(pc.address, "k7q2-m9xa", "TECNO CL6k", "phone-1")
        fun close() { pc.server.stop(0) }
    }

    @Test fun phonePairsByCodeAndOnlyItsKeyOpensTheLink() {
        Lang.french = true
        val pc = FakePc("AB12CD34", temp("pc"))
        try {
            val client = LinkClient(Http())
            val wrong = assertThrows(BrainError::class.java) { client.pair(pc.address, "ZZZZ9999", "TECNO CL6k", "phone-1") }
            assertTrue(wrong.message!!.startsWith("Code incorrect"))
            assertEquals(1, pc.tries)
            val link = client.pair(pc.address, "ab12-cd34", "TECNO CL6k", "phone-1")
            assertEquals(32, link.key.size)
            assertEquals("pc-test", link.pcId)
            assertEquals("PC-TEST", client.call(link, "ping")["name"])
            val stranger = LinkPc(link.address, SyncCrypto.randomKey(), "phone-1", "pc-test", "PC-TEST")
            assertThrows(LinkForgotten::class.java) { client.call(stranger, "ping") }
        } finally { pc.server.stop(0) }
    }

    @Test fun whatTheyTellThePcThePhoneKnowsAndForgettingGoesBothWays() {
        Lang.french = true
        val s = Setup()
        try {
            s.memory.note("Loves Free Fire")
            s.pc.note("Their Android project is called Capri")
            val first = s.sync.sync(s.link)
            assertTrue("the PC's note reached the phone", s.memory.notes().contains("Their Android project is called Capri"))
            assertTrue("the phone's note reached the PC", s.pc.notes.readText().contains("Loves Free Fire"))
            assertEquals(1, first.tries)

            // "Oublie ça" on the phone: gone on the PC, and the next sync doesn't bring it back.
            s.memory.unnote("Free Fire")
            s.sync.sync(s.link)
            assertFalse(s.pc.notes.readText().contains("Free Fire"))
            s.sync.sync(s.link)
            assertFalse(s.memory.notes().contains("Free Fire"))

            // Forgotten on the PC (they deleted the line in me.md): gone on the phone too.
            s.pc.notes.writeText(s.pc.notes.readText().replace("- Their Android project is called Capri\n", ""))
            s.sync.sync(s.link)
            assertFalse(s.memory.notes().contains("Capri"))
        } finally { s.close() }
    }

    @Test fun aNoteWrittenOnThePcDuringASyncIsNotLost() {
        Lang.french = true
        val s = Setup()
        try {
            s.memory.note("Wants the same Capri everywhere")
            s.pc.meanwhile = { s.pc.note("Codes Capri at night") }
            val done = s.sync.sync(s.link)
            assertEquals("the PC said its notes changed, so the phone merged again", 2, done.tries)
            assertTrue(s.pc.notes.readText().contains("Codes Capri at night") && s.pc.notes.readText().contains("Wants the same Capri everywhere"))
            assertTrue(s.memory.notes().contains("Codes Capri at night"))
        } finally { s.close() }
    }

    @Test fun memoriesProjectsPersonalityAndChatLogsTravelToo() {
        Lang.french = true
        val s = Setup()
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            s.pc.memories.writeText(Memory.MemoriesHead + "## Tuesday 3 March 2026\n- They built Capri Link\n")
            s.pc.personality.writeText("# Capri\nShe teases them about their code.\n")
            s.pc.say("You", "je vais continuer sur mon téléphone")
            s.pc.say("Capri", "Ok, je te suis.")
            s.memory.project("note", "Nova", "Même mémoire sur les deux appareils", "about")
            s.memory.logged("rappelle-moi le café", "C'est noté.")
            s.sync.personality = File(s.phoneFolder, "personality.md")
            s.pc.context = mapOf("device" to "pc", "name" to "PC-TEST", "at" to System.currentTimeMillis() - 120_000, "app" to "Visual Studio Code",
                "turns" to listOf(mapOf("role" to "user", "text" to "je vais continuer sur mon téléphone"), mapOf("role" to "assistant", "text" to "Ok, je te suis.")))
            s.sync.context = { mapOf("device" to "phone", "name" to "TECNO CL6k", "at" to System.currentTimeMillis(), "app" to "YouTube") }
            val done = s.sync.sync(s.link)

            assertTrue(s.memory.readMemories().contains("- They built Capri Link"))
            assertEquals("# Capri\nShe teases them about their code.\n", File(s.phoneFolder, "personality.md").readText())
            val phoneLog = File(s.memory.transcripts, "$today.md").readText()
            assertTrue(phoneLog.contains("You (PC):** je vais continuer sur mon téléphone"))
            assertTrue(phoneLog.contains("Capri (PC):** Ok, je te suis."))
            val pcLog = File(s.pc.transcripts, "$today.md").readText()
            assertTrue(pcLog.contains("You (phone):** rappelle-moi le café"))
            assertEquals(2, done.linesIn)
            assertTrue(File(s.pc.projects, "nova.md").readText().contains("Même mémoire sur les deux appareils"))
            assertEquals("YouTube", s.pc.phoneContext?.get("app"))
            val elsewhere = s.sync.elsewhere()
            assertTrue(elsewhere, elsewhere.contains("On their PC (PC-TEST), 2 min ago") && elsewhere.contains("Visual Studio Code") && elsewhere.contains("- They: je vais continuer sur mon téléphone"))

            // A second sync adds nothing twice, and lines it brought aren't sent back.
            val again = s.sync.sync(s.link)
            assertEquals(0, again.linesIn)
            assertEquals(1, Regex("rappelle-moi le café").findAll(File(s.pc.transcripts, "$today.md").readText()).count())
            assertEquals(1, Regex("je vais continuer").findAll(File(s.memory.transcripts, "$today.md").readText()).count())

            // The project forgotten on the PC goes from the phone too.
            File(s.pc.projects, "nova.md").delete()
            s.sync.sync(s.link)
            assertTrue(s.memory.projectNames().isEmpty())
        } finally { s.close() }
    }

    @Test fun forgettingEverythingOnThePhoneEmptiesThePcToo() {
        Lang.french = true
        val s = Setup()
        try {
            s.pc.note("Their Android project is called Capri")
            s.pc.say("You", "salut")
            s.memory.note("Loves tea")
            s.sync.sync(s.link)
            s.memory.forgetAll()
            assertTrue(s.memory.wipeFile.exists())
            s.sync.sync(s.link)
            assertFalse(s.pc.notes.readText().contains("project is called Capri"))
            assertFalse(s.pc.notes.readText().contains("tea"))
            assertTrue(s.pc.transcripts.listFiles()?.isEmpty() ?: true)
            assertFalse(s.memory.wipeFile.exists())
            assertTrue(s.memory.noteLines().isEmpty())
        } finally { s.close() }
    }

    @Test fun thePhoneHearsWhenThePcChanges() {
        Lang.french = true
        val s = Setup()
        try {
            s.sync.sync(s.link)
            val rev = s.client.call(s.link, "ping")["rev"].str()!!
            val quiet = s.sync.changed(s.link, rev, seconds = 1)
            assertFalse("nothing changed on the PC", quiet.first)
            Thread { Thread.sleep(300); s.pc.note("Just said this on the PC") }.start()
            val started = System.currentTimeMillis()
            val (changed, now) = s.sync.changed(s.link, rev)
            assertTrue(changed && now != rev)
            assertTrue("woke up as soon as the PC changed", System.currentTimeMillis() - started < 5000)
            s.sync.sync(s.link)
            assertTrue(s.memory.notes().contains("Just said this on the PC"))
        } finally { s.close() }
    }
}
