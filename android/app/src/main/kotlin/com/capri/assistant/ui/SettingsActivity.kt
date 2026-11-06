package com.capri.assistant.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.capri.assistant.overlay.OverlayService
import com.capri.core.Lang
import com.capri.core.Models
import com.capri.core.Persona
import com.capri.core.Problems
import com.capri.core.Security
import com.capri.core.Source
import com.capri.core.keyTail
import java.io.File

// Her settings, laid out like the desktop's Mainframe: BRAIN, VOICE, EARS, HEY CAPRI, MODES, POWER, BUBBLE,
// NOTIFICATIONS, EMERGENCY, CAPRI LINK, YOU and SECURITY. Every switch takes effect at once.
class SettingsActivity : NeonActivity() {
    private lateinit var f: Form
    private val prefs get() = capri.prefs

    override fun onCreate(saved: Bundle?) { super.onCreate(saved); neonStart() }
    override fun onResume() { super.onResume(); build() }

    private fun changed() { capri.apply(); if (OverlayService.running) capri.bubble("restyle") }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun bg(work: () -> String) { capri.io.execute { val r = try { work() } catch (e: Exception) { Problems.explain(e, capri.brain.source) ?: (e.message ?: "?") }; runOnUiThread { toast(r); build() } } }
    private fun pick(title: String, items: List<String>, then: (String) -> Unit) {
        if (items.isEmpty()) { toast(Lang.pick("Nothing to pick.", "Rien à choisir.")); return }
        AlertDialog.Builder(this).setTitle(title).setItems(items.toTypedArray()) { _, i -> then(items[i]) }.show()
    }
    private fun masked(key: String?) = if (key == null) Lang.pick("none", "aucune") else "••••" + keyTail(key)

    private fun build() {
        val y = fresh()
        brain(); voice(); ears(); hey(); vision(); modes(); power(); bubble(); notifications(); emergency(); sync(); you(); security()
        setContentView(f.scroll)
        padForBars(f.scroll)
        f.scroll.post { f.scroll.scrollTo(0, y) }
    }

    private fun fresh(): Int { val y = if (::f.isInitialized) f.scroll.scrollY else 0; f = Form(this); f.title(Lang.pick("SETTINGS", "RÉGLAGES"), capri.brainLabel()); return y }

