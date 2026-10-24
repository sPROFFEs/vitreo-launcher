package dev.vitreo.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import dev.vitreo.launcher.BuildConfig

/** Each redirect is checked before connecting. No cookies, credentials or cleartext fallback. */
object HttpsClient {
    val githubHosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    val newsHosts = setOf("news.google.com")
    fun allowed(url: String, hosts: Set<String>): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.userInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)
    private fun open(url: String, hosts: Set<String>): HttpsURLConnection {
        var next = url
        repeat(6) {
            require(allowed(next, hosts)) { "Untrusted download address." }
            val c = URL(next).openConnection() as HttpsURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 12_000; c.readTimeout = 20_000
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
                    throw IOException("Server returned HTTP $code.")
                }
                return c
            }
        }
        throw IOException("Too many redirects.")
    }
    suspend fun text(url: String, hosts: Set<String>, maxBytes: Int = 2 * 1024 * 1024): String = withContext(Dispatchers.IO) {
        val c = open(url, hosts)
        try { c.inputStream.use { input ->
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer); if (n < 0) break
                if (bytes.size() + n > maxBytes) throw IOException("Response is too large.")
                bytes.write(buffer, 0, n)
            }
            bytes.toString("UTF-8")
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
