package dev.vitreo.launcher.core

import dev.vitreo.launcher.data.StateCodec
import org.junit.Assert.*
import org.junit.Test

class FolderAndIconLayoutTest {
    private val ids=(0..9).map { "app$it/App$it" }
    @Test fun oneHundredDpIconsAreAvailableWithoutChangingOtherPreferences() {
        val a=Appearance(iconSize=100f,libraryIconSize=100f,iosClassic=true).sanitized()
        assertEquals(100f,a.iconSize);assertEquals(100f,a.libraryIconSize)
        assertEquals(a,StateCodec.decode(StateCodec.encode(LauncherState(appearance=a))).appearance)
        assertEquals(100f,Appearance(iconSize=200f).sanitized().iconSize)
        assertEquals(0f,Appearance(libraryIconSize=Float.NaN).sanitized().libraryIconSize)
    }
    @Test fun libraryDefaultsToTheActualHomeSizeButCanChooseAnIndependentSize() {
        val a=Appearance(iconSize=100f)
        val home=workspaceGeometry(360f,600f,LayoutPreset(iconSize=100f)).iconSize
        assertEquals(home,libraryIconSize(a,400f))
        assertEquals(100f,libraryIconSize(a.copy(libraryIconSize=100f),400f))
        assertEquals(2,appGridColumns(290f,100f));assertEquals(3,appGridColumns(290f,60f))
    }
    @Test fun multipleAppsBecomeOneFolderWithoutLosingOtherAppsOrTheirOrder() {
        val before=LayoutOps.reconcile(LauncherState(),ids)
        val selected=listOf(ids[7],ids[1],ids[5])
        val after=LayoutOps.createFolder(before,selected,"Work","folder:work")
        assertEquals(ids.toSet(),after.placedApps().toSet());assertEquals(ids.size,after.placedApps().size)
        val folder=after.home.filterIsInstance<HomeEntry.Folder>().single()
        assertEquals(selected,folder.apps);assertEquals("Work",folder.title)
        assertEquals(before.home.map {it.id}.filterNot {it in selected},after.home.filterNot {it is HomeEntry.Folder}.map {it.id})
        assertFalse(ids[1] in after.dock)
    }
    @Test fun invalidOrOversizedSelectionsDoNotEvictExistingApps() {
        val before=LayoutOps.reconcile(LauncherState(),ids)
        assertEquals(before,LayoutOps.createFolder(before,listOf(ids[0]),"Bad","folder:test"))
        assertEquals(before,LayoutOps.createFolder(before,listOf(ids[0],"intent://evil"),"Bad","folder:test"))
        val full=LayoutOps.reconcile(LauncherState(),(0 until MAX_ITEMS).map {"app$it/App$it"})
        assertEquals(full,LayoutOps.createFolder(full,listOf("extra/App",ids[5]),"Bad","folder:test"))
    }
}
