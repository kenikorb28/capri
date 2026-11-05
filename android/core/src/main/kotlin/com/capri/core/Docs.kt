package com.capri.core

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// The words in a file they hand her, the way the desktop's Files.cs reads them: plain text and code as they are, Word,
// Excel, PowerPoint and OpenDocument files from the XML inside them. PDFs and pictures are read on the phone (pages
// drawn, then OCR or her eyes). File contents are data for the chat, never orders.
object Docs {
    private val textTypes = setOf("txt", "md", "markdown", "csv", "tsv", "json", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf", "log", "html", "htm", "css", "js", "ts", "tsx", "jsx",
        "kt", "kts", "java", "py", "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "dart", "sh", "bat", "ps1", "sql", "gradle", "properties", "srt", "vtt", "tex", "lua", "r", "m", "gitignore", "env")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isText(name: String, mime: String?): Boolean = extension(name) in textTypes || (mime ?: "").startsWith("text/") || mime == "application/json" || mime == "application/xml"

    // The file's words, or null when it isn't a kind she reads here (then the phone tries pages or pictures).
    fun text(name: String, mime: String?, bytes: ByteArray, most: Int = 14000): String? {
        val ext = extension(name)
        val raw = when {
            isText(name, mime) -> decode(bytes)
            ext == "docx" || ext == "docm" -> zipped(bytes, Regex("^word/(document|footnotes|endnotes)\\d*\\.xml$")) { wordText(it) }
            ext == "pptx" -> zipped(bytes, Regex("^ppt/slides/slide\\d+\\.xml$")) { drawingText(it) }
            ext == "xlsx" || ext == "xlsm" -> sheet(bytes)
            ext == "odt" || ext == "odp" || ext == "ods" -> zipped(bytes, Regex("^content\\.xml$")) { odfText(it) }
            else -> null
        } ?: return null
        val clean = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        return if (clean.length > most) clean.substring(0, most) + "\n[... " + (clean.length - most) + " more characters]" else clean
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }

    private fun zipped(bytes: ByteArray, part: Regex, read: (String) -> String): String? {
        val parts = ArrayList<Pair<String, String>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (part.matches(entry.name)) parts.add(entry.name to String(zip.readBytes(), Charsets.UTF_8))
            }
        }
        if (parts.isEmpty()) return null
        parts.sortBy { (n, _) -> Regex("\\d+").find(n)?.value?.toIntOrNull() ?: 0 }
        return parts.joinToString("\n\n") { read(it.second) }
    }

    private fun wordText(xml: String): String {
        var t = Regex("</w:p>").replace(xml, "\n")
        t = Regex("<w:tab/>").replace(t, "\t")
        t = Regex("<w:br[^>]*/>").replace(t, "\n")
        return lines(Html.entities(Regex("<[^>]+>").replace(t, "")))
    }

    private fun drawingText(xml: String): String = lines(Html.entities(Regex("<[^>]+>").replace(Regex("</a:p>").replace(xml, "\n"), "")))

    private fun odfText(xml: String): String = lines(Html.entities(Regex("<[^>]+>").replace(Regex("</text:(p|h)>").replace(xml, "\n"), "")))

    private fun lines(t: String): String = t.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }.joinToString("\n")

    private fun sheet(bytes: ByteArray): String? {
        var shared = emptyList<String>()
        val sheets = ArrayList<Pair<String, String>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml") shared = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(String(zip.readBytes(), Charsets.UTF_8)).map { Html.entities(Regex("<[^>]+>").replace(it.groupValues[1], "")) }.toList()
                else if (Regex("^xl/worksheets/sheet\\d+\\.xml$").matches(e.name)) sheets.add(e.name to String(zip.readBytes(), Charsets.UTF_8))
            }
        }
        if (sheets.isEmpty()) return null
        sheets.sortBy { (n, _) -> Regex("\\d+").find(n)?.value?.toIntOrNull() ?: 0 }
        val out = StringBuilder()
        for ((n, xml) in sheets) {
            out.append("## ").append(n.substringAfterLast('/').removeSuffix(".xml")).append('\n')
            for (row in Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
                val cells = Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).map { c ->
                    val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(c.groupValues[2])?.groupValues?.get(1) ?: Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(c.groupValues[2])?.groupValues?.get(1) ?: ""
                    if (c.groupValues[1].contains("t=\"s\"")) shared.getOrElse(v.toIntOrNull() ?: -1) { v } else Html.entities(v)
                }.toList()
                if (cells.any { it.isNotBlank() }) out.append(cells.joinToString(" | ")).append('\n')
            }
        }
        return out.toString()
    }
}
