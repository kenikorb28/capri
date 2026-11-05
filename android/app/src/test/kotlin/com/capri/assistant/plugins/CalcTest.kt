package com.capri.assistant.plugins

import com.capri.core.Intents
import com.capri.core.Lang
import com.capri.core.Reminders
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The calculator plugin's arithmetic, on the JVM (it uses no Android classes).
class CalcTest {
    @Test fun sumsAreExact() {
        assertEquals(36.0, Calc.eval("12 fois 3"), 0.0)
        assertEquals(37.5, Calc.eval("12,5 x 3"), 1e-12)
        assertEquals(180.0, Calc.eval("(1500*12)/100"), 1e-12)
        assertEquals(300.0, Calc.eval("15 % de 2000"), 1e-12)
        assertEquals(1024.0, Calc.eval("2^10"), 0.0)
        assertEquals(-7.0, Calc.eval("-(3+4)"), 0.0)
        assertEquals(14.0, Calc.eval("2+3*4"), 0.0)
        assertEquals(0.75, Calc.eval("15 sur 20"), 1e-12)
        assertEquals(25.0, Calc.eval("5 au carré"), 0.0)
    }

    // A plugin's phrases reach it with no brain, and win over the general "lance…" (which would open an app).
    @Test fun pluginPhrasesRouteWithoutTouchingTheCore() {
        Intents.route(Plugins::calcPhrase)
        Intents.route(Plugins::pickPhrase)
        val r = Reminders(Files.createTempDirectory("capri").toFile())
        val c = Intents.heard("Capri, combien font 15 % de 2000 ?", r)!!
        assertEquals("calculate", c.name); assertEquals("15 % de 2000", c.args["expression"])
        assertEquals("random_pick", Intents.heard("lance un dé", r)!!.name)
        assertEquals("20", Intents.heard("lance un dé de 20 faces", r)!!.args.text)
        assertEquals("coin", Intents.heard("pile ou face ?", r)!!.args.kind)
        assertEquals("open_app", Intents.heard("lance Free Fire", r)!!.name)
    }

    @Test fun badInputSaysSo() {
        Lang.french = true
        assertTrue(Calc.say("10 / 0").startsWith("Ça n'a pas marché : division par zéro"))
        assertTrue(Calc.say("rm -rf /").startsWith("Ça n'a pas marché"))
        assertTrue(Calc.say("(2+3").startsWith("Ça n'a pas marché"))
        assertEquals("12 fois 3 = 36", Calc.say("12 fois 3"))
        assertEquals("1/3 = 0,333333", Calc.say("1/3"))
    }
}