    private fun brain() {
        f.section(Lang.pick("BRAIN", "CERVEAU"))
        val modes = listOf("local", "hybrid", "cloud")
        f.choice("Mode", listOf("LOCAL", Lang.pick("HYBRID", "HYBRIDE"), "CLOUD"), modes.indexOf(prefs.brainMode).coerceAtLeast(0),
            Lang.pick("LOCAL: Ollama on your PC + plain commands offline. HYBRID: your PC first, the cloud when it's away. CLOUD: a free or paid key.",
                "LOCAL : Ollama sur ton PC + commandes simples hors ligne. HYBRIDE : ton PC d'abord, le cloud s'il ne répond pas. CLOUD : une clé gratuite ou payante.")) { prefs.brainMode = modes[it]; changed(); build() }
        if (prefs.brainMode != "cloud") {
            f.field(Lang.pick("Your PC's server (Ollama, LM Studio…)", "Serveur de ton PC (Ollama, LM Studio…)"), "192.168.1.20:11434", prefs.server) { prefs.server = it; changed(); build() }
            f.value(Lang.pick("Model", "Modèle"), prefs.serverModel.ifEmpty { Lang.pick("pick one", "à choisir") }) {
                val s = Source.serverAddress(prefs.server)?.let { Source(if (it.endsWith(":11434")) "ollama" else "local", it, null, "") }
                if (s == null) toast(Lang.pick("Enter the server's address first.", "Entre d'abord l'adresse du serveur.")) else capri.io.execute {
                    val list = try { capri.backends.models(s) } catch (e: Exception) { runOnUiThread { toast(Problems.explain(e, s) ?: (e.message ?: "?")) }; return@execute }
                    runOnUiThread { pick(Lang.pick("Model on your PC", "Modèle sur ton PC"), list) { prefs.serverModel = it; changed(); build() } }
                }
            }
            f.note(Lang.pick("Plain http is fine on your own Wi-Fi; a key never goes to a public address over http.", "Le http simple va bien sur ton propre Wi-Fi ; une clé ne part jamais en http vers une adresse publique."))
        }
        if (prefs.brainMode != "local") {
            f.choice(Lang.pick("Cloud level", "Niveau cloud"), listOf("MEDIUM", "MAX"), prefs.tier - 1, Lang.pick("MEDIUM: free keys like NVIDIA or Gemini. MAX: a paid key.", "MEDIUM : clés gratuites comme NVIDIA ou Gemini. MAX : une clé payante.")) { prefs.tier = it + 1; changed(); build() }
            val medium = prefs.tier == 1
            val key = if (medium) prefs.mediumKey else prefs.maxKey
            f.field(Lang.pick("API key", "Clé API") + " (" + masked(key) + ")", "nvapi-… / AIza… / sk-…", "", secret = true, action = Lang.pick("Save", "Garder")) {
                if (it.isNotEmpty()) { if (medium) prefs.mediumKey = it else prefs.maxKey = it; changed(); build() }
            }
            val kind = key?.let { Source.kindOf(it) }
            f.value(Lang.pick("Model", "Modèle"), (if (medium) prefs.mediumModel else prefs.maxModel).ifEmpty { kind?.let { capri.defaultModel(it) } ?: Lang.pick("pick one", "à choisir") }) {
                val s = capri.cloudSource() ?: kind?.let { Source(it, null, key, "") }
                if (s == null) toast(Lang.pick("Paste a key first.", "Colle d'abord une clé.")) else capri.io.execute {
                    val list = try { capri.backends.models(s) } catch (e: Exception) { runOnUiThread { toast(Problems.explain(e, s) ?: (e.message ?: "?")) }; return@execute }
                    runOnUiThread { pick(Lang.pick("Model", "Modèle"), list) { if (medium) prefs.mediumModel = it else prefs.maxModel = it; changed(); build() } }
                }
            }
            f.toggle(Lang.pick("Stay on this model", "Rester sur ce modèle"), prefs.stayOnModel, Lang.pick("Off: with NVIDIA she switches to another model when one stalls.", "Désactivé : avec NVIDIA elle change de modèle si l'un bloque.")) { prefs.stayOnModel = it; changed() }
            f.note(Lang.pick("Keys are encrypted with the phone's Android Keystore and only their last 4 characters are ever shown.", "Les clés sont chiffrées par le Keystore Android du téléphone ; seuls leurs 4 derniers caractères sont affichés."))
        }
        f.buttons(Lang.pick("Test the brain", "Tester le cerveau") to {
            val s = capri.brain.source
            if (s == null) toast(Problems.Offline) else bg { val r = capri.brain.aside("Reply with one short friendly sentence in " + prefs.language + ".", "Say hi.", 60); r.text.ifBlank { Problems.Wordless } }
        })
        f.choice(Lang.pick("Replies", "Réponses"), listOf(Lang.pick("Short", "Courtes"), Lang.pick("Medium", "Moyennes"), Lang.pick("Long", "Longues")), prefs.wordiness) { prefs.wordiness = it; changed() }
        f.choice(Lang.pick("Chat memory", "Mémoire du chat"), listOf("8", "16", "32"), prefs.recall) { prefs.recall = it; changed() }
        f.toggle(Lang.pick("She works jobs out herself", "Elle enchaîne les étapes seule"), prefs.ownSteps, Lang.pick("Strong cloud brains call her tools one at a time.", "Les cerveaux cloud puissants appellent ses outils un par un.")) { prefs.ownSteps = it; changed() }
        f.toggle(Lang.pick("Second thoughts", "Petites relances"), prefs.followUps) { prefs.followUps = it; changed() }
    }

