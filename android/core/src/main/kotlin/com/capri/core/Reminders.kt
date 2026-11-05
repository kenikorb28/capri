package com.capri.core

import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Reminder(val text: String, val due: Long, var said: Boolean = false, val id: Int = (text + due).hashCode() and 0x7fffffff)

class Routine(val phrase: String, val steps: String)

// Reminders and routines, ported from app/Reminders.cs: the same time words in English, French, Spanish, German, Polish and
// Portuguese, kept in reminders.json and routines.json. The phone layer rings them with an alarm and a notification.
class Reminders(folder: File, private val now: () -> Long = { System.currentTimeMillis() }) {
    private val remindersFile = File(folder, "reminders.json")
    private val routinesFile = File(folder, "routines.json")
    private val reminders = ArrayList<Reminder>()
    private val routines = ArrayList<Routine>()
    private val running = HashSet<String>()
    // Set by the phone layer: puts a due reminder on the alarm clock, or takes one off.
    @Volatile var schedule: ((Reminder) -> Unit)? = null
    @Volatile var unschedule: ((Reminder) -> Unit)? = null

    init {
        folder.mkdirs()
        try {
            if (remindersFile.exists()) for (o in Json.parse(remindersFile.readText()).arr() ?: emptyList()) {
                val m = o.obj() ?: continue
                val text = m["text"].str() ?: continue
                val due = m["due"].num()?.toLong() ?: continue
                reminders.add(Reminder(text, due, m["said"].bool() ?: false))
            }
        } catch (_: Exception) { }
        try {
            if (routinesFile.exists()) for (o in Json.parse(routinesFile.readText()).arr() ?: emptyList()) {
                val m = o.obj() ?: continue
                routines.add(Routine(m["phrase"].str() ?: continue, m["steps"].str() ?: continue))
            }
        } catch (_: Exception) { }
    }

    private fun save() {
        val tmp = File(remindersFile.path + ".new")
        tmp.writeText(Json.write(reminders.map { mapOf("text" to it.text, "due" to it.due, "said" to it.said) }))
        tmp.renameTo(remindersFile)
        val rtmp = File(routinesFile.path + ".new")
        rtmp.writeText(Json.write(routines.map { mapOf("phrase" to it.phrase, "steps" to it.steps) }))
        rtmp.renameTo(routinesFile)
    }

    @Synchronized fun waiting(): List<Reminder> = reminders.filter { !it.said }.sortedBy { it.due }

    private fun shown(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, if (Lang.french) Locale.FRENCH else Locale.ENGLISH).format(Date(t))

    @Synchronized
    fun remind(args: Args): String {
        if (args.action == "list") {
            val w = waiting()
            return if (w.isEmpty()) Lang.pick("You have no reminders waiting.", "Tu n'as aucun rappel en attente.") else Lang.pick("Your reminders:\n", "Tes rappels :\n") + w.joinToString("\n") { shown(it.due) + " : " + it.text }
        }
        if (args.action == "cancel") {
            val wanted = key(args.text)
            if (wanted.isEmpty() || wanted == "the" || wanted == "my" || wanted == "mon" || wanted == "le") return "Couldn't find that reminder."
            val found = reminders.firstOrNull { !it.said && key(it.text) == wanted } ?: reminders.firstOrNull { !it.said && (" " + key(it.text) + " ").contains(" $wanted ") }
                ?: return Lang.pick("Couldn't find that reminder.", "Je ne trouve pas ce rappel.")
            reminders.remove(found)
            save()
            unschedule?.invoke(found)
            return Lang.pick("Cancelled your reminder: ", "Rappel annulé : ") + found.text
        }
        val text = args.text?.trim()
        if (text.isNullOrEmpty()) return Lang.pick("What should I remind you about?", "Je te rappelle quoi ?")
        val due = parse(args.whenText, now()) ?: return Lang.pick("What time should I remind you? Try in 20 minutes, at 5pm or tomorrow at 9.", "Je te le rappelle quand ? Essaie « dans 20 minutes », « à 17h » ou « demain à 9h ».")
        val r = Reminder(text, due)
        reminders.add(r)
        save()
        schedule?.invoke(r)
        return Lang.pick("I'll remind you " + shown(due) + ": " + text, "Je te le rappelle " + shown(due) + " : " + text)
    }

