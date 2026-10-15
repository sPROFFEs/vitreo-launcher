package dev.vitreo.launcher

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.core.LauncherState
import dev.vitreo.launcher.core.PhotoTransform
import dev.vitreo.launcher.core.photoFitZoom
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.vitreo.launcher.data.StateStore
import dev.vitreo.launcher.graphics.WallpaperStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses isolated temporary directories, never the live launcher's layout. */
@RunWith(AndroidJUnit4::class)
class StateStoreDeviceTest {
    private lateinit var directory: File
    private lateinit var context: Context

    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        directory = File(base.cacheDir, "storage-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
    }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun atomicStateRoundTripSurvivesNewStoreInstance() {
        val state = LauncherState(initialized = true,
            home = listOf(HomeEntry.App("com.example/.Main"))).sanitized()
        StateStore(context).save(state)
        val loaded = StateStore(context).load()
        assertEquals(state, loaded.state)
        assertNull(loaded.warning)
    }
    @Test fun corruptStateIsPreservedAndDoesNotCrash() {
        File(directory, "layout.json").writeText("not-json")
        val loaded = StateStore(context).load()
        assertFalse(loaded.state.initialized)
        assertNotNull(loaded.warning)
        assertTrue(directory.listFiles().orEmpty().any { it.name.startsWith("layout-corrupt-") })
    }
    @Test fun absentPhotoFallsBackToProceduralWallpaper() {
        val frame = WallpaperStore(context).load(usePhoto = true, palette = 2)
        assertTrue(frame.sharp.width > 0 && frame.sharp.height > 0)
        assertTrue(frame.frosted.width > 0 && frame.frosted.height > 0)
    }
    @Test fun fittingPhotoKeepsBothEdgesAndPreservesTheOriginalForLaterEdits() {
        val photo = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.RED)
            drawRect(80f, 0f, 160f, 90f, Paint().apply { color = Color.BLUE })
        }
        val store = WallpaperStore(context)
        store.savePhoto(photo)
        val fit = PhotoTransform(photoFitZoom(160f, 90f, 400f, 900f))
        val fitted = store.load(true, 0, fit, 400, 900)
        assertEquals(Color.RED, fitted.sharp.getPixel(2, 450))
        assertEquals(Color.BLUE, fitted.sharp.getPixel(397, 450))
        val original = store.readPhoto()!!
        assertEquals(160, original.width)
        assertEquals(90, original.height)
        val preview = Bitmap.createBitmap(400, 900, Bitmap.Config.ARGB_8888)
        WallpaperStore.drawPhoto(Canvas(preview), store.frame(original), 400f, 900f, fit)
        assertTrue("The editor and committed wallpaper must match", preview.sameAs(fitted.sharp))
    }
}