    private fun voice() {
        f.section(Lang.pick("VOICE", "VOIX"))
        val kinds = listOf("off", "system", "elevenlabs", "fish", "openai")
        f.choice(Lang.pick("Her voice", "Sa voix"), listOf(Lang.pick("Off", "Coupée"), Lang.pick("Phone", "Téléphone"), "ElevenLabs", "Fish Audio", Lang.pick("Speech server", "Serveur vocal")), kinds.indexOf(prefs.voice).coerceAtLeast(0),
            Lang.pick("Phone: works offline once the voice is downloaded. Cloud voices only get the words she says.", "Téléphone : marche hors ligne une fois la voix téléchargée. Les voix cloud ne reçoivent que les mots qu'elle dit.")) { prefs.voice = kinds[it]; changed(); build() }
        when (prefs.voice) {
            "system" -> f.value(Lang.pick("Phone voice", "Voix du téléphone"), prefs.systemVoice.ifEmpty { Lang.pick("default", "par défaut") }) { pick(Lang.pick("Voice", "Voix"), capri.mouth.voices()) { prefs.systemVoice = it; changed(); build() } }
            "elevenlabs" -> {
                f.field("ElevenLabs (" + masked(prefs.elevenKey) + ")", Lang.pick("key", "clé"), "", secret = true) { if (it.isNotEmpty()) { prefs.elevenKey = it; build() } }
                f.field("Voice ID", "ID", prefs.elevenVoice) { prefs.elevenVoice = it; build() }
            }
            "fish" -> {
                f.field("Fish Audio (" + masked(prefs.fishKey) + ")", Lang.pick("key", "clé"), "", secret = true) { if (it.isNotEmpty()) { prefs.fishKey = it; build() } }
                f.field(Lang.pick("Voice ID or link", "ID ou lien de voix"), "", prefs.fishVoice) { prefs.fishVoice = it; build() }
                f.note(Lang.pick("Never clone or imitate a real person's voice without their clear consent.", "Ne clone ni n'imite jamais la voix d'une vraie personne sans son accord clair."))
            }
            "openai" -> {
                f.field(Lang.pick("Server", "Serveur"), "https://api.openai.com/v1 · http://192.168.1.20:8880/v1", prefs.speechUrl) { prefs.speechUrl = it; build() }
                f.field(Lang.pick("Key", "Clé") + " (" + masked(prefs.speechKey) + ")", Lang.pick("optional on your PC", "facultative sur ton PC"), "", secret = true) { if (it.isNotEmpty()) { prefs.speechKey = it; build() } }
                f.field(Lang.pick("Model and voice", "Modèle et voix"), "gpt-4o-mini-tts marin", prefs.speechModel + " " + prefs.speechVoice) { v -> val parts = v.split(' ', limit = 2); prefs.speechModel = parts[0]; prefs.speechVoice = parts.getOrElse(1) { "" }; build() }
            }
        }
        if (prefs.voice != "off") {
            f.slider(Lang.pick("Pitch", "Hauteur"), 0, 10, prefs.pitch, { "$it" }) { prefs.pitch = it; changed() }
            f.slider(Lang.pick("Speed", "Vitesse"), 50, 200, prefs.speed, { "$it %" }) { prefs.speed = it; changed() }
            f.slider("Volume", 0, 100, prefs.volume, { "$it %" }) { prefs.volume = it; changed() }
            f.toggle(Lang.pick("Quiet (typed chat stays silent)", "Silencieux (le chat écrit reste muet)"), prefs.quiet) { prefs.quiet = it; changed() }
            f.toggle(Lang.pick("Interrupt her by talking", "L'interrompre en parlant"), prefs.bargeIn, Lang.pick("Needs the phone's echo canceller; works best with headphones.", "Utilise l'annulation d'écho du téléphone ; marche mieux avec un casque.")) { prefs.bargeIn = it }
            f.buttons(Lang.pick("Test her voice", "Tester sa voix") to { capri.speak(Lang.pick("Hey choom, this is my voice.", "Salut choom, c'est ma voix."), voice = false) })
        }
    }

