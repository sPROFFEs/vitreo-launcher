package dev.vitreo.launcher

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vitreo.launcher.core.IconOverride
import dev.vitreo.launcher.data.IconPacks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPackDeviceTest {
    @Test fun bundledHudVectorsInflateAndDrawOnAndroid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.drawable.cyber_ui_search, R.drawable.cyber_ui_settings, R.drawable.cyber_ui_chevron_left,
            R.drawable.cyber_ui_chevron_right, R.drawable.cyber_ui_wifi, R.drawable.cyber_ui_bluetooth,
            R.drawable.cyber_ui_plus, R.drawable.cyber_ui_minus, R.drawable.cyber_ui_menu_dots,
            R.drawable.cyber_ui_volume_high, R.drawable.cyber_ui_home, R.drawable.cyber_ui_sliders,
            R.drawable.cyber_ui_check).forEach { id ->
                val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                val drawable = checkNotNull(context.getDrawable(id)); drawable.setBounds(0, 0, 96, 96)
                drawable.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(96 * 96); bitmap.getPixels(pixels, 0, 96, 0, 0, 96, 96)
                assertTrue("HUD vector must have visible geometry: $id", pixels.any { Color.alpha(it) > 0 })
            }
    }
    private fun parse(xml: String) = IconPacks.parse(xml.byteInputStream())
    @Test fun componentMappingsPreserveAliasesAndRejectUnsafeNames() {
        val index = parse("""<resources>
            <item component="ComponentInfo{com.example.app/.Main}" drawable="one"/>
            <item component="ComponentInfo{com.example.app/.Alias}" drawable="two"/>
            <item component="ComponentInfo{com.example.app/.Main}" drawable="duplicate"/>
            <item drawable="alternate"/><item drawable="../unsafe"/>
            </resources>""")
        assertEquals("one", index.mappings["com.example.app/com.example.app.Main"])
        assertEquals("two", index.mappings["com.example.app/com.example.app.Alias"])
        assertNull(index.mappings["com.example.app/com.example.app.Unknown"])
        assertTrue("alternate" in index.drawables)
        assertFalse("../unsafe" in index.drawables)
    }
    @Test fun malformedAndEntityXmlCannotBeUsedAsAnIconIndex() {
        assertTrue(runCatching { parse("<resources><item") }.isFailure)
        assertTrue(runCatching { parse("""<!DOCTYPE resources [<!ENTITY secret SYSTEM "file:///proc/self/cmdline">]><resources>&secret;</resources>""") }.isFailure)
    }
    @Test fun largeRealisticIndexIsAcceptedButByteAndItemLimitsAreEnforced() {
        val body = buildString {
            append("<resources>")
            repeat(50_000) { append("<item component=\"ComponentInfo{com.example.app$it/.Main}\" drawable=\"icon_$it\"/>") }
            append("</resources>")
        }
        assertEquals(50_000, parse(body).mappings.size)
        assertTrue(runCatching { parse("<resources>" + " ".repeat(IconPacks.MAX_XML_BYTES) + "</resources>") }.isFailure)
        val tooMany = "<resources>" + "<item drawable=\"one\"/>".repeat(IconPacks.MAX_ENTRIES + 1) + "</resources>"
        assertTrue(runCatching { parse(tooMany) }.isFailure)
    }
    @Test fun installedPackResourcesOverridesUpdateAndRemovalUsePublicAndroidApis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
        }
        check(shell("getprop ro.kernel.qemu").trim() == "1") { "Fixture installation requires a disposable emulator." }
        val pack = "dev.vitreo.fixture.uninstall"
        try {
            check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            val reader = IconPacks(context)
            assertTrue(reader.refresh().any { it.packageName == pack })
            assertTrue("fixture_red" in reader.names(pack))
            val revision = reader.revision
            reader.refresh(); assertEquals("Unchanged inventory must retain icon caches", revision, reader.revision)
            val cyan = checkNotNull(reader.resolve("$pack/$pack.MainActivity", pack, null))
            assertEquals(Color.CYAN, cyan.getPixel(96, 96))
            val red = checkNotNull(reader.resolve("$pack/$pack.MainActivity", pack, IconOverride(pack, "fixture_red")))
            assertEquals(Color.RED, red.getPixel(96, 96))
            assertNull(reader.resolve("$pack/$pack.FolderAlias", pack, null))
            assertNull(reader.resolve("$pack/$pack.Unknown", pack, null))
            check(shell("pm install -r -t /data/local/tmp/vitreo-uninstall-fixture.apk").contains("Success"))
            reader.refresh(); assertTrue(reader.revision > revision)
            assertNotNull(reader.resolve("$pack/$pack.MainActivity", pack, null))
            check(shell("pm uninstall $pack").contains("Success"))
            reader.refresh(); assertFalse(reader.discover().any { it.packageName == pack })
            assertNull(reader.resolve("$pack/$pack.MainActivity", pack, IconOverride(pack, "fixture_red")))
        } finally { shell("pm uninstall $pack") }
    }
}
