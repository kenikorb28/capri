package com.capri.core

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale

// The November additions: encrypted backup, voice cache, usage and limits, corrections, the tool guard and the self-check.
class AddsTest {
    private fun temp(): File = Files.createTempDirectory("capri-adds").toFile()

    private fun <T> speaking(french: Boolean, block: () -> T): T {
        val was = Lang.french
        Lang.french = french
        try { return block() } finally { Lang.french = was }
    }

    private fun refused(what: String, block: () -> Unit) {
        try { block() } catch (e: BackupError) { return }
        fail(what)
    }

    @Test fun aBackupOpensOnlyWithItsPassword() {
        val files = linkedMapOf(
            "manifest.json" to "{\"format\":1}".toByteArray(),
            "capri/me.md" to "- Aime le thé à la menthe\n".toByteArray(),
            "capri/projects/Capri été.md" to "# Projet".toByteArray(),
            "capri/empty.txt" to ByteArray(0),
            "capri/blob.bin" to ByteArray(5000) { (it * 7).toByte() })
        val sealed = Backup.pack(files, "mot de passe long", 10_000)
        assertFalse("nothing readable is left in the file", String(sealed, Charsets.ISO_8859_1).contains("menthe"))
        val back = Backup.unpack(sealed, "mot de passe long")
        assertEquals(files.keys.toList(), back.keys.toList())
        for ((name, bytes) in files) assertTrue(name, bytes.contentEquals(back[name]))
        assertFalse("sealed twice, the same files never give the same bytes", sealed.contentEquals(Backup.pack(files, "mot de passe long", 10_000)))
        refused("a wrong password opened it") { Backup.unpack(sealed, "mot de passe faux") }
        refused("a short password was taken") { Backup.pack(files, "court", 10_000) }
        refused("an empty backup was made") { Backup.pack(emptyMap(), "mot de passe long", 10_000) }
        assertTrue(Backup.strong("12345678"))
        assertFalse(Backup.strong("1234567"))
    }

    @Test fun aDamagedOrForeignBackupIsRefused() {
        val sealed = Backup.pack(mapOf("a.txt" to "hello".toByteArray()), "password-123", 10_000)
        for (at in listOf(3, 10, 20, 35, sealed.size - 1)) {
            val bad = sealed.clone()
            bad[at] = (bad[at] + 1).toByte()
            refused("a changed byte at $at went through") { Backup.unpack(bad, "password-123") }
        }
        refused("a cut file opened") { Backup.unpack(sealed.copyOf(sealed.size - 5), "password-123") }
        refused("any file opened") { Backup.unpack("not a backup at all, just some text that is long enough to try".toByteArray(), "password-123") }
        // A name that would leave the folder is refused when packing, and when a crafted file is opened.
        for (name in listOf("../evil", "/etc/passwd", "a/../../b", "a\\b", "C:/x", "a//b", "")) {
            assertFalse(name, Backup.safeName(name))
            refused("packed $name") { Backup.pack(mapOf(name to ByteArray(1)), "password-123", 10_000) }
        }
        assertTrue(Backup.safeName("capri/transcripts/2026-11-12.md"))
        val crafted = Backup.seal(Backup.zip(mapOf("../evil" to "x".toByteArray())), "password-123", 10_000)
        refused("a file outside the folder came out") { Backup.unpack(crafted, "password-123") }
    }