    private fun ears() {
        f.section(Lang.pick("EARS", "OREILLES"))
        val kinds = listOf("system", "device", "whisper")
        f.choice(Lang.pick("Hearing", "Écoute"), listOf(Lang.pick("Phone", "Téléphone"), Lang.pick("On-device only", "Sur l'appareil"), "Whisper"), kinds.indexOf(prefs.ears).coerceAtLeast(0),
            Lang.pick("On-device keeps your voice on the phone when its language pack is downloaded. Whisper sends the few seconds you say to your own server.", "« Sur l'appareil » garde ta voix sur le téléphone si le pack de langue est téléchargé. Whisper envoie les quelques secondes que tu dis à ton serveur.")) { prefs.ears = kinds[it]; build() }
        if (prefs.ears == "whisper") {
            f.field(Lang.pick("Transcription server", "Serveur de transcription"), "http://192.168.1.20:8000/v1", prefs.whisperUrl) { prefs.whisperUrl = it; build() }
            f.field(Lang.pick("Key", "Clé") + " (" + masked(prefs.whisperKey) + ")", Lang.pick("optional on your PC", "facultative sur ton PC"), "", secret = true) { if (it.isNotEmpty()) { prefs.whisperKey = it; build() } }
        }
        f.toggle(Lang.pick("Keep listening after she answers", "Continuer d'écouter après sa réponse"), prefs.keepListening, Lang.pick("A voice chat ends after two silences.", "Un chat vocal s'arrête après deux silences.")) { prefs.keepListening = it }
    }

    private fun hey() {
        f.section("HEY CAPRI", capri.wake.why() ?: Lang.pick("listening", "à l'écoute"))
        f.note(Lang.pick("Your own voice, compared on the phone only: nothing is recorded or sent. Android shows its microphone dot while it listens.",
            "Ta propre voix, comparée sur le téléphone uniquement : rien n'est enregistré ni envoyé. Android affiche son point micro pendant l'écoute."))
        f.buttons((if (capri.wake.trained) Lang.pick("Record again", "Réenregistrer") else Lang.pick("Record my Hey Capri", "Enregistrer mon Hey Capri")) to { startActivity(Intent(this, WakeTrainActivity::class.java)) })
        if (capri.wake.trained) {
            f.toggle("Hey Capri", prefs.heyCapri, Lang.pick("Only while her bubble runs (started from the app).", "Seulement quand sa bulle tourne (lancée depuis l'app).")) { prefs.heyCapri = it; changed(); build() }
            f.slider(Lang.pick("Sensitivity", "Sensibilité"), 0, 100, prefs.heySensitivity, { "$it" }) { prefs.heySensitivity = it; changed() }
            f.toggle(Lang.pick("Also with the screen off", "Aussi écran éteint"), prefs.heyScreenOff, Lang.pick("Uses more battery.", "Consomme plus de batterie.")) { prefs.heyScreenOff = it; changed() }
            f.toggle(Lang.pick("She answers \"Yes?\"", "Elle répond « Oui ? »"), prefs.wakeSays, Lang.pick("Off: a short beep, then she listens.", "Désactivé : un petit bip, puis elle écoute.")) { prefs.wakeSays = it }
            f.buttons(Lang.pick("Forget my voice", "Oublier ma voix") to { capri.wake.forget(); prefs.heyCapri = false; build() })
        }
    }

    private fun vision() {
        f.section("VISION")
        f.note(Lang.pick("She looks only when you ask. The words on screen and in photos are read on the phone (OCR, offline); a picture goes to your brain only if it can see, and a line in the chat says so.",
            "Elle ne regarde que si tu demandes. Les mots de l'écran et des photos sont lus sur le téléphone (OCR, hors ligne) ; une image ne part vers ton cerveau que s'il voit, et une ligne du chat le dit."))
        val q = listOf("low", "normal", "high")
        f.choice(Lang.pick("Picture size sent", "Taille des images envoyées"), listOf(Lang.pick("Light (960 px)", "Légère (960 px)"), "Normale (1280 px)", Lang.pick("Sharp (1600 px)", "Nette (1600 px)")), q.indexOf(prefs.visionQuality).coerceAtLeast(0),
            Lang.pick("Lighter is faster on mobile data; sharper reads small text better.", "Plus léger va plus vite en données mobiles ; plus net lit mieux les petits textes.")) { prefs.visionQuality = q[it] }
        f.toggle(Lang.pick("Photos with the camera", "Photos avec l'appareil photo"), Security.on(Security.Open), Lang.pick("Off: she asks before opening the camera app.", "Désactivé : elle demande avant d'ouvrir l'app Photo.")) { Security.set(Security.Open, it); prefs.security = Security.saved() }
    }