    // The reminder that just came due, marked said, or null.
    @Synchronized
    fun take(id: Int): Reminder? {
        val r = reminders.firstOrNull { it.id == id && !it.said } ?: reminders.filter { !it.said && it.due <= now() }.minByOrNull { it.due } ?: return null
        r.said = true
        reminders.removeAll { it.said && it.due < now() - 7 * 86400_000L }
        save()
        return r
    }

    @Synchronized
    fun routine(args: Args): String {
        if (args.action == "list") return if (routines.isEmpty()) "You have no saved routines." else "Your routines:\n" + routines.joinToString("\n") { it.phrase + ": " + it.steps }
        val phrase = (args.phrase ?: "").trim().trim('"', '\'', '“', '”')
        if (key(phrase).isEmpty()) return "What phrase should start your routine?"
        val existing = routines.firstOrNull { key(it.phrase) == key(phrase) }
        if (args.action == "forget") {
            if (existing == null) return "Couldn't find that routine."
            routines.remove(existing)
            save()
            return "Forgot your routine: $phrase"
        }
        val steps = args.steps?.trim()
        if (steps.isNullOrEmpty()) return "What should I do when you say $phrase?"
        if (existing != null) routines.remove(existing)
        routines.add(Routine(phrase, steps))
        save()
        return "When you say $phrase, I'll $steps"
    }

    @Synchronized fun steps(phrase: String): String? = routines.firstOrNull { key(it.phrase) == key(phrase) }?.steps

    fun run(phrase: String, go: (String) -> String): String {
        val steps = steps(phrase) ?: return "Couldn't find that routine."
        val k = key(phrase)
        synchronized(running) { if (!running.add(k)) return "Already doing that routine." }
        try { return go(steps) } finally { synchronized(running) { running.remove(k) } }
    }

    private fun timerSize(span: String): String {
        var s = rxi("^(?:an?|one|un|une) ").replace(span.trim(), "1 ")
        s = rxi("\\b(?:minutes|mins?)\\b").replace(s, "minute")
        s = rxi("\\b(?:hours|hrs?|heures?)\\b").replace(s, "hour")
        return rxi("\\b(?:seconds|secs?|secondes?)\\b").replace(s, "second")
    }

