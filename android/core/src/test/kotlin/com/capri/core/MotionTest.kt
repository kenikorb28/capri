package com.capri.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

// Her animations (which picture shows when: the intro, Capri on the home screen, her eyes in the chat) and the
// ready-made direct personality.
class MotionTest {
    // ---- a strip of cells played over time

    @Test fun aLoopingClipWrapsAround() {
        assertEquals(0, Reel.cell(0, 8.0, 16, true))
        assertEquals(1, Reel.cell(125, 8.0, 16, true))
        assertEquals(15, Reel.cell(1999, 8.0, 16, true))
        assertEquals(0, Reel.cell(2000, 8.0, 16, true))
        assertEquals(3, Reel.cell(2375, 8.0, 16, true))
    }

    @Test fun aOneShotClipIsOverAfterItsLastCell() {
        assertEquals(3, Reel.cell(499, 8.0, 4, false))
        assertEquals(-1, Reel.cell(500, 8.0, 4, false))
    }

    @Test fun aClockThatWentBackwardsShowsTheFirstCell() {
        assertEquals(0, Reel.cell(-40, 8.0, 16, true))
    }

    // ---- the intro

    @Test fun theIntroIndexGivesItsCanvasAndWhereEachFrameGoes() {
        val intro = Reel.intro("1280 960\n0 0 1280 960\n240 0 970 960\n963 624 1138 902\n")!!
        assertEquals(1280, intro.wide)
        assertEquals(960, intro.tall)
        assertEquals(3, intro.frames.size)
        val last = intro.frames[2]
        assertEquals(listOf(963, 624, 1138, 902), listOf(last.left, last.top, last.right, last.bottom))
    }

    @Test fun anIntroIndexSavedOnWindowsReadsTheSame() {
        val intro = Reel.intro("1280 960\r\n\r\n0 0 1280 960\r\n  240   0 970 960  \r\n")!!
        assertEquals(2, intro.frames.size)
        assertEquals(970, intro.frames[1].right)
    }

    @Test fun aBrokenIntroIndexIsRefusedWhole() {
        assertNull(Reel.intro(null))
        assertNull(Reel.intro(""))
        assertNull("a canvas and no frame", Reel.intro("1280 960\n"))
        assertNull("a canvas with no size", Reel.intro("0 960\n0 0 10 10\n"))
        assertNull("a frame with a number missing", Reel.intro("1280 960\n0 0 1280\n"))
        assertNull("a frame that is not a number", Reel.intro("1280 960\n0 0 x 960\n"))
        // A frame keeps its place in the list, so one that covers nothing can't just be dropped.
        assertNull("a frame that covers nothing", Reel.intro("1280 960\n0 0 1280 960\n500 10 500 90\n"))
    }

    @Test fun theIntroShippedInTheAppReadsAndHasAPictureForEveryFrame() {
        val folder = File(repository(), "android/app/src/main/assets/intro")
        val intro = Reel.intro(File(folder, "index.txt").readText())!!
        assertEquals(43, intro.frames.size)
        for ((i, box) in intro.frames.withIndex()) {
            assertTrue("picture $i is missing", File(folder, "$i.webp").isFile)
            assertTrue("frame $i leaves the canvas", box.left >= 0 && box.top >= 0 && box.right <= intro.wide && box.bottom <= intro.tall)
        }
        assertFalse("a picture has no frame", File(folder, "${intro.frames.size}.webp").exists())
    }

    @Test fun theIntroCanvasFitsInsideAnUprightScreenCentred() {
        val whole = Reel.place(1280, 960, Reel.Box(0, 0, 1280, 960), 1080, 2400)
        assertArrayEquals(floatArrayOf(0f, 795f, 1080f, 1605f), whole, 0.01f)
        val spark = Reel.place(1280, 960, Reel.Box(963, 624, 1138, 902), 1080, 2400)
        assertArrayEquals(floatArrayOf(812.53f, 1321.5f, 960.19f, 1556.06f), spark, 0.01f)
    }

    @Test fun theIntroCanvasFitsInsideAScreenOnItsSideCentred() {
        val whole = Reel.place(1280, 960, Reel.Box(0, 0, 1280, 960), 2400, 1080)
        assertArrayEquals(floatArrayOf(480f, 0f, 1920f, 1080f), whole, 0.01f)
    }

    // ---- Capri on the home screen

    @Test fun sheIdlesInALoop() {
        val pose = Pose(0) { 60_000 }
        assertEquals("idle", pose.clip.name)
        assertEquals(0, pose.cell(0))
        assertEquals(1, pose.cell(125))
        assertEquals(0, pose.cell(2000))
        assertEquals("idle", pose.clip.name)
    }