    private fun modes() {
        f.section("MODES")
        val names = listOf("normal", "study", "dev", "gaming")
        f.choice(Lang.pick("Mode", "Mode"), listOf("NORMAL", "CAPRI STUDY", "CAPRI DEV", "CAPRI GAMING"), names.indexOf(prefs.mode).coerceAtLeast(0),
            Lang.pick("Study explains lessons; Dev talks code; Gaming keeps replies to one line and shows the small HUD. Never cheats.", "Étude explique les cours ; Dev parle code ; Jeu garde des réponses d'une ligne et affiche le petit HUD. Jamais de triche.")) {
            prefs.mode = names[it]; changed(); capri.bubble("mode", names[it]); build()
        }
        if (prefs.mode == "study") {
            val lv = listOf("beginner", "normal", "advanced")
            f.choice(Lang.pick("Level", "Niveau"), listOf(Lang.pick("Beginner", "Débutant"), "Normal", Lang.pick("Advanced", "Avancé")), lv.indexOf(prefs.studyLevel).coerceAtLeast(0)) { prefs.studyLevel = lv[it]; changed() }
        }
        f.toggle(Lang.pick("Gaming HUD", "HUD de jeu"), prefs.gamingHud, Lang.pick("Battery, heat, memory and time over games. FPS isn't readable by apps on Android.", "Batterie, chaleur, mémoire et heure par-dessus les jeux. Les FPS ne sont pas lisibles par les apps sur Android.")) { prefs.gamingHud = it; capri.bubble("mode", prefs.mode) }
    }

    private fun power() {
        f.section(Lang.pick("POWER", "ÉNERGIE"))
        val names = listOf("performance", "normal", "saver")
        f.choice(Lang.pick("Profile", "Profil"), listOf("PERFORMANCE", "NORMAL", Lang.pick("SAVER", "ÉCONOMIE")), names.indexOf(prefs.power).coerceAtLeast(0),
            Lang.pick("Saver turns off Hey Capri and her idle animation and refreshes the HUD less often.", "Économie coupe Hey Capri et son animation au repos, et rafraîchit le HUD moins souvent.")) { prefs.power = names[it]; changed(); build() }
        f.toggle(Lang.pick("Idle animation", "Animation au repos"), prefs.animations == 0) { prefs.animations = if (it) 0 else 1; changed() }
        f.toggle(Lang.pick("Battery and storage warnings", "Alertes batterie et stockage"), prefs.deviceAlerts, Lang.pick("Once when the battery drops to 15% unplugged, and when storage is nearly full (checked at most once a day).", "Une fois quand la batterie tombe à 15 % sans chargeur, et quand le stockage est presque plein (vérifié au plus une fois par jour).")) { prefs.deviceAlerts = it }
    }

    private fun bubble() {
        f.section(Lang.pick("BUBBLE", "BULLE"))
        f.choice(Lang.pick("Size", "Taille"), listOf(Lang.pick("Small", "Petite"), Lang.pick("Medium", "Moyenne"), Lang.pick("Large", "Grande")), prefs.bubbleSize) { prefs.bubbleSize = it; changed() }
        f.slider(Lang.pick("Opacity", "Opacité"), 20, 100, prefs.bubbleOpacity, { "$it %" }) { prefs.bubbleOpacity = it; changed() }
        f.toggle(Lang.pick("Dim over full-screen apps", "Atténuer sur les apps plein écran"), prefs.dimFullscreen) { prefs.dimFullscreen = it; changed() }
        f.toggle(Lang.pick("Start after the phone restarts", "Démarrer après un redémarrage"), prefs.startOnBoot, Lang.pick("After a restart, open Capri once for the microphone (Android's rule).", "Après un redémarrage, ouvre Capri une fois pour le micro (règle d'Android).")) { prefs.startOnBoot = it }
        f.choice(Lang.pick("Look", "Thème"), listOf(Lang.pick("Phone", "Système"), Lang.pick("Dark", "Sombre"), Lang.pick("Light", "Clair")), prefs.theme) { prefs.theme = it; recreate() }
    }

