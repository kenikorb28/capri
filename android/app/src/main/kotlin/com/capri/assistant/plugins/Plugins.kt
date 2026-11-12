package com.capri.assistant.plugins

import com.capri.assistant.engine.Capri
import com.capri.core.Args
import com.capri.core.Call
import com.capri.core.Intents
import com.capri.core.Lang
import com.capri.core.ToolSpec
import com.capri.core.Toolbox
import com.capri.core.rxi
import kotlin.random.Random

// Capri's plugins: tools added from outside her core, the way any new one can be. Each plugin gives a ToolSpec (name,
// description, parameters and which are required, its SECURITY toggle or -1, what she says she can do, its group and its
// risk), its hands, and if it likes, plain phrases that reach it with no brain call (so they work offline too).
object Plugins {
    fun install(capri: Capri) {
        // CALCULATE: exact arithmetic on the phone, offline.
        Toolbox.register(ToolSpec("calculate", "Work out arithmetic exactly on the phone, offline: + - * / ^ %, parentheses and decimals, like 12.5*3, (1500*12)/100 or 15% of 2000. Use it instead of doing sums in your head.",
            Toolbox.s(Toolbox.p("expression", "The arithmetic, with numbers and + - * / ^ ( ) %"), "expression"), -1, "do exact calculations", "plugins"))
        capri.hands.plug("calculate") { args -> Calc.say(args["expression"] ?: args.text ?: "") }
        Intents.route(::calcPhrase)

        // RANDOM_PICK: a coin, dice, or one thing from their list.
        Toolbox.register(ToolSpec("random_pick", "Flip a coin, roll dice, or pick one thing at random from a list they give, when they can't decide.",
            Toolbox.s(Toolbox.p("kind", "What to do", listOf("coin", "dice", "list")) + "," + Toolbox.p("text", "For list, the choices separated by commas or 'ou'; for dice, how many sides"), "kind"), -1, "flip a coin, roll dice or pick at random", "plugins"))
        capri.hands.plug("random_pick") { args -> pick(args) }
        Intents.route(::pickPhrase)
        Extras.install(capri)
    }

    // "Calcule 12 fois 3", "combien font 15 % de 2000": straight to the calculator, no brain needed.
    fun calcPhrase(text: String): Call? {
        val m = rxi("^(?:calcule|calculer|combien (?:font|fait|ça fait|ca fait)|c'est combien|ça fait combien|what'?s|calculate|how much is)\\s+(.+?)\\s*\\??$").matchEntire(text) ?: return null
        val what = m.groupValues[1]
        return if (!Regex("\\d").containsMatchIn(what)) null else Call("calculate", Args.of("expression" to what))
    }

    fun pickPhrase(text: String): Call? = when {
        rxi("^(?:pile ou face|lance une pièce|tire à pile ou face|flip a coin|heads or tails)\\s*\\??$").matches(text) -> Call("random_pick", Args.of("kind" to "coin"))
        rxi("^(?:lance un dé|lance les dés|jette un dé|roll a die|roll the dice|roll a dice)(?:\\s+(?:de|à)\\s+(\\d+)(?:\\s+faces)?)?$").matches(text) ->
            Call("random_pick", Args.of("kind" to "dice", "text" to Regex("\\d+").find(text)?.value))
        else -> null
    }

