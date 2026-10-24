package dev.vitreo.launcher.update

import org.json.JSONObject

data class StableRelease(val version: String, val code: Long, val url: String, val size: Long,
    val sha256: String, val page: String, val notes: String, val raw: String) {
    companion object {
        const val API = "https://api.github.com/repos/sPROFFEs/vitreo-launcher/releases/latest"
        const val REPO = "https://github.com/sPROFFEs/vitreo-launcher"
        fun parse(raw: String): StableRelease {
            val obj = JSONObject(raw)
            require(!obj.getBoolean("draft") && !obj.getBoolean("prerelease")) { "A stable release is required." }
            val tag = obj.getString("tag_name")
            require(Regex("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]?)\\.(0|[1-9][0-9]?)").matches(tag)) { "Unsupported release version." }
            val version = tag.drop(1); val parts = version.split('.').map(String::toLong)
            require(parts[0] <= 214747L)
            val code = parts[0] * 10000 + parts[1]*100 + parts[2]
            require(code in 1..Int.MAX_VALUE.toLong())
            val filename = "vitreo-$version-release.apk"
            val assets = obj.getJSONArray("assets")
            require(assets.length() <= 100)
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name") == filename }
            require(matches.size == 1) { "Release must contain exactly one signed APK." }
            val asset = matches.single()
            require(asset.getString("state") == "uploaded")
            val url = "$REPO/releases/download/$tag/$filename"
            require(asset.getString("browser_download_url") == url)
            val page = "$REPO/releases/tag/$tag"
            require(obj.getString("html_url") == page)
            val size = asset.getLong("size"); require(size in 1..64L*1024*1024)
            val digest = asset.getString("digest")
            require(Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) { "Missing release checksum." }
            return StableRelease(version,code,url,size,digest.substringAfter(':').lowercase(),page,obj.optString("body").take(16000),raw)
        }
    }
}
