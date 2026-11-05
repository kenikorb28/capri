package com.capri.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections

class HttpReply(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
    val text: String get() = String(body, Charsets.UTF_8)
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}

class HttpCancelled : IOException("Reply cancelled.")
class HttpTimeout(message: String = "The reply took too long.") : IOException(message)
class HttpUnreachable(message: String) : IOException(message)

// Plain HttpURLConnection, which Android and the JVM both have. Requests never follow redirects, so a key only reaches the
// address it was meant for, and every open request can be cut off at once when they interrupt her.
class Http(private val userAgent: String = "Capri-Android") {
    private val open = Collections.synchronizedSet(HashSet<HttpURLConnection>())
    @Volatile private var generation = 0

    fun cancelAll() {
        generation++
        val now = synchronized(open) { open.toList() }
        for (c in now) try { c.disconnect() } catch (_: Exception) { }
    }

    fun ticket(): Int = generation

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        timeoutMs: Int = 45000,
        ticket: Int = -1,
        onLine: ((String) -> Unit)? = null
    ): HttpReply {
        if (ticket >= 0 && ticket != generation) throw HttpCancelled()
        val connection: HttpURLConnection
        try {
            connection = URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw HttpUnreachable("That address doesn't work: " + Redact.text(url))
        }
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.connectTimeout = minOf(timeoutMs, 15000)
        connection.readTimeout = timeoutMs
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", userAgent)
        for ((k, v) in headers) connection.setRequestProperty(k, v)
        open.add(connection)
        try {
            if (body != null) {
                connection.doOutput = true
                if (headers.keys.none { it.equals("Content-Type", true) }) connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val replyHeaders = HashMap<String, String>()
            for ((k, v) in connection.headerFields) if (k != null && v != null && v.isNotEmpty()) replyHeaders[k] = v.joinToString(",")
            val stream: InputStream? = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = if (stream == null) ByteArray(0) else if (onLine != null && status < 400) lines(stream, onLine, ticket) else stream.use { read(it) }
            if (ticket >= 0 && ticket != generation) throw HttpCancelled()
            return HttpReply(status, replyHeaders, bytes)
        } catch (e: HttpCancelled) {
            throw e
        } catch (e: SocketTimeoutException) {
            if (ticket >= 0 && ticket != generation) throw HttpCancelled()
            throw HttpTimeout()
        } catch (e: IOException) {
            if (ticket >= 0 && ticket != generation) throw HttpCancelled()
            throw HttpUnreachable(Redact.text(e.message ?: e.javaClass.simpleName))
        } finally {
            open.remove(connection)
            try { connection.disconnect() } catch (_: Exception) { }
        }
    }

    private fun read(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    // Server-sent events and line-by-line replies: each line goes out as it lands, and the whole body is kept too.
    private fun lines(stream: InputStream, onLine: (String) -> Unit, ticket: Int): ByteArray {
        val all = ByteArrayOutputStream()
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                if (ticket >= 0 && ticket != generation) throw HttpCancelled()
                val line = reader.readLine() ?: break
                all.write(line.toByteArray(Charsets.UTF_8))
                all.write('\n'.code)
                onLine(line)
            }
        }
        return all.toByteArray()
    }

    companion object {
        // A server on their own phone or network, where plain http is fine and nothing leaves their home.
        fun isPrivateHost(host: String): Boolean {
            val h = host.trim('[', ']').lowercase()
            if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".internal")) return true
            if (!Regex("^[0-9.:a-f]+$").matches(h)) return false
            return try {
                val a = InetAddress.getByName(h)
                a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || h.startsWith("100.") && (h.split('.').getOrNull(1)?.toIntOrNull() ?: 0) in 64..127 || h.startsWith("fd") || h.startsWith("fc")
            } catch (e: Exception) { false }
        }

        // A key only goes over https, or plain http to their own network.
        fun safeForKey(url: String): Boolean = try {
            val u = URL(url)
            u.protocol == "https" || (u.protocol == "http" && isPrivateHost(u.host))
        } catch (e: Exception) { false }
    }
}