    @Test fun herVoiceCacheReplaysShortLinesAndDropsTheOldest() {
        val dir = File(temp(), "voice-cache")
        val cache = VoiceCache(dir, maxBytes = 4000, maxChars = 40)
        assertTrue(cache.worth("Oui ?"))
        assertFalse(cache.worth("  "))
        assertFalse(cache.worth("x".repeat(41)))
        val mark = "voice-a|s2.1-pro-free|110|false"
        val k1 = cache.key("fish", mark, "Salut choom.")
        assertTrue(Regex("[0-9a-f]{64}").matches(k1))
        assertEquals(k1, cache.key("fish", mark, " Salut choom. "))
        assertNotEquals(k1, cache.key("fish", "voice-b|s2.1-pro-free|110|false", "Salut choom."))
        assertNotEquals(k1, cache.key("elevenlabs", mark, "Salut choom."))
        assertNotEquals(k1, cache.key("fish", mark, "Salut choom !"))
        assertNull(cache.get(k1))
        fun sound(seed: Int) = ByteArray(900) { (it + seed).toByte() }
        assertNull("too small to be a sound", cache.put(k1, ByteArray(50)))
        assertNull("too big to keep", cache.put(k1, ByteArray(1500)))
        val first = cache.put(k1, sound(1))!!
        assertEquals(dir, first.parentFile)
        assertTrue(sound(1).contentEquals(cache.get(k1)!!.readBytes()))
        val keys = (2..4).map { cache.key("fish", mark, "line $it") }
        for ((i, k) in keys.withIndex()) cache.put(k, sound(i + 2))!!.setLastModified(2000L + i * 1000L)
        first.setLastModified(1000L)
        assertEquals(4, cache.count())
        assertEquals(3600L, cache.bytes())
        // A fifth line doesn't fit: the one she said longest ago leaves.
        val fifth = cache.key("fish", mark, "line 5")
        cache.put(fifth, sound(5))
        assertNull(cache.get(k1))
        assertEquals(4, cache.count())
        // Saying a line again keeps it: the next to leave is the oldest one she hasn't said since.
        cache.get(keys[0])
        cache.put(cache.key("fish", mark, "line 6"), sound(6))
        assertTrue(cache.get(keys[0]) != null)
        assertNull(cache.get(keys[1]))
        assertEquals(4, cache.clear())
        assertEquals(0, cache.count())
    }

    @Test fun usageCountsByMonthAndKnowsWhenALimitIsReached() {
        val dir = temp()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val november = day.parse("2026-11-12")!!
        val december = day.parse("2026-12-01")!!
        val usage = Usage(dir)
        usage.brain("nvidia", Tally(1000, 200, true), november)
        usage.brain("nvidia", Tally(500, 100, false), november)
        usage.brain("pc", Tally(9000, 900, true), november)
        usage.voice("fish", 120, november)
        usage.voice("pc", 5000, november)
        usage.saved("fish", 40, november)
        assertEquals(1800L, usage.tokens(november))
        assertEquals(120L, usage.chars(november))
        assertEquals("a new month starts from nothing", 0L, usage.tokens(december))
        assertTrue(Usage.over(1800, usage.tokens(november)))
        assertFalse(Usage.over(1801, usage.tokens(november)))
        assertFalse("0 means no limit", Usage.over(0, usage.tokens(november)))
        assertEquals(90, Usage.share(2000, 1800))
        val again = Usage(dir)
        assertEquals("it survives a restart", 1800L, again.tokens(november))
        val fr = speaking(true) { again.report(2000, 1000, Usage.price("0,50"), Usage.price("15"), november) }
        assertTrue(fr, fr.contains("Cerveau, NVIDIA : 2 demandes, 1 800 jetons (1 500 lus, 300 écrits), en partie estimés"))
        assertTrue(fr, fr.contains("Cerveau, ton PC : 1 demande, 9 900 jetons") && fr.contains("(hors plafond)"))
        assertTrue(fr, fr.contains("Voix, Fish Audio : 1 phrase, 120 caractères ; 40 de plus évités par le cache vocal"))
        assertTrue(fr, fr.contains("Plafond cerveau : 1 800 sur 2 000 jetons (90 %)") && fr.contains("Plafond voix : 120 sur 1 000 caractères (12 %)"))
        assertTrue(fr, fr.contains("Coût estimé"))
        assertEquals("Nothing used this month.", speaking(false) { again.report(0, 0, null, null, december) })
        assertNull(Usage.price("gratuit"))
        assertNull(Usage.price("0"))
        assertEquals(0.5, Usage.price(" 0,5 ")!!, 1e-9)
        again.reset()
        assertEquals(0L, Usage(dir).tokens(november))
    }

