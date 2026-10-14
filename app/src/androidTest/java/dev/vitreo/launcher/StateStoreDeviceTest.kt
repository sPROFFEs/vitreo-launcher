package dev.vitreo.launcher

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vitreo.launcher.core.HomeEntry
import dev.vitreo.launcher.core.LauncherState
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
}
