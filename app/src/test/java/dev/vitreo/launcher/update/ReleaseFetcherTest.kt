package dev.vitreo.launcher.update

import dev.vitreo.launcher.net.RateLimitedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ReleaseFetcherTest {
    private fun info(version: String = "0.9.0") = JSONObject().put("buildType","release").put("applicationId","dev.vitreo.launcher")
        .put("versionName",version).put("versionCode",900).put("apk","vitreo-$version-release.apk")
        .put("apkSize",1234).put("apkSha256","a".repeat(64)).put("signingCertificateSha256","b".repeat(64)).toString()
    private fun sums(raw: String): String {
        val hash=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash  build-info.json\n${"a".repeat(64)}  vitreo-0.9.0-release.apk\n${"b".repeat(64)}  signing-certificate.txt\n"
    }
    @Test fun websiteMetadataRetainsTheSameStrictReleaseAndCacheContract() {
        val raw=info(); val r=ReleaseFetcher.parseWebsite(raw,sums(raw),1234)
        assertEquals(900L,r.code);assertEquals("0.9.0",r.version);assertEquals("a".repeat(64),r.sha256)
        assertEquals(r,StableRelease.parse(r.raw))
    }
    @Test fun apiRateLimitUsesPinnedWebsiteFilesAndRemembersTheServerDeadline() = runBlocking {
        val raw=info();val requests=mutableListOf<String>();var deadline=0L
        val fetcher=ReleaseFetcher(text={ url -> requests+=url; when {
            url==StableRelease.API -> throw RateLimitedException(500_000)
            url.endsWith("build-info.json") -> raw
            url.endsWith("/v0.9.0/SHA256SUMS") -> sums(raw)
            else -> error("Unexpected endpoint: $url")
        } },clock={100_000})
        val r=fetcher.fetch(rateLimited={deadline=it})
        assertEquals(500_000L,deadline);assertEquals(900L,r.code);assertEquals(3,requests.size)
        requests.clear();fetcher.fetch(apiRetryAt=deadline)
        assertEquals(2,requests.size);assertFalse(requests.any { it.contains("api.github.com") })
    }
    @Test fun oldReleaseMetadataObtainsTheBoundedApkLengthWithHead() = runBlocking {
        val raw=JSONObject(info()).apply {remove("apkSize")}.toString();val heads=mutableListOf<String>()
        val fetcher=ReleaseFetcher(text={ if(it.endsWith("build-info.json")) raw else sums(raw) },length={ heads+=it;1234 })
        assertEquals(1234L,fetcher.fetch(Long.MAX_VALUE).size)
        assertEquals(listOf("${StableRelease.REPO}/releases/download/v0.9.0/vitreo-0.9.0-release.apk"),heads)
    }
    @Test fun changedMetadataChecksumWrongIdentityVersionSizeAndUnsafeNamesAreRejected() {
        fun rejected(raw: String, sum: String=sums(raw), size: Long=1234) { assertTrue(runCatching { ReleaseFetcher.parseWebsite(raw,sum,size) }.isFailure) }
        rejected(info()+" ",sums(info()))
        for ((key,value) in listOf("buildType" to "preview","applicationId" to "dev.evil.app","versionCode" to 901,"apk" to "../other.apk","apkSha256" to "bad","versionName" to "0.9.0-beta"))
            rejected(JSONObject(info()).put(key,value).toString())
        rejected(info(),size=1235);rejected(info(),sums(info())+sums(info()))
    }
    @Test fun invalidApiMetadataAndCancellationDoNotSilentlySwitchTrustPaths() = runBlocking {
        for(failure in listOf(IllegalArgumentException("invalid metadata"),CancellationException("cancelled"))) {
            var calls=0
            val result=runCatching { ReleaseFetcher(text={calls++;throw failure}).fetch() }
            assertSame(failure,result.exceptionOrNull());assertEquals(1,calls)
        }
    }
}