    // A plain reminder, timer or routine ask, the desktop's Route.
    fun route(said: String): Call? {
        val text = Intents.asked(said)
        if (rxi("^(?:what (?:reminders|routines) do i have|(?:list|show)(?: me)?(?: my)? (?:reminders|routines)|what are my (?:reminders|routines))$").matches(text))
            return Call(if (rxi("\\broutines\\b").containsMatchIn(text)) "routine" else "remind", Args.of("action" to "list"))
        val folded = fold(text)
        if (Regex("^(?:que recordatorios tengo|quels (?:sont mes rappels|rappels ai-je)|quels rappels j'ai|mes rappels|liste (?:de )?mes rappels|welche erinnerungen habe ich|jakie mam przypomnienia|quais (?:sao (?:os )?meus lembretes|lembretes (?:eu )?tenho))$").matches(folded)) return Call("remind", Args.of("action" to "list"))
        var cancel = rxi("^(?:cancel|remove|delete|forget)(?: the| my)? (.+?) reminder$").matchEntire(text)
        if (cancel == null) cancel = rxi("^(?:cancela (?:el )?recordatorio (?:de la |del |de )?|annule (?:le |mon )?rappel (?:de |pour |d')?|supprime (?:le |mon )?rappel (?:de |pour |d')?|(?:lösche|losche) (?:die )?erinnerung (?:an )?|anuluj przypomnienie (?:o )?|(?:cancele|cancela) (?:o )?lembrete (?:de |da |do )?)(.+)$").matchEntire(text)
        if (cancel != null && !Regex("^(?:the|my|le|mon)$").matches(key(cancel.groupValues[1]))) return Call("remind", Args.of("action" to "cancel", "text" to cancel.groupValues[1]))
        val unset = rxi("^(?:cancel|stop|remove|delete|forget|end|kill|annule|arrête|stoppe|supprime)(?: the| my| le| mon)?((?: \\S+)? (?:minutes?|mins?|hours?|hrs?|seconds?|secs?|heures?|secondes?))? (?:timer|minuteur|minuterie)$").matchEntire(text)
        if (unset != null) return Call("remind", Args.of("action" to "cancel", "text" to (timerSize(unset.groupValues[1]) + " timer").trim()))
        val forget = rxi("^(?:forget|remove|delete|oublie|supprime)(?: the| my| la| ma)? (.+?) routine$").matchEntire(text)
        if (forget != null && steps(forget.groupValues[1]) != null) return Call("routine", Args.of("action" to "forget", "phrase" to forget.groupValues[1]))
        val actions = "(?:open|launch|start|run|play|turn|mute|unmute|pause|resume|close|switch|search|google|look|tell|send|write|type|press|click|take|show|set|remind|go|dance|shut|lock|join|leave|make|create|move|ouvre|lance|mets|joue|coupe|cherche|envoie|écris|dis|montre|règle|allume|éteins|baisse|monte|va)\\b"
        var routine = rxi("^(?:when i say|quand je dis)\\s+[\"“«]\\s*(.+?)\\s*[\"”»][\\s,:]+($actions.*)$").matchEntire(text)
        if (routine == null) routine = rxi("^(?:when i say|quand je dis)\\s+(.+?),\\s*($actions.*)$").matchEntire(text)
        if (routine != null && key(routine.groupValues[1]).isNotEmpty()) return Call("routine", Args.of("phrase" to routine.groupValues[1], "steps" to routine.groupValues[2]))
        if (synchronized(running) { key(said) !in running } && steps(said) != null) return Call("routine", Args.of("action" to "run", "phrase" to said))
        val t = now()
        val starting = "^(?:(?:set|start|make|put on|put|give me|mets|lance|démarre|programme)\\s+(?:me\\s+|up\\s+|moi\\s+)?(?:a\\s+|an\\s+|un\\s+|une\\s+)?)?"
        val timer = rxi(starting + "(?:timer|minuteur|minuterie)\\s+(?:for\\s+|de\\s+|pour\\s+|d')?(.+?)$").matchEntire(text)
        val sized = rxi(starting + "(\\S+(?:\\s+and\\s+a\\s+half|\\s+et\\s+demie)?)[\\s-](minutes?|mins?|hours?|hrs?|seconds?|secs?|heures?|secondes?)\\s+(?:timer|minuteur)$").matchEntire(text)
        val span = timer?.groupValues?.get(1)?.trim() ?: sized?.let { it.groupValues[1] + " " + it.groupValues[2] }
        if (span != null && parse("in $span", t) != null) return Call("alarm", Args.of("action" to "set_timer", "seconds" to (((parse("in $span", t) ?: t) - t) / 1000).toString(), "text" to Lang.pick("Your ${timerSize(span)} timer", "Minuteur de $span")))
        val starters = listOf("^remind me\\s+(.+)$", "^recu[eé]rdame\\s+(.+)$", "^rappelle[- ]moi\\s+(.+)$", "^fais[- ]moi penser\\s+(.+)$", "^erinnere mich\\s+(.+)$", "^przypomnij mi\\s+(.+)$", "^(?:lembra[- ]me|lembre[- ]me|me lembre|me lembra)\\s+(.+)$")
        for (starter in starters) {
            val ask = rxi(starter).matchEntire(text) ?: continue
            val rest = ask.groupValues[1]
            // The time can come first ("in 10 minutes to call mum") or last ("to call mum in 10 minutes").
            val gaps = Regex("\\s+").findAll(rest).toList().reversed()
            for (gap in gaps) {
                val whenText = rest.substring(0, gap.range.first).trimEnd(',')
                if (parse(whenText, t) == null) continue
                var note = rest.substring(gap.range.last + 1).trimStart(' ', ',')
                note = rxi("^(?:to\\s+|que\\s+|qu'|de\\s+|d['’]|daran[, ]+(?:dass\\s+)?|dass\\s+|(?:żeby|zeby|aby|by)\\s+|para\\s+)").replace(note, "").trim()
                if (note.isEmpty() || isTimeWord(note)) continue
                return Call("remind", Args.of("text" to note, "when" to whenText))
            }
            for (gap in gaps.reversed()) {
                val whenText = rest.substring(gap.range.last + 1).trim()
                if (parse(whenText, t) == null) continue
                var note = rest.substring(0, gap.range.first).trim(' ', ',')
                note = rxi("^(?:to\\s+|que\\s+|qu'|de\\s+|d['’]|para\\s+)").replace(note, "").trim()
                if (note.isEmpty() || isTimeWord(note)) continue
                return Call("remind", Args.of("text" to note, "when" to whenText))
            }
            return null
        }
        val later = rxi("^(.+?)\\s+(?:tell me to|remind me to|dime que|rappelle[- ]moi de|rappelle[- ]moi d'|dis[- ]moi de|sag mir[, ]+(?:dass\\s+)?|powiedz mi[, ]+(?:(?:żeby|zeby|aby)\\s+)?|(?:me diga|diga[- ]me|lembre[- ]me)\\s+(?:para|de))\\s*(.+)$").matchEntire(text)
        if (later != null && parse(later.groupValues[1], t) != null && later.groupValues[2].isNotBlank()) return Call("remind", Args.of("text" to later.groupValues[2].trim(), "when" to later.groupValues[1]))
        return null
    }