    @Test fun aTallyReadsWhatEachProviderReports() {
        val openAi = Tally.read("{\"choices\":[],\"usage\":{\"prompt_tokens\":812,\"completion_tokens\":64,\"total_tokens\":876}}", 4000)
        assertTrue(openAi.exact)
        assertEquals(812L, openAi.input)
        assertEquals(64L, openAi.output)
        val claude = Tally.read("{\"content\":[],\"usage\":{\"input_tokens\":20,\"cache_read_input_tokens\":700,\"cache_creation_input_tokens\":80,\"output_tokens\":33}}", 4000)
        assertEquals(800L, claude.input)
        assertEquals(33L, claude.output)
        val gemini = Tally.read("{\"candidates\":[],\"usageMetadata\":{\"promptTokenCount\":150,\"candidatesTokenCount\":25}}", 4000)
        assertEquals(150L, gemini.input)
        assertEquals(25L, gemini.output)
        val ollama = Tally.read("{\"message\":{\"content\":\"ok\"},\"prompt_eval_count\":90,\"eval_count\":12}", 4000)
        assertEquals(90L, ollama.input)
        assertEquals(12L, ollama.output)
        val silent = Tally.read("{\"choices\":[{\"message\":{\"content\":\"" + "a".repeat(400) + "\"}}]}", 4000)
        assertFalse(silent.exact)
        assertEquals(1000L, silent.input)
        assertTrue(silent.output in 100L..120L)
        assertFalse(Tally.read("not json", 40).exact)
    }

