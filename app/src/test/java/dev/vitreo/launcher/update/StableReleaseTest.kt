package dev.vitreo.launcher.update

import org.junit.Test
import org.junit.Assert.*
import org.json.*
class StableReleaseTest {
    private fun fixture(version: String = "0.8.1") = JSONObject().put("draft",false).put("prerelease",false)
        .put("tag_name","v$version").put("html_url","${StableRelease.REPO}/releases/tag/v$version")
        .put("body","Changes").put("assets",JSONArray().put(JSONObject().put("name","vitreo-$version-release.apk")
            .put("state","uploaded").put("size",1234).put("digest","sha256:"+"a".repeat(64))
            .put("browser_download_url","${StableRelease.REPO}/releases/download/v$version/vitreo-$version-release.apk")))
    private fun rejected(obj: JSONObject) { assertTrue(runCatching { StableRelease.parse(obj.toString()) }.isFailure) }
    @Test fun stableVersionMatchesThePublishedNamingAndVersionCode() {
        val r=StableRelease.parse(fixture("1.23.45").toString()); assertEquals(12345L,r.code); assertEquals("1.23.45",r.version)
    }
    @Test fun draftsPrereleasesAndMalformedVersionsAreExcluded() {
        rejected(fixture().put("draft",true)); rejected(fixture().put("prerelease",true))
        for (v in listOf("0.8.1-beta","0.08.1","0.8.100","-1.0.0","9999999999999999999999.0.0")) rejected(fixture(v))
    }
    @Test fun releaseMustHaveExactlyOneExpectedSignedApk() {
        rejected(fixture().put("assets",JSONArray()))
        rejected(fixture().apply { val a=getJSONArray("assets"); a.put(a.getJSONObject(0)) })
        rejected(fixture().apply { getJSONArray("assets").getJSONObject(0).put("name","debug.apk") })
    }
    @Test fun foreignDownloadsMissingDigestsAndOversizeApksAreRejected() {
        for ((key,value) in listOf("browser_download_url" to "https://evil.example/a.apk","digest" to "sha256:bad","size" to 100000000,"state" to "new"))
            rejected(fixture().apply { getJSONArray("assets").getJSONObject(0).put(key,value) })
        rejected(fixture().put("html_url","https://evil.example"))
    }
}