    private fun isTimeWord(note: String) = Regex("^\\p{N}|^(?:at|a las|a la|a|à|um|o|as|to|de|para|daran|zeby|aby|am|pm|in|dans|on|and|et|half|demie|after|before|today|aujourd'hui|tomorrow|tonight|next|this|morning|afternoon|evening|minutes?|hours?|heures?|seconds?|secondes?|days?|jours?|weeks?|manana|demain|morgen|jutro|amanha|esta noche|ce soir|heute|dzis|dzisiaj|hoje|esta noite|morgens|abends|rano|wieczorem|du soir|du matin)(?:\\s|$)").containsMatchIn(fold(note))

    companion object {
        fun key(text: String?): String {
            var plain = Regex("[^\\p{L}\\p{N}\\s]").replace(fold(text).replace("'", ""), " ")
            plain = plain.squash()
            return Regex("^(?:hey\\s+)?capri(?:\\s+|$)").replace(plain, "")
        }

        private val numberWords = listOf(
            "zero|cero|null|zéro", "a|an|one|un|una|une|ein|eine|einer|einen|eins|um|uma|jeden|jedna|jednej",
            "two|dos|deux|zwei|dwa|dwie|dois|duas", "three|tres|trois|drei|trzy", "four|cuatro|quatre|vier|cztery|quatro",
            "five|cinco|cinq|funf|piec|piatej", "six|seis|sechs|szesc|szostej", "seven|siete|sept|sieben|siedem|sete|siodmej",
            "eight|ocho|huit|acht|osiem|oito|osmej", "nine|nueve|neuf|neun|dziewiec|nove|dziewiatej",
            "ten|diez|dix|zehn|dziesiec|dez|dziesiatej", "eleven|once|onze|elf|jedenascie", "twelve|doce|douze|zwolf|dwanascie|doze",
            "thirteen|trece|treize|dreizehn|trzynascie|treze", "fourteen|catorce|quatorze|vierzehn|czternascie|catorze",
            "fifteen|quince|quinze|funfzehn|pietnascie", "sixteen|dieciseis|seize|sechzehn|szesnascie|dezesseis|dezasseis",
            "seventeen|diecisiete|dix-sept|siebzehn|siedemnascie|dezessete|dezassete", "eighteen|dieciocho|dix-huit|achtzehn|osiemnascie|dezoito",
            "nineteen|diecinueve|dix-neuf|neunzehn|dziewietnascie|dezenove", "twenty|veinte|vingt|zwanzig|dwadziescia|vinte",
            "twenty one|veintiuno|vingt et un|einundzwanzig|dwadziescia jeden|vinte e um",
            "twenty two|veintidos|vingt-deux|zweiundzwanzig|dwadziescia dwa|vinte e dois",
            "twenty three|veintitres|vingt-trois|dreiundzwanzig|dwadziescia trzy|vinte e tres")

        private fun number(text: String): Double {
            text.replace(',', '.').toDoubleOrNull()?.let { return it }
            for ((i, words) in numberWords.withIndex()) if (text in words.split('|')) return i.toDouble()
            if (Regex("^(?:half(?: an?| of an?)?|media|une demi|demi|une demie|einer halben|einer halbe|meia|pol)$").matches(text)) return 0.5
            if (text in setOf("thirty", "treinta", "trente", "dreissig", "dreißig", "trzydziesci", "trinta")) return 30.0
            if (text in setOf("quarante", "forty")) return 40.0
            if (text in setOf("quarante-cinq", "forty five", "forty-five")) return 45.0
            return -1.0
        }

        // When a time said in their words falls, by their phone's clock, or null.
        fun parse(said: String?, now: Long): Long? {
            var plain = fold(said).trimEnd('.', ',', '!', '?')
            plain = Regex("^(.*?)\\s+(heures?|hours?|minutes?)\\s+(et demie|and a half)$").replace(plain, "$1 $3 $2")
            val relative = Regex("^(?:in|en|dans|za|em|dentro de|daqui a)\\s+(.*?)\\s*(minutes?|mins?|minutos?|minuten|minut(?:e|y|a)?|hours?|hrs?|horas?|heures?|h|stunden?|godzin(?:e|y|a)?|seconds?|secs?|segundos?|secondes?|sekunden?|sekund(?:e|y|a)?)$").find(plain)
            if (relative != null) {
                var amount = relative.groupValues[1].trim().trimEnd('-')
                var extraHalf = 0.0
                Regex("^(.*?)\\s+(?:et demie|and a half)$").find(amount)?.let { amount = it.groupValues[1]; extraHalf = 0.5 }
                val count = (if (amount.isEmpty()) 1.0 else number(amount)).let { if (it < 0) it else it + extraHalf }
                if (count <= 0) return null
                val unit = relative.groupValues[2]
                val minutes = if (Regex("^(?:h|stund|godzin)").containsMatchIn(unit)) count * 60 else if (Regex("^(?:sec|seg|sek)").containsMatchIn(unit)) count / 60 else count
                val due = now + (minutes * 60000).toLong()
                return if (due > now) due else null
            }
            val tomorrow = Regex("^(?:tomorrow|manana|demain|morgen|jutro|amanha)(?:\\s|$)").containsMatchIn(plain)
            if (tomorrow) plain = Regex("^\\S+\\s*").replaceFirst(plain, "")
            val tonightWords = "^(?:tonight|esta noche|ce soir|heute abend|dzis(?:iaj)? wieczorem|hoje a noite|esta noite)"
            val tonight = Regex("$tonightWords(?:\\s|$)").containsMatchIn(plain)
            if (tonight) plain = Regex("$tonightWords\\s*").replaceFirst(plain, "")
            val morning = Regex("^(?:matin|demain matin|ce matin|morning)$").matches(plain)
            val atWords = "^(?:at|a las|a la|a|um|o|as|pelas|vers)\\s+"
            val at = Regex(atWords).containsMatchIn(plain)
            if (at) plain = Regex(atWords).replaceFirst(plain, "")
            if (plain.isEmpty() && !tomorrow && !tonight) return null
            var hour: Int
            var minute: Int
            var meridiem = false
            if (plain.isEmpty() || morning) { hour = if (tonight) 20 else 9; minute = 0 }
            else {
                val c = clock(plain) ?: return null
                hour = c[0]; minute = c[1]; meridiem = c[2] == 1
            }
            if (!at && !tomorrow && !tonight && !morning && !Regex("\\d\\s*(?:[:h]|[ap]\\.?m|uhr|heures?)|^(?:half past|midnight|noon|midi|minuit)\\b").containsMatchIn(plain)) return null
            if (tonight && hour in 1..11 && !Regex("\\bam$").containsMatchIn(plain)) hour += 12
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            if (tomorrow) cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.HOUR_OF_DAY, hour)
            cal.add(Calendar.MINUTE, minute)
            var due = cal.timeInMillis
            if (!tomorrow && !tonight && !meridiem && hour in 1..11 && due <= now && due + 12 * 3600_000L > now) due += 12 * 3600_000L
            if (!tomorrow && due <= now) due += 24 * 3600_000L
            return due
        }