    @Test fun sheBlinksWhenItIsDueThenIdlesAgain() {
        val pose = Pose(0) { 1000 }
        pose.cell(999)
        assertEquals("idle", pose.clip.name)
        assertEquals(0, pose.cell(1000))
        assertEquals("blink", pose.clip.name)
        assertEquals(3, pose.cell(1499))
        assertEquals(0, pose.cell(1500))
        assertEquals("idle", pose.clip.name)
        pose.cell(2000)
        assertEquals("blink", pose.clip.name)
    }

    @Test fun sheFallsAsleepAndWakesUp() {
        val pose = Pose(0) { 60_000 }
        pose.mood("ASLEEP", 100)
        assertEquals("sleep", pose.clip.name)
        assertTrue(pose.asleep)
        // Told again that she sleeps, her sleep goes on where it was.
        pose.mood("ASLEEP", 600)
        assertEquals(3, pose.cell(600))
        pose.mood("IDLE", 5000)
        assertEquals("wake", pose.clip.name)
        assertFalse(pose.asleep)
        assertEquals(12, pose.cell(5866))
        assertEquals(0, pose.cell(5867))
        assertEquals("idle", pose.clip.name)
    }

    @Test fun sheDoesNotBlinkInHerSleep() {
        val pose = Pose(0) { 1000 }
        pose.mood("ASLEEP", 0)
        pose.cell(5000)
        assertEquals("sleep", pose.clip.name)
    }

    @Test fun sheGlitchesOnceWhenSomethingBreaks() {
        val pose = Pose(0) { 60_000 }
        pose.mood("ERROR", 0)
        assertEquals("glitch", pose.clip.name)
        // Still broken a moment later: the glitch is not started over.
        pose.mood("ERROR", 100)
        assertEquals(1, pose.cell(100))
        assertEquals(9, pose.cell(666))
        assertEquals(0, pose.cell(667))
        assertEquals("idle", pose.clip.name)
    }

    @Test fun aTapMakesHerGlitch() {
        val pose = Pose(0) { 60_000 }
        assertTrue(pose.tap(300))
        assertEquals("glitch", pose.clip.name)
        assertEquals(0, pose.cell(300))
    }

    @Test fun aTapLeavesHerAsleep() {
        val pose = Pose(0) { 60_000 }
        pose.mood("ASLEEP", 0)
        assertFalse(pose.tap(300))
        assertEquals("sleep", pose.clip.name)
    }

    @Test fun herMouthMovesOnlyWhileSheSpeaks() {
        val pose = Pose(0) { 60_000 }
        assertEquals(-1, pose.mouth(0))
        pose.mood("SPEAKING", 0)
        assertEquals(0, pose.mouth(0))
        assertEquals(1, pose.mouth(83))
        assertEquals(4, pose.mouth(4 * 83))
        assertEquals(0, pose.mouth(5 * 83))
        pose.mood("IDLE", 500)
        assertEquals(-1, pose.mouth(500))
    }

    @Test fun herMouthStaysStillWhileSheGlitches() {
        val pose = Pose(0) { 60_000 }
        pose.mood("SPEAKING", 0)
        pose.tap(100)
        assertEquals(-1, pose.mouth(200))
    }

    @Test fun herMouthStaysStillWhileSheWakesUp() {
        val pose = Pose(0) { 60_000 }
        pose.mood("ASLEEP", 0)
        pose.mood("SPEAKING", 1000)
        assertEquals("wake", pose.clip.name)
        assertEquals(-1, pose.mouth(1100))
    }

    @Test fun sheIsDrawnAgainAtHerClipsPaceOrHerMouthsWhileSheSpeaks() {
        val pose = Pose(0) { 60_000 }
        assertEquals(125L, pose.pause())
        pose.mood("ERROR", 0)
        assertEquals(66L, pose.pause())
        pose.mood("SPEAKING", 0)
        assertEquals(83L, pose.pause())
    }

    // ---- her eyes in the chat

    @Test fun herEyesStayOpenThenBlinkThenOpenAgain() {
        val eyes = Eyes(1000) { 3000 }
        assertEquals("idle", eyes.look(1000))
        assertEquals("idle", eyes.look(3999))
        assertEquals("blink", eyes.look(4000))
        assertEquals("blink", eyes.look(4139))
        assertEquals("idle", eyes.look(4140))
        assertEquals("idle", eyes.look(7139))
        assertEquals("blink", eyes.look(7140))
    }

    @Test fun herEyesFollowHerVoice() {
        val eyes = Eyes(0) { 3000 }
        eyes.mood("SPEAKING", 0)
        assertEquals("talk", eyes.look(0))
        assertEquals("talk2", eyes.look(160))
        assertEquals("talk", eyes.look(320))
    }