    @Test fun herBrainsTellWhatAReplyCost() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            ex.requestBody.readBytes()
            val body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Salut choom.\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":321,\"completion_tokens\":12}}".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val backends = Backends(Http())
            backends.baseOverride = "http://127.0.0.1:" + server.address.port
            val seen = ArrayList<Pair<Source, Tally>>()
            backends.onUsage = { source, tally -> seen.add(source to tally) }
            val reply = backends.chat(Source("openai", null, "sk-test-0123456789abcdef", "gpt-test"), "You are Capri.", 0, listOf(Msg("user", "Salut")), null, 100, -1)
            assertTrue(reply.text, reply.text.contains("Salut"))
            assertEquals(1, seen.size)
            assertEquals("openai", seen[0].first.kind)
            assertEquals(321L, seen[0].second.input)
            assertEquals(12L, seen[0].second.output)
            assertTrue(seen[0].second.exact)
        } finally { server.stop(0) }
    }

    @Test fun correctionsAreNotesSheFollowsEveryTime() {
        speaking(true) {
            val memory = Memory(temp())
            assertEquals("", memory.corrected())
            val said = memory.correct("quand je dis la boutique, c'est ma boutique en ligne, pas un magasin")
            assertTrue(said, said.startsWith("C'est retenu"))
            memory.note("Aime le thé")
            memory.correct("réponds-moi toujours en français")
            assertEquals(listOf("quand je dis la boutique, c'est ma boutique en ligne, pas un magasin", "réponds-moi toujours en français"), memory.corrections())
            val prompt = memory.corrected()
            assertTrue(prompt, prompt.contains("follow each one") && prompt.contains("- réponds-moi toujours en français") && prompt.contains("la boutique"))
            assertFalse("an ordinary note is not a correction", prompt.contains("thé"))
            assertTrue("they are notes, shown with the rest", memory.notes().contains("Correction : réponds-moi toujours en français"))
            assertTrue(memory.correct("  ").contains("à la place"))
            memory.correct("my password is hunter2")
            assertEquals("what stays out of her notes stays out of her corrections", 2, memory.corrections().size)
        }
    }

    @Test fun aGuardCanStopAnyToolBeforeItRuns() {
        speaking(true) {
            val folder = temp()
            val ran = ArrayList<String>()
            val hands = object : Hands {
                override fun missing(tool: String): String? = null
                override fun run(tool: String, args: Args): String { ran.add(tool); return "Opened YouTube." }
            }
            val brain = Brain(folder, Backends(Http()), Memory(folder), Reminders(folder))
            brain.hands = hands
            val before = Security.mask
            try {
                Security.pick(Security.Trusted)
                brain.ask("ouvre YouTube")
                val plain = ran.size
                assertTrue("the plain ask runs with no guard", plain >= 1)
                brain.guard = { _, _ -> "Arrêt d'urgence actif." }
                val stopped = brain.ask("ouvre YouTube")
                assertEquals("nothing ran once the guard said no", plain, ran.size)
                assertTrue(stopped.text, stopped.text.contains("Arrêt d'urgence"))
            } finally { Security.mask = before }
        }
    }

    @Test fun plainPhrasesReachTheNewTools() {
        val kept = ExtraPhrases.correction("Tu t'es trompée : mon frère s'appelle Idriss, pas Driss")!!
        assertEquals("correction", kept.name)
        assertEquals("add", kept.args.action)
        assertEquals("mon frère s'appelle Idriss, pas Driss", kept.args.text)
        assertEquals("La prochaine fois, réponds plus court", ExtraPhrases.correction("La prochaine fois, réponds plus court")!!.args.text)
        assertEquals("answer in French", ExtraPhrases.correction("you got it wrong, answer in French")!!.args.text)
        assertEquals("list", ExtraPhrases.correction("quelles sont tes corrections ?")!!.args.action)
        assertNull("nothing to keep: her brain answers that one", ExtraPhrases.correction("tu t'es trompée"))
        assertNull(ExtraPhrases.correction("ouvre YouTube"))
        assertEquals("usage_report", ExtraPhrases.usage("combien j'ai consommé ce mois-ci ?")!!.name)
        assertEquals("usage_report", ExtraPhrases.usage("How much have I used?")!!.name)
        assertNull(ExtraPhrases.usage("combien font 2 fois 3"))
        assertEquals("self_check", ExtraPhrases.check("Capri, vérifie-toi")!!.name)
        assertEquals("self_check", ExtraPhrases.check("run a self-test")!!.name)
        assertNull(ExtraPhrases.check("vérifie mes mails"))
        assertEquals("stop", ExtraPhrases.emergency("Arrêt d'urgence !")!!.args.action)
        assertEquals("resume", ExtraPhrases.emergency("tu peux reprendre")!!.args.action)
        assertNull("an ordinary stop is not the emergency stop", ExtraPhrases.emergency("arrête la musique"))
        assertNull(ExtraPhrases.emergency("reprends la musique"))
        assertEquals("backup", ExtraPhrases.backup("fais une sauvegarde de ta mémoire")!!.name)
        assertNull(ExtraPhrases.backup("sauvegarde ce fichier"))
    }

    @Test fun herSelfCheckSaysWhatWorksAndWhatDoesNot() {
        val list = listOf(Check("Cerveau", Check.OK, "NVIDIA répond."), Check("Voix", Check.BAD, "Fish Audio a refusé la clé (HTTP 401)."),
            Check("Accessibilité", Check.WARN, "coupée"), Check("Capri Link", Check.OFF, "aucun PC appairé"))
        val fr = speaking(true) { Checks.summary(list) }
        assertTrue(fr, fr.startsWith("Résultat : 1 sur 3."))
        assertTrue(fr, fr.contains("Ne marche pas : Voix (Fish Audio a refusé la clé (HTTP 401))."))
        assertTrue(fr, fr.contains("À regarder : Accessibilité (coupée)."))
        assertFalse("what they chose not to use is not a problem", fr.contains("Capri Link"))
        val board = speaking(true) { Checks.report(list) }.lines()
        assertEquals(4, board.size)
        assertEquals("🟢 Cerveau : NVIDIA répond.", board[0])
        assertTrue(board[3].startsWith("⚪ Capri Link"))
        assertEquals("Tout marche : 2 sur 2.", speaking(true) { Checks.summary(listOf(Check("a", Check.OK, "x"), Check("b", Check.OK, "y"))) })
        assertEquals("I had nothing to check.", speaking(false) { Checks.summary(emptyList()) })
    }
}
