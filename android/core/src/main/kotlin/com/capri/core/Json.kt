package com.capri.core

// A small JSON reader and writer with no dependencies, so the same code runs on Android and in the JVM tests.
// Objects come back as LinkedHashMap<String, Any?>, arrays as ArrayList<Any?>, numbers as Long or Double.

class JsonException(message: String) : Exception(message)

// Already-written JSON that Json.write copies as it is, like a tool schema.
class RawJson(val text: String)

object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.space()
        val value = reader.value()
        reader.space()
        if (!reader.done()) throw JsonException("Extra text after JSON at " + reader.at)
        return value
    }

    fun parseOrNull(text: String?): Any? = try { if (text == null) null else parse(text) } catch (e: JsonException) { null }

    fun write(value: Any?): String {
        val out = StringBuilder()
        write(value, out)
        return out.toString()
    }

    fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        quote(text, out)
        return out.toString()
    }

    private fun quote(text: String, out: StringBuilder) {
        out.append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') out.append("\\u").append(String.format("%04x", c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private fun write(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is RawJson -> out.append(value.text)
            is String -> quote(value, out)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double -> if (value.isNaN() || value.isInfinite()) out.append("null") else if (value == Math.floor(value) && Math.abs(value) < 1e15) out.append(value.toLong().toString()) else out.append(value.toString())
            is Float -> write(value.toDouble(), out)
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    quote(k.toString(), out)
                    out.append(':')
                    write(v, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    write(v, out)
                }
                out.append(']')
            }
            is Array<*> -> write(value.asList(), out)
            is IntArray -> write(value.toList(), out)
            is DoubleArray -> write(value.toList(), out)
            else -> quote(value.toString(), out)
        }
    }

    private class Reader(val text: String) {
        var at = 0

        fun done() = at >= text.length

        fun space() {
            while (at < text.length && (text[at] == ' ' || text[at] == '\n' || text[at] == '\r' || text[at] == '\t')) at++
        }

        fun value(): Any? {
            if (at >= text.length) throw JsonException("JSON ended early")
            return when (val c = text[at]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw JsonException("Unexpected '" + c + "' at " + at)
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!text.startsWith(w, at)) throw JsonException("Bad literal at $at")
            at += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            space()
            if (at < text.length && text[at] == '}') { at++; return map }
            while (true) {
                space()
                if (at >= text.length || text[at] != '"') throw JsonException("Expected a key at $at")
                val key = str()
                space()
                if (at >= text.length || text[at] != ':') throw JsonException("Expected ':' at $at")
                at++
                space()
                map[key] = value()
                space()
                if (at >= text.length) throw JsonException("Object ended early")
                if (text[at] == ',') { at++; continue }
                if (text[at] == '}') { at++; return map }
                throw JsonException("Expected ',' or '}' at $at")
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            at++
            space()
            if (at < text.length && text[at] == ']') { at++; return list }
            while (true) {
                space()
                list.add(value())
                space()
                if (at >= text.length) throw JsonException("Array ended early")
                if (text[at] == ',') { at++; continue }
                if (text[at] == ']') { at++; return list }
                throw JsonException("Expected ',' or ']' at $at")
            }
        }

        fun str(): String {
            at++
            val out = StringBuilder()
            while (true) {
                if (at >= text.length) throw JsonException("String ended early")
                val c = text[at++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (at >= text.length) throw JsonException("Escape ended early")
                        when (val e = text[at++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (at + 4 > text.length) throw JsonException("Bad unicode escape")
                                out.append(text.substring(at, at + 4).toInt(16).toChar())
                                at += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = at
            if (text[at] == '-') at++
            while (at < text.length && text[at] in '0'..'9') at++
            var fraction = false
            if (at < text.length && text[at] == '.') { fraction = true; at++; while (at < text.length && text[at] in '0'..'9') at++ }
            if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
                fraction = true
                at++
                if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
                while (at < text.length && text[at] in '0'..'9') at++
            }
            val number = text.substring(start, at)
            if (number == "-" || number.isEmpty()) throw JsonException("Bad number at $start")
            return if (fraction) number.toDouble() else number.toLongOrNull() ?: number.toDouble()
        }
    }
}

// Reading helpers that never throw: a missing or wrongly typed field is null.
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?>? = this as? Map<String, Any?>

fun Any?.arr(): List<Any?>? = this as? List<Any?>

fun Any?.str(): String? = when (this) { is String -> this; is Number, is Boolean -> this.toString(); else -> null }

fun Any?.num(): Double? = when (this) { is Number -> this.toDouble(); is String -> this.trim().toDoubleOrNull(); else -> null }

fun Any?.bool(): Boolean? = when (this) { is Boolean -> this; is String -> if (this.equals("true", true)) true else if (this.equals("false", true)) false else null; else -> null }

fun Map<String, Any?>?.at(vararg path: Any): Any? {
    var here: Any? = this
    for (step in path) {
        here = when (step) {
            is String -> here.obj()?.get(step)
            is Int -> here.arr()?.getOrNull(step)
            else -> null
        }
        if (here == null) return null
    }
    return here
}