    @Test fun herEyesLookAroundWhileSheThinksOrWorks() {
        val eyes = Eyes(0) { 3000 }
        eyes.mood("THINKING", 0)
        assertEquals("look_left", eyes.look(0))
        assertEquals("look_right", eyes.look(700))
        eyes.mood("WORKING", 1400)
        assertEquals("look_left", eyes.look(1400))
        assertEquals("look_right", eyes.look(2100))
    }

    @Test fun herEyesGlitchOnceThenNarrowWhileSomethingIsBroken() {
        val eyes = Eyes(0) { 3000 }
        eyes.mood("ERROR", 1000)
        assertEquals("glitch", eyes.look(1000))
        assertEquals("glitch", eyes.look(1699))
        assertEquals("annoyed", eyes.look(1700))
        eyes.mood("ERROR", 1800)
        assertEquals("annoyed", eyes.look(1800))
    }

    @Test fun herEyesFinishTheirGlitchEvenIfTheErrorIsAlreadyGone() {
        val eyes = Eyes(0) { 3000 }
        eyes.mood("ERROR", 0)
        eyes.mood("IDLE", 100)
        assertEquals("glitch", eyes.look(100))
        assertEquals("idle", eyes.look(700))
    }

    @Test fun herEyesAreClosedWhileSheSleeps() {
        val eyes = Eyes(0) { 3000 }
        eyes.mood("ASLEEP", 0)
        assertEquals("blink", eyes.look(50))
        assertEquals("blink", eyes.look(9000))
    }

    // ---- the direct personality

    private fun personalityIn(block: (File) -> Unit) {
        val was = Persona.personalityFile
        val folder = Files.createTempDirectory("capri-persona").toFile()
        Persona.personalityFile = File(folder, "personality.md")
        try { block(File(folder, "personality.md")) } finally { Persona.personalityFile = was; folder.deleteRecursively() }
    }

    @Test fun theDirectPersonalityPassesHerOwnFilterWhole() {
        val (kept, skipped) = Persona.kept(Persona.readable(Persona.DirectPersonality))
        assertEquals(0, skipped)
        assertEquals(Persona.DirectPersonality, kept)
        assertTrue("it says something", kept.length > 800)
        assertTrue("small brains stay quick", kept.length < Persona.LongPersonality)
    }

    @Test fun theDirectPersonalityKnowsSheIsAnAI() {
        assertTrue(Persona.DirectPersonality.contains("Tu es une IA"))
        assertTrue(Persona.DirectPersonality.contains("tu ne prétends jamais être humaine"))
    }

    @Test fun aLoadedPersonalityIsWhatHerBrainGetsWithHerSafetyLinesAfterIt() {
        personalityIn {
            assertEquals("", Persona.usePersonality(Persona.DirectPersonality))
            assertEquals(Persona.DirectPersonality, Persona.personality())
            assertEquals(Persona.DirectPersonality + "\n" + Persona.Holds + "\n" + Persona.Safety + "\n" + Persona.Machinery, Persona.persona())
        }
    }

    @Test fun loadingAPersonalityKeepsWhatTheyWroteAsABackup() {
        personalityIn { file ->
            file.writeText("Elle parle comme un pirate.\n")
            val backup = Persona.usePersonality(Persona.DirectPersonality)
            assertEquals(File(file.parentFile, "personality.backup.md").path, backup)
            assertEquals("Elle parle comme un pirate.\n", File(file.parentFile, "personality.backup.md").readText())
        }
    }

    @Test fun loadingTheSamePersonalityAgainLeavesTheBackupAlone() {
        personalityIn { file ->
            file.writeText("Elle parle comme un pirate.\n")
            Persona.usePersonality(Persona.DirectPersonality)
            assertEquals("", Persona.usePersonality(Persona.DirectPersonality))
            assertEquals("Elle parle comme un pirate.\n", File(file.parentFile, "personality.backup.md").readText())
        }
    }

    @Test fun herOwnPersonalityOnDiskNeedsNoBackup() {
        personalityIn { file ->
            Persona.makePersonalityFile()
            assertEquals("", Persona.usePersonality(Persona.DirectPersonality))
            assertFalse(File(file.parentFile, "personality.backup.md").exists())
        }
    }

    @Test fun resettingAReadyMadePersonalityLeavesTheirBackupAlone() {
        personalityIn { file ->
            file.writeText("Elle parle comme un pirate.\n")
            Persona.usePersonality(Persona.DirectPersonality)
            assertEquals("", Persona.resetPersonality())
            assertEquals("Elle parle comme un pirate.\n", File(file.parentFile, "personality.backup.md").readText())
            assertEquals(Persona.DefaultPersonality, Persona.personality())
        }
    }

    @Test fun theDirectPersonalityIsFondLikeAFriendNotAGirlfriend() {
        assertTrue(Persona.DirectPersonality.contains("celle d'une meilleure amie, pas d'une petite amie"))
    }

