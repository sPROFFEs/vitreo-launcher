package dev.vitreo.launcher

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import dev.vitreo.launcher.core.NewsSettings
import dev.vitreo.launcher.net.HttpsClient
import dev.vitreo.launcher.news.NewsRepository
import dev.vitreo.launcher.news.NewsImages
import dev.vitreo.launcher.news.NewsLoader
import dev.vitreo.launcher.update.*
import kotlinx.coroutines.runBlocking
import java.io.File

/** Explicit manual probe, not a JUnit test and never automatically selected by CI. */
class SelfUpdateProbe : Instrumentation() {
    private var args=Bundle()
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments); args=arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            if(args.getString("mode")=="updates-web") {
                runBlocking {
                    val release=ReleaseFetcher().fetch(Long.MAX_VALUE)
                    check(StableRelease.parse(release.raw)==release)
                    finish(0,Bundle().apply { putString("websiteHttps","passed");putString("stableVersion",release.version);putLong("apkBytes",release.size);putString("sha256",release.sha256) })
                }
                return
            }
            if(args.getString("mode") in setOf("network","news")) {
                runBlocking {
                    val release=if(args.getString("mode")=="network") StableRelease.parse(HttpsClient.text(StableRelease.API,HttpsClient.githubHosts)) else null
                    val settings=NewsSettings(true,args.getString("market") ?: "US",args.getString("topic") ?: "top",args.getString("provider") ?: "direct")
                    val feed=NewsRepository(targetContext,java.util.Locale.US).load(settings,true)
                    check(feed.error==null && feed.articles.isNotEmpty()) { feed.error ?: "No headlines" }
                    var photos=0
                    if(args.getString("images")=="true") {
                        val images=NewsImages(targetContext)
                        for(url in feed.articles.mapNotNull { it.imageUrl }.take(2)) if(images.load(url)!=null) photos++
                        check(photos>0) { "No publisher image could be downloaded and decoded" }
                    }
                    val offline=NewsRepository(targetContext,java.util.Locale.US,NewsLoader({throw java.net.UnknownHostException("offline probe")})).load(settings,true)
                    check(offline.articles==feed.articles && offline.error!=null) { "Offline headlines/photos were not preserved" }
                    finish(0,Bundle().apply { putString("https","passed");putString("stableVersion",release?.version ?: "not checked");
                        putInt("headlines",feed.articles.size);putString("source",feed.sourceName);putInt("photos",photos);putBoolean("offlineCache",true) })
                }
                return
            }
            val qemu=uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { p -> java.io.FileInputStream(p.fileDescriptor).bufferedReader().readText().trim() }
            check(qemu=="1") { "Manual update probe requires a disposable emulator." }
            val apk=File(targetContext.getExternalFilesDir(null),"update-probe.apk")
            val version=args.getString("version") ?: error("Missing fixture version")
            val code=args.getString("code")?.toLong() ?: error("Missing fixture version code")
            val release=StableRelease(version,code,"",apk.length(),ApkVerifier.sha256(apk),"","","")
            startActivitySync(Intent(targetContext,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val session=SelfUpdateInstaller.stage(targetContext,apk,release)
            sendStatus(0,Bundle().apply { putString("stage","passed");putInt("session",session) })
            SelfUpdateInstaller.commit(targetContext,session)
            // Package replacement kills instrumentation. The external observer must prove the
            // native version transition. Finishing here would force-stop the installer sender.
            Thread.sleep(30_000)
            finish(1,Bundle().apply { putString("error","Android did not replace the package within 30 seconds.") })
        } catch(e:Exception) { finish(1,Bundle().apply {putString("error",e.toString())}) }
    }
}