    private fun pick(args: Args): String = when (args.kind) {
        "coin" -> if (Random.nextBoolean()) Lang.pick("Heads.", "Pile.") else Lang.pick("Tails.", "Face.")
        "dice" -> { val sides = (args.text?.filter { it.isDigit() }?.toIntOrNull() ?: 6).coerceIn(2, 1000); Lang.pick("Rolled a d$sides: ", "Dé à $sides faces : ") + (1 + Random.nextInt(sides)) + "." }
        else -> {
            val choices = (args.text ?: "").split(Regex("\\s*(?:,|;|\\bou\\b|\\bor\\b)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
            if (choices.size < 2) Lang.pick("That didn't work: give me at least two choices.", "Ça n'a pas marché : donne-moi au moins deux choix.")
            else Lang.pick("Picked at random: ", "Tiré au sort : ") + choices[Random.nextInt(choices.size)] + "."
        }
    }
}

// Arithmetic without eval: a small parser for + - * / ^ %, parentheses, unary minus, decimals with a dot or a comma,
// and the words people say ("fois", "divisé par", "plus", "moins", "puissance", "15 % de 2000").
object Calc {
    class Bad(message: String) : Exception(message)

    fun words(text: String): String {
        var t = " " + text.lowercase().replace('×', '*').replace('÷', '/').replace('−', '-') + " "
        t = Regex("(\\d)\\s*%\\s*(?:de|of|du|des)\\s").replace(t) { it.groupValues[1] + "/100*" }
        for ((w, op) in listOf(" multiplié par " to "*", " multiplied by " to "*", " fois " to "*", " times " to "*", " divisé par " to "/", " divided by " to "/", " sur " to "/", " over " to "/",
            " plus " to "+", " moins " to "-", " minus " to "-", " puissance " to "^", " au carré " to "^2 ", " squared " to "^2 ", " au cube " to "^3 "))
            t = t.replace(w, " $op ")
        t = Regex("(\\d)\\s*x\\s*(\\d)").replace(t) { it.groupValues[1] + "*" + it.groupValues[2] }
        t = Regex("(\\d),(\\d)").replace(t) { it.groupValues[1] + "." + it.groupValues[2] }
        return t.replace(Regex("[=?]"), "").replace(Regex("\\s+"), "")
    }

    fun eval(text: String): Double {
        val s = words(text)
        if (s.isEmpty() || !Regex("^[0-9+\\-*/^%().]+$").matches(s)) throw Bad(Lang.pick("that isn't arithmetic I can do", "ce n'est pas un calcul que je sais faire"))
        var at = 0
        fun peek(): Char? = if (at < s.length) s[at] else null
        lateinit var sum: () -> Double
        fun number(): Double {
            val start = at
            while (peek()?.let { it.isDigit() || it == '.' } == true) at++
            if (start == at) throw Bad(Lang.pick("a number is missing", "il manque un nombre"))
            return s.substring(start, at).toDoubleOrNull() ?: throw Bad(Lang.pick("a number is malformed", "un nombre est mal écrit"))
        }
        fun atom(): Double = when (peek()) {
            '-' -> { at++; -atom() }
            '+' -> { at++; atom() }
            '(' -> { at++; val v = sum(); if (peek() != ')') throw Bad(Lang.pick("a parenthesis isn't closed", "une parenthèse n'est pas fermée")); at++; v }
            else -> { var v = number(); if (peek() == '%') { at++; v /= 100 }; v }
        }
        fun power(): Double { val b = atom(); if (peek() == '^') { at++; return Math.pow(b, power()) }; return b }
        fun product(): Double {
            var v = power()
            while (true) when (peek()) {
                '*' -> { at++; v *= power() }
                '/' -> { at++; val d = power(); if (d == 0.0) throw Bad(Lang.pick("division by zero", "division par zéro")); v /= d }
                else -> return v
            }
        }
        sum = {
            var v = product()
            while (true) { when (peek()) { '+' -> { at++; v += product() }; '-' -> { at++; v -= product() }; else -> break } }
            v
        }
        val v = sum()
        if (at != s.length) throw Bad(Lang.pick("something in it isn't arithmetic", "une partie n'est pas un calcul"))
        if (v.isNaN() || v.isInfinite()) throw Bad(Lang.pick("the result is too big", "le résultat est trop grand"))
        return v
    }

    fun shown(v: Double): String {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.format(java.util.Locale.ROOT, "%d", v.toLong())
        return String.format(java.util.Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.').let { if (Lang.french) it.replace('.', ',') else it }
    }

    fun say(expression: String): String = try {
        val v = eval(expression)
        Lang.pick("$expression = ${shown(v)}", "$expression = ${shown(v)}")
    } catch (e: Bad) { Lang.pick("That didn't work: ", "Ça n'a pas marché : ") + (e.message ?: "?") + "." }
}