        // [hour, minute, 1 if am/pm was said] or null.
        private fun clock(text: String): IntArray? {
            var plain = text.replace("a.m.", "am").replace("p.m.", "pm").replace("a.m", "am").replace("p.m", "pm")
            plain = Regex("\\s+(?:de la tarde|de la noche|du soir|de l'apres-midi|de l apres-midi|nachmittags|abends|po poludniu|wieczorem|da tarde|da noite)$").replace(plain, " pm")
            plain = Regex("\\s+(?:de la manana|du matin|morgens|rano|da manha)$").replace(plain, " am")
            if (Regex("^(?:midnight|medianoche|minuit|mitternacht|polnoc|polnocy|meia-noite)$").matches(plain)) return intArrayOf(0, 0, 0)
            if (Regex("^(?:noon|midday|mediodia|midi|mittag|poludnie|meio-dia)$").matches(plain)) return intArrayOf(12, 0, 0)
            var minute = 0
            val nextHour = Regex("^(?:halb|w pol do)\\s+").containsMatchIn(plain)
            val half = nextHour || plain.startsWith("half past ") || Regex("\\s+(?:y media|et demie|e meia)(?:\\s+[ap]m)?$").containsMatchIn(plain)
            if (half) {
                plain = Regex("^(?:half past|halb|w pol do)\\s+|\\s+(?:y media|et demie|e meia)(?=\\s+[ap]m|$)").replace(plain, "")
                minute = 30
            }
            val quarter = Regex("\\s+et quart$").containsMatchIn(plain)
            if (quarter) { plain = Regex("\\s+et quart$").replace(plain, ""); minute = 15 }
            val m = Regex("^(.+?)(?:\\s*[:h]\\s*(\\d{1,2}))?\\s*(?:h|uhr|heures?)?\\s*([ap]m)?$").find(plain) ?: return null
            val digits = m.groupValues[1].trim()
            if (digits == "a" || digits == "an") return null
            val n = number(digits)
            if (n < 0 || n > 23 || n != Math.floor(n)) return null
            var hour = n.toInt()
            if (m.groups[2] != null) {
                if (half || quarter) return null
                minute = m.groupValues[2].toInt()
            }
            if (minute > 59) return null
            var meridiem = 0
            if (m.groups[3] != null) {
                meridiem = 1
                if (hour < 1 || hour > 12) return null
                hour = hour % 12 + if (m.groupValues[3] == "pm") 12 else 0
            }
            if (nextHour) hour = (hour + 23) % 24
            return intArrayOf(hour, minute, meridiem)
        }
    }
}