    private fun notifications() {
        f.section("NOTIFICATIONS")
        f.toggle(Lang.pick("Keep today's notifications for her", "Garder les notifications du jour pour elle"), prefs.notifKeep, Lang.pick("In memory on the phone only, never sent anywhere unless you ask her about them.", "En mémoire sur le téléphone uniquement, jamais envoyées sauf si tu lui en parles.")) { prefs.notifKeep = it; if (!it) capri.notices.clear() }
        f.toggle(Lang.pick("Show one-time codes to her brain", "Montrer les codes à son cerveau"), prefs.notifCodes, Lang.pick("Off: codes are hidden unless you ask for \"the code\".", "Désactivé : les codes restent masqués sauf si tu demandes « le code ».")) { prefs.notifCodes = it }
    }

    private fun emergency() {
        f.section(Lang.pick("EMERGENCY", "URGENCE"), if (prefs.emergencyOn) "ON" else "OFF")
        f.note(Lang.pick("Off until you set it up. She always asks before calling or sharing your location.", "Désactivé tant que tu ne le configures pas. Elle demande toujours avant d'appeler ou d'envoyer ta position."))
        f.toggle(Lang.pick("Emergency button", "Bouton d'urgence"), prefs.emergencyOn) { prefs.emergencyOn = it; build() }
        if (prefs.emergencyOn) {
            f.field(Lang.pick("Contact name", "Nom du contact"), "Maman", prefs.emergencyName) { prefs.emergencyName = it; build() }
            f.field(Lang.pick("Number", "Numéro"), "+226…", prefs.emergencyNumber) { prefs.emergencyNumber = it; build() }
            f.field(Lang.pick("Message", "Message"), "", prefs.emergencyText) { prefs.emergencyText = it; build() }
            f.field(Lang.pick("Important info she can show", "Infos importantes qu'elle peut afficher"), Lang.pick("e.g. allergies, a doctor's number", "ex. allergies, numéro d'un médecin"), prefs.emergencyInfo) { prefs.emergencyInfo = it; build() }
            f.buttons(Lang.pick("Allow direct call", "Autoriser l'appel direct") to { requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 5) },
                Lang.pick("Allow location", "Autoriser la position") to { requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 6) })
        }
    }

    private fun sync() {
        val link = capri.sync
        f.section("CAPRI LINK", if (link.paired()) prefs.linkPcName else Lang.pick("not paired", "non appairé"))
        f.note(Lang.pick("The same Capri, with the same memory, on this phone and in Capri Desktop on your PC: notes, memories, projects, personality and chat logs. On the PC, open her menu (her icon by the clock) > Capri Link… > Add a phone, then type the code here. Your own Wi-Fi or Tailscale only, everything encrypted.",
            "La même Capri, avec la même mémoire, sur ce téléphone et dans Capri Desktop sur ton PC : notes, souvenirs, projets, personnalité et journaux. Sur le PC, ouvre son menu (son icône près de l'horloge) > Capri Link… > Ajouter un téléphone, puis tape le code ici. Ton Wi-Fi ou Tailscale uniquement, tout est chiffré."))
        if (link.paired()) {
            f.note(link.state())
            f.toggle(Lang.pick("Sync by herself", "Synchroniser toute seule"), prefs.linkAuto,
                Lang.pick("After each change, before answering when it's been a while, and about every 15 minutes.", "Après chaque changement, avant de répondre si ça fait un moment, et environ toutes les 15 minutes.")) { prefs.linkAuto = it; link.configure() }
            f.buttons(Lang.pick("Sync now", "Synchroniser") to { bg { link.run("sync_memory") } }, Lang.pick("Test", "Tester") to { bg { link.run("status") } },
                Lang.pick("Forget this PC", "Oublier ce PC") to { bg { link.unpair() } })
        } else {
            val address = f.field(Lang.pick("PC address (leave empty: she looks for it)", "Adresse du PC (vide : elle la cherche)"), "192.168.1.20", prefs.linkAddress.removePrefix("http://").substringBefore(":47917"), action = Lang.pick("Find", "Chercher")) { typed ->
                if (typed.isNotBlank()) { prefs.linkAddress = typed.trim(); build() } else bg {
                    val found = link.discover().firstOrNull()
                    if (found == null) Lang.pick("No PC with Capri Link found on this Wi-Fi. Open Capri Link on the PC, or type the address it shows.", "Aucun PC avec Capri Link trouvé sur ce Wi-Fi. Ouvre Capri Link sur le PC, ou tape l'adresse qu'il affiche.")
                    else { prefs.linkAddress = found.address; Lang.pick("Found ${found.name} at ${found.address}.", "Trouvé : ${found.name} à ${found.address}.") }
                }
            }
            f.field(Lang.pick("Pairing code", "Code d'appairage"), "ABCD-EFGH", "", action = Lang.pick("Pair", "Associer")) { code -> val a = address.text.toString().trim(); bg { link.pair(a, code) } }
        }
    }

    private fun you() {
        f.section(Lang.pick("YOU", "TOI"))
        val langs = listOf("French", "English", "Spanish", "German", "Portuguese", "Polish", "Italian")
        f.choice(Lang.pick("Language", "Langue"), listOf("Français", "English", "Español", "Deutsch", "Português", "Polski", "Italiano"), langs.indexOf(prefs.language).coerceAtLeast(0)) { prefs.language = langs[it]; changed(); recreate() }
        f.field(Lang.pick("What should she call you?", "Comment doit-elle t'appeler ?"), "choom", prefs.nickname) { prefs.nickname = Persona.nick(it); changed(); build() }
        f.buttons(Lang.pick("Get to know me", "Faire connaissance") to { val first = capri.brain.meet(); capri.line(com.capri.assistant.engine.Line.CAPRI, first); capri.speak(first, false); startActivity(Intent(this, ChatActivity::class.java)) },
            Lang.pick("Edit her personality", "Modifier sa personnalité") to {
                val file = File(capri.folder, "personality.md")
                startActivity(Intent(this, TextEditActivity::class.java).putExtra("file", file.path).putExtra("title", "personality.md").putExtra("template", Persona.DefaultPersonality)
                    .putExtra("sub", Lang.pick("Her safety lines always stay, whatever you write here.", "Ses règles de sécurité restent toujours, quoi que tu écrives ici.")))
            })
        f.buttons(Lang.pick("Reset her personality", "Réinitialiser sa personnalité") to { Persona.resetPersonality(); toast(Lang.pick("Back to her own personality.", "Retour à sa personnalité d'origine.")) })
    }

    private fun security() {
        f.section(Lang.pick("SECURITY", "SÉCURITÉ"), Security.Presets[Security.preset])
        f.choice(Lang.pick("Preset", "Préréglage"), Security.Presets.take(4), Security.preset.coerceIn(0, 3), Security.PresetLines[Security.preset]) { Security.pick(it); prefs.security = Security.saved(); build() }
        for (i in 0 until Security.Count) f.toggle(Security.Names[i], Security.on(i)) { v -> Security.set(i, v); prefs.security = Security.saved() }
        f.note(Lang.pick("Off means she asks you first, never a silent no. Sending, paying, deleting and wiping her memory always ask.", "Désactivé veut dire qu'elle te demande d'abord, jamais un refus muet. Envoyer, payer, supprimer et effacer sa mémoire demandent toujours."))
    }
}
