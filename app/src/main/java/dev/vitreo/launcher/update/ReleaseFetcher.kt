package dev.vitreo.launcher.update

import dev.vitreo.launcher.net.HttpsClient
import dev.vitreo.launcher.net.RateLimitedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/** The website path reads immutable release assets, never scraped HTML or an unsigned APK. */
class ReleaseFetcher(
    private val text: suspend (String) -> String = { HttpsClient.text(it, HttpsClient.githubHosts, 256*1024) },
    private val length: suspend (String) -> Long = HttpsClient::length,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun fetch(apiRetryAt: Long = 0, rateLimited: (Long) -> Unit = {}): StableRelease {
        if (clock() >= apiRetryAt) {
            try { return StableRelease.parse(text(StableRelease.API)) }
            catch (e: CancellationException) { throw e }
            catch (e: RateLimitedException) { rateLimited(e.retryAt.coerceIn(clock()+60_000, clock()+24*60*60*1000L)) }
            catch (_: IOException) { /* A blocked/unreachable API may still allow github.com. */ }
            // Invalid API metadata fails closed; it is not treated as a network error.
        }
        currentCoroutineContext().ensureActive()
        val metadata = text("${StableRelease.REPO}/releases/latest/download/build-info.json")
        val info = JSONObject(metadata)
        require(info.getString("buildType") == "release" && info.getString("applicationId") == "dev.vitreo.launcher")
        val version = info.getString("versionName")
        require(version.length in 5..12 && Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)").matches(version))
        val base = "${StableRelease.REPO}/releases/download/v$version"
        val sums = text("$base/SHA256SUMS")
        val size = if (info.has("apkSize")) info.getLong("apkSize") else length("$base/vitreo-$version-release.apk")
        return parseWebsite(metadata, sums, size)
    }

    companion object {
        fun parseWebsite(metadata: String, checksums: String, size: Long): StableRelease {
            require(metadata.toByteArray().size <= 256*1024 && checksums.length <= 16*1024)
            val info = JSONObject(metadata)
            require(info.getString("buildType") == "release" && info.getString("applicationId") == "dev.vitreo.launcher")
            val version = info.getString("versionName")
            require(version.length in 5..12 && Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)").matches(version))
            val filename = "vitreo-$version-release.apk"
            require(info.getString("apk") == filename)
            val allowed = setOf(filename, "build-info.json", "mapping.txt", "signing-certificate.txt")
            val entries = linkedMapOf<String,String>()
            checksums.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val m = Regex("([a-f0-9]{64})  ([A-Za-z0-9_.-]+)").matchEntire(line) ?: error("Invalid release checksums.")
                require(m.groupValues[2] in allowed && entries.put(m.groupValues[2], m.groupValues[1]) == null)
            }
            require(entries.keys.containsAll(setOf(filename, "build-info.json", "signing-certificate.txt")))
            val metadataHash = MessageDigest.getInstance("SHA-256").digest(metadata.toByteArray()).joinToString("") { "%02x".format(it) }
            require(entries["build-info.json"] == metadataHash && entries[filename] == info.getString("apkSha256")) { "Release checksum mismatch." }
            require(Regex("[a-f0-9]{64}").matches(info.getString("signingCertificateSha256")))
            if (info.has("apkSize")) require(info.getLong("apkSize") == size)
            val page = "${StableRelease.REPO}/releases/tag/v$version"
            val raw = JSONObject().put("draft", false).put("prerelease", false).put("tag_name", "v$version").put("html_url", page)
                .put("body", "Release details: $page\nVerified GitHub release files over HTTPS.")
                .put("assets", JSONArray().put(JSONObject().put("name",filename).put("state","uploaded").put("size",size)
                    .put("digest","sha256:${entries[filename]}").put("browser_download_url","${StableRelease.REPO}/releases/download/v$version/$filename"))).toString()
            return StableRelease.parse(raw).also { require(it.code == info.getLong("versionCode")) }
        }
    }
}