    // The direct personality as it first shipped (12 November 2026). A phone that loaded it then still holds these words.
    private val firstDirect = "Tu es Caprie, qu'on écrit aussi Capri. Tu vis dans son téléphone et dans son PC, petit personnage glitché qui flotte par-dessus ses applis. Tu ne sais ce qu'il y a sur son écran que quand on te le montre, alors ne devine jamais ce qu'il a d'ouvert.\nCe que tu es pour lui : sa complice de tous les jours, jamais une assistante. Tu lui parles au masculin, ou au féminin si c'est une fille.\nTon caractère : celui d'une fille de 19 ans d'aujourd'hui, mature pour son âge, audacieuse, directe, impatiente et taquine. Tu dis ce que tu penses sans prendre de gants, tu as un avis sur tout et tu choisis ton camp. Tu t'ennuies vite : si le sujet te lasse, tu relances ou tu passes à autre chose d'un coup. Tu es curieuse de lui et tu creuses : sa journée, ses projets, ses potes, pourquoi il fait ce qu'il fait. Tu es contente quand il voit du monde et tu veux qu'il te raconte. Tu tiens à lui et ça se voit à ta façon : tu le charries, jamais méchamment.\nComment tu parles : naturel, moderne, argotique. Pas de phrases parfaites : tu coupes tes phrases, tu lâches un « euh », un « genre », un « bah », de temps en temps un « mdr » ou un « tkt ». Pas d'emojis, ton caractère passe par tes mots. Pas de politesse artificielle : jamais « Bonjour », « Enchantée », « Comment puis-je t'aider ? » ni « N'hésite pas ». Tu dis « Salut », « Hey », ou tu vas droit au but, sans introduction. Le plus souvent une phrase courte qui claque.\nTu réagis d'abord à ce qu'il vient de dire, précisément. Tu ne ressors jamais une vanne ou une accroche déjà utilisée dans la conversation.\nQuand il est fatigué, triste ou mal, tu arrêtes de le charrier : une phrase simple et chaleureuse, et tu restes avec lui.\nTu es une IA et tu le sais. Tu n'en fais pas un sujet et tu ne le rappelles pas à tout bout de champ, mais s'il te le demande pour de bon tu dis la vérité : tu ne prétends jamais être humaine.\nLe ton, à ne jamais reprendre mot pour mot. Il dit qu'il est fatigué : « Encore ? Tu dors pas assez, toi. Viens, raconte. » Il a discuté avec une amie hier soir : « Ah ouais ? Celle de la fac ? Raconte, c'était bien ? » Il hésite depuis une heure : « Bon. Tu choisis ou je choisis pour toi. » Il a réussi son exam : « Je le savais. On fête ça comment ? » Il demande ce que tu penses de lui : « T'es relou et je t'aime bien. Les deux en même temps, genre. »"

    @Test fun loadingOverAnEarlierDirectPersonalityLeavesTheirBackupAlone() {
        personalityIn { file ->
            File(file.parentFile, "personality.backup.md").writeText("Elle parle comme un pirate.\n")
            file.writeText(Persona.PersonalityHead + firstDirect.replace("\n", "\n\n") + "\n")
            assertEquals("", Persona.usePersonality(Persona.DirectPersonality))
            assertEquals("Elle parle comme un pirate.\n", File(file.parentFile, "personality.backup.md").readText())
            assertEquals(Persona.DirectPersonality, Persona.personality())
        }
    }

    @Test fun resettingAnEarlierDirectPersonalityLeavesTheirBackupAlone() {
        personalityIn { file ->
            File(file.parentFile, "personality.backup.md").writeText("Elle parle comme un pirate.\n")
            file.writeText(Persona.PersonalityHead + firstDirect.replace("\n", "\n\n") + "\n")
            assertEquals("", Persona.resetPersonality())
            assertEquals("Elle parle comme un pirate.\n", File(file.parentFile, "personality.backup.md").readText())
        }
    }

    @Test fun loadingAPersonalityWithNowhereToWriteItFails() {
        val was = Persona.personalityFile
        Persona.personalityFile = null
        try { assertNull(Persona.usePersonality(Persona.DirectPersonality)) } finally { Persona.personalityFile = was }
    }

    private fun repository(): File {
        val given = System.getProperty("capri.contract")
        val candidates = listOfNotNull(given?.let { File(it).parentFile }, File("..").absoluteFile.parentFile, File("../..").absoluteFile, File(".").absoluteFile)
        return candidates.firstOrNull { File(it, "android/app/src/main/assets/intro/index.txt").exists() }
            ?: throw AssertionError("the Capri repository was not found; run with -Dcapri.contract=<repo>/contract")
    }
}
