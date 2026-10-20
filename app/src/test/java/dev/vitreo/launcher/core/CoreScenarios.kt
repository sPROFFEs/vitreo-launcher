package dev.vitreo.launcher.core

import kotlin.random.Random

/** No Android/JUnit dependency: the exact same scenarios run offline and in Gradle. */
object CoreScenarios {
    private fun app(n: Int) = "com.example.app$n/.Main"
    private fun state(n: Int = 12) = LauncherState(initialized = true,
        home = (0 until n).map { HomeEntry.App(app(it)) }, knownApps = (0 until n).map(::app).toSet())
    private fun invariant(s: LauncherState) {
        val placed = s.placedApps()
        check(placed.size == placed.distinct().size) { "Duplicate placement" }
        check(placed.all(::isAppId)) { "Invalid component" }
        check(placed.size <= MAX_ITEMS)
        check(s.dock.size <= DOCK_SLOTS)
        check(s.home.filterIsInstance<HomeEntry.Folder>().all { it.apps.size in 2..MAX_FOLDER_APPS })
        check(s.home.filterIsInstance<HomeEntry.Folder>().map { it.id }.distinct().size ==
            s.home.filterIsInstance<HomeEntry.Folder>().size)
        check(s.sanitized() == s) { "State is not normalized" }
    }
    fun runAll(verbose: Boolean = false): Int {
        var count = 0
        fun scenario(name: String, body: () -> Unit) {
            try { body(); count++; if (verbose) println("PASS $name") }
            catch (t: Throwable) { throw AssertionError("FAILED $name", t) }
        }
        scenario("overlong folder ID cannot delete apps") {
            val s = state()
            check(LayoutOps.folder(s, app(0), app(1), "folder:" + "x".repeat(130)) == s)
        }
        scenario("empty folder ID cannot delete apps") {
            val s = state()
            check(LayoutOps.folder(s, app(0), app(1), "folder:") == s)
        }
        scenario("new dock app cannot evict an app at capacity") {
            val s = state(MAX_ITEMS).sanitized()
            check(LayoutOps.pinDock(s, app(MAX_ITEMS + 1)) == s)
        }
        scenario("new folder app cannot evict an app at capacity") {
            val s = LayoutOps.folder(state(MAX_ITEMS), app(0), app(1), "folder:full")
            check(LayoutOps.addToFolder(s, app(MAX_ITEMS + 1), "folder:full") == s)
        }
        scenario("new home app is rejected at capacity") {
            val s = state(MAX_ITEMS).sanitized()
            check(LayoutOps.addToHome(s, app(MAX_ITEMS + 1)) == s)
        }
        scenario("moving to dock at capacity preserves every app") {
            val s = state(MAX_ITEMS).sanitized()
            val next = LayoutOps.pinDock(s, app(42))
            check(next.dock == listOf(app(42)))
            check(next.placedApps().toSet() == s.placedApps().toSet()); invariant(next)
        }
        scenario("moving to folder at capacity preserves every app") {
            val s = LayoutOps.folder(state(MAX_ITEMS), app(0), app(1), "folder:full")
            val next = LayoutOps.addToFolder(s, app(42), "folder:full")
            check(next.placedApps().toSet() == s.placedApps().toSet()); invariant(next)
        }
        scenario("valid explicit component IDs") {
            check(isAppId("org.example.app/.Main")); check(isAppId("org.example.app/org.example.Main"))
            check(isAppId("org.example.app/.Outer\$Inner"))
        }
        scenario("intent URI and malformed IDs rejected") {
            listOf("", "intent://bad", "file:///etc", "com.example", "a/b/c", "../Main", "a/ Main").forEach { check(!isAppId(it)) }
        }
        scenario("geometry keeps four columns") { check(workspaceGeometry(360f, 520f, LayoutPreset()).columns == 4) }
        scenario("geometry never yields empty pages") { check(workspaceGeometry(128f, 48f, LayoutPreset()).pageSize >= 4) }
        scenario("geometry limits oversized displays") { check(workspaceGeometry(2000f, 2000f, LayoutPreset()).rows <= 7) }
        scenario("geometry is finite for malformed inputs") {
            val g = workspaceGeometry(Float.NaN, Float.POSITIVE_INFINITY, LayoutPreset(Float.NaN), Float.NaN)
            check(g.iconSize.isFinite() && g.cells.rowHeight.isFinite() && g.cells.cellWidth.isFinite())
        }
        scenario("font scaling reduces capacity") {
            check(workspaceGeometry(360f, 500f, LayoutPreset(), 2f).rows <= workspaceGeometry(360f, 500f, LayoutPreset(), 1f).rows)
        }
        scenario("cell coordinates") { val c = HomeCellLayout(80f, 100f, 2f); check(c.x(2) == 162f && c.y(3) == 300f && c.spanHeight(2) == 200f) }
        scenario("page count boundaries") { check(pageCount(0, 24) == 1 && pageCount(24, 24) == 1 && pageCount(25, 24) == 2) }
        scenario("page count overflow") { check(pageCount(Int.MAX_VALUE, 4) == 536870912) }
        scenario("page count rejects invalid input") { check(runCatching { pageCount(-1, 4) }.isFailure); check(runCatching { pageCount(1, 0) }.isFailure) }
        scenario("appearance clamps unsafe numbers") {
            val a = Appearance(Float.NaN, refraction = Float.POSITIVE_INFINITY, frost = -2f, palette = 999).sanitized()
            check(a.iconSize == 60f && a.refraction.isFinite() && a.frost == 0f && a.palette == WALLPAPER_NAMES.lastIndex)
        }
        scenario("dock wins duplicate conflicts") {
            val s = state().copy(dock = listOf(app(0), app(0), app(1), app(2), app(3), app(4))).sanitized()
            invariant(s); check(s.dock.size == 4); check(s.home.none { it.id == app(0) })
        }
        scenario("invalid imported IDs removed") { invariant(state().copy(home = state().home + HomeEntry.App("intent://bad")).sanitized()) }
        scenario("initial scan fills dock and home") {
            val s = LayoutOps.reconcile(LauncherState(), (0..8).map(::app)); invariant(s)
            check(s.initialized && s.dock == (0..3).map(::app) && s.home.size == 5)
        }
        scenario("initial scan deduplicates") { invariant(LayoutOps.reconcile(LauncherState(), listOf(app(0), app(0), app(1)))) }
        scenario("newly installed app appended") { val s = LayoutOps.reconcile(state(), (0..12).map(::app)); check(s.home.last().id == app(12)); invariant(s) }
        scenario("temporarily unavailable placements retained") { val s = LayoutOps.reconcile(state(), listOf(app(0))); check(s.home.size == 12); invariant(s) }
        scenario("removed shortcut stays removed after refresh") {
            val s = LayoutOps.reconcile(LayoutOps.removeApp(state(), app(2)), (0..11).map(::app)); check(app(2) !in s.placedApps()); invariant(s)
        }
        scenario("adding to home moves existing shortcut") { val s = LayoutOps.addToHome(state(), app(1)); check(s.home.last().id == app(1)); invariant(s) }
        scenario("pinning removes former home slot") { val s = LayoutOps.pinDock(state(), app(0)); check(s.dock == listOf(app(0)) && s.home.size == 11); invariant(s) }
        scenario("full dock is not silently replaced") {
            val s = (0..3).fold(state()) { acc, i -> LayoutOps.pinDock(acc, app(i)) }
            check(LayoutOps.pinDock(s, app(4)) == s); invariant(s)
        }
        scenario("unpin through home retains app") { val s = LayoutOps.addToHome(LayoutOps.pinDock(state(), app(1)), app(1)); check(s.dock.isEmpty()); invariant(s) }
        scenario("drag forward one slot really moves") { val s = LayoutOps.moveToSlot(state(), app(0), app(1)); check(s.home[1].id == app(0)); invariant(s) }
        scenario("drag backward one slot") { val s = LayoutOps.moveToSlot(state(), app(1), app(0)); check(s.home[0].id == app(1)); invariant(s) }
        scenario("move clamps both ends without overflow") {
            check(LayoutOps.move(state(), app(2), Int.MAX_VALUE).home.last().id == app(2))
            check(LayoutOps.move(state(), app(2), Int.MIN_VALUE).home.first().id == app(2))
        }
        scenario("unknown drag targets are harmless") { check(LayoutOps.moveToSlot(state(), app(0), app(90)) == state()) }
        scenario("folder created at earliest selected position") {
            val s = LayoutOps.folder(state(), app(5), app(1), "folder:one")
            check(s.home[1] is HomeEntry.Folder && s.placedApps().size == 12); invariant(s)
        }
        scenario("folder cannot contain same app twice") { check(LayoutOps.folder(state(), app(1), app(1), "folder:one") == state()) }
        scenario("moving app into folder removes former slot") {
            val s = LayoutOps.addToFolder(LayoutOps.folder(state(), app(0), app(1), "folder:one"), app(2), "folder:one")
            check(s.home.filterIsInstance<HomeEntry.Folder>().single().apps.size == 3); invariant(s)
        }
        scenario("one app left in folder becomes ordinary shortcut") {
            val s = LayoutOps.removeApp(LayoutOps.folder(state(), app(0), app(1), "folder:one"), app(0))
            check(s.home.none { it is HomeEntry.Folder }); check(app(1) in s.placedApps()); invariant(s)
        }
        scenario("moving between folders preserves all other apps") {
            val s = LayoutOps.folder(LayoutOps.folder(state(), app(0), app(1), "folder:a"), app(2), app(3), "folder:b")
            val result = LayoutOps.addToFolder(s, app(0), "folder:b")
            check(result.placedApps().toSet() == s.placedApps().toSet()); invariant(result)
        }
        scenario("dissolve preserves folder order") {
            val s = LayoutOps.dissolve(LayoutOps.folder(state(), app(0), app(1), "folder:a"), "folder:a")
            check(s.home == state().home); invariant(s)
        }
        scenario("folder name normalized") {
            val s = LayoutOps.renameFolder(LayoutOps.folder(state(), app(0), app(1), "folder:a"), "folder:a", "  ")
            check(s.home.filterIsInstance<HomeEntry.Folder>().single().title == "Folder"); invariant(s)
        }
        scenario("global placement cap includes folders and dock") {
            val folders = (0..30).map { f -> HomeEntry.Folder("folder:$f", "Folder", (0 until 100).map { app(f * 100 + it) }) }
            val s = LauncherState(initialized = true, home = folders, dock = (5000..5003).map(::app)).sanitized()
            check(s.placedApps().size == MAX_ITEMS); invariant(s)
        }
        scenario("known app list is bounded") { val s = state().copy(knownApps = (0..10000).map(::app).toSet()).sanitized(); check(s.knownApps.size == 8000); invariant(s) }
        scenario("accent-insensitive case-insensitive search") { check(searchKey("  C\u00c1mara  ") == "camara"); check(searchKey("M\u00fasica") == "musica") }
        scenario("2000 randomized operations preserve invariants") {
            val random = Random(1010)
            var s = state(80)
            repeat(2000) { step ->
                val id = app(random.nextInt(100))
                s = when (random.nextInt(9)) {
                    0 -> LayoutOps.addToHome(s, id)
                    1 -> LayoutOps.removeApp(s, id)
                    2 -> LayoutOps.pinDock(s, id)
                    3 -> LayoutOps.move(s, id, random.nextInt(-100, 100))
                    4 -> LayoutOps.moveToSlot(s, id, app(random.nextInt(100)))
                    5 -> LayoutOps.folder(s, id, app(random.nextInt(100)), "folder:$step")
                    6 -> s.home.filterIsInstance<HomeEntry.Folder>().firstOrNull()?.let { LayoutOps.addToFolder(s, id, it.id) } ?: s
                    7 -> s.home.filterIsInstance<HomeEntry.Folder>().lastOrNull()?.let { LayoutOps.dissolve(s, it.id) } ?: s
                    else -> LayoutOps.reconcile(s, (0 until random.nextInt(100)).map(::app))
                }
                invariant(s)
            }
        }
        return count
    }
}
fun main() { println("CORE: ${CoreScenarios.runAll(verbose = true)} scenarios passed; randomized operations: 2000") }
