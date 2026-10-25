package dev.vitreo.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.SocketTimeoutException
import javax.net.ssl.HttpsURLConnection
import dev.vitreo.launcher.BuildConfig

/** Each redirect is checked before connecting. No cookies, credentials or cleartext fallback. */
object HttpsClient {
    val githubHosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    val newsHosts = setOf("news.google.com", "feeds.bbci.co.uk", "feeds.elpais.com", "www.lemonde.fr", "www.tagesschau.de")
    val newsImageHosts = setOf("ichef.bbci.co.uk", "ichef.bbci.com", "imagenes.elpais.com", "img.lemde.fr", "images.tagesschau.de")
    fun allowed(url: String, hosts: Set<String>): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.userInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)
    private fun remaining(deadline: Long): Int {
        val millis = (deadline - System.nanoTime()) / 1_000_000
        if (millis <= 0) throw SocketTimeoutException("Request timed out.")
        return millis.coerceAtMost(8_000).toInt()
    }
    private fun open(url: String, hosts: Set<String>, deadline: Long = 0): HttpsURLConnection {
        var next = url
        repeat(6) {
            require(allowed(next, hosts)) { "Untrusted download address." }
            val c = URL(next).openConnection() as HttpsURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = if (deadline == 0L) 12_000 else remaining(deadline)
            c.readTimeout = if (deadline == 0L) 20_000 else remaining(deadline)
            c.setRequestProperty("User-Agent", "Vitreo/${BuildConfig.VERSION_NAME}")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (URI(next).host == "api.github.com") {
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = try { c.responseCode } catch(e: Exception) { c.disconnect(); throw e }
            if (code in setOf(301,302,303,307,308)) {
                val location = c.getHeaderField("Location"); c.disconnect()
                if (location == null) throw IOException("Missing redirect.")
                next = URI(next).resolve(location).toString()
            } else {
                if (code != 200) {
                    val rateLimited = code == 429 || code == 403 && c.getHeaderField("X-RateLimit-Remaining") == "0"
                    c.disconnect()
                    if (rateLimited) throw RateLimitedException()
                    throw HttpStatusException(code)
                }
                return c
            }
        }
        throw IOException("Too many redirects.")
    }
    suspend fun text(url: String, hosts: Set<String>, maxBytes: Int = 2 * 1024 * 1024): String =
        bytes(url, hosts, maxBytes).toString(Charsets.UTF_8)

    suspend fun bytes(url: String, hosts: Set<String>, maxBytes: Int, image: Boolean = false): ByteArray = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        // A news request has one total budget across redirects and body reads.
        // APK downloads keep their independent streaming/progress behavior.
        val newsRequest = hosts.isNotEmpty() && (newsHosts + newsImageHosts).containsAll(hosts)
        val deadline = if (newsRequest) System.nanoTime() + 20_000_000_000L else 0L
        val c = open(url, hosts, deadline)
        try { c.inputStream.use { input ->
            if (image && c.contentType?.substringBefore(';')?.lowercase() !in setOf("image/jpeg", "image/png", "image/webp"))
                throw IOException("Unsupported news image.")
            if (c.contentLengthLong > maxBytes) throw IOException("Response is too large.")
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (deadline != 0L) c.readTimeout = remaining(deadline)
                val n = input.read(buffer); if (n < 0) break
                if (bytes.size() + n > maxBytes) throw IOException("Response is too large.")
                bytes.write(buffer, 0, n)
            }
            bytes.toByteArray()
        } } finally { c.disconnect() }
    }
    suspend fun download(url: String, target: File, expected: Long, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        require(expected in 1..64L*1024*1024)
        val c = open(url, githubHosts)
        try {
            val length = c.contentLengthLong
            if (length > 0 && length != expected) throw IOException("Unexpected APK length.")
            c.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64*1024); var total = 0L; var previous = -1
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    total += n; if (total > expected) throw IOException("APK exceeds expected size.")
                    output.write(buffer, 0, n)
                    val percent = (100 * total / expected).toInt()
                    if (percent != previous) { previous = percent; progress(percent) }
                }
                if (total != expected) throw IOException("Incomplete APK download.")
                output.fd.sync()
            } }
        } finally { c.disconnect() }
    }
}

class RateLimitedException : IOException("The server is temporarily limiting requests.")
class HttpStatusException(val code: Int) : IOException("Server returned HTTP $code.")
