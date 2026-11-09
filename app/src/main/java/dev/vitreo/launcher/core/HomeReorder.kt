package dev.vitreo.launcher.core

/** Slot identities stay fixed while the icons occupying them animate out of the way. */
data class HomeDropSlot(val index: Int, val column: Int, val row: Int)

fun homeAppSlots(page: HomeGridPage, rows: Int): List<Pair<Int, Int>> {
    val widgets = page.widgets + listOfNotNull(page.clockPlacement, page.calendarPlacement)
    return (0 until rows).flatMap { row -> (0 until GRID_COLUMNS).map { it to row } }.filter { (x, y) ->
        widgets.none { x in it.column until it.column + it.columns && y in it.row until it.row + it.rows } &&
            !(page.builtins && page.clockPlacement == null && page.calendarPlacement == null && y < 2 &&
                (x < 2 && page.builtinClock || x >= 2 && page.builtinCalendar))
    }
}

/** Coordinates are relative to the stationary page content, including its scroll offset. */
fun homeDropSlot(pages: List<HomeGridPage>, page: Int, rows: Int, width: Float, rowHeight: Float,
    x: Float, y: Float, lastIndex: Int): HomeDropSlot? {
    if (page !in pages.indices || lastIndex < 0 || width <= 0 || rowHeight <= 0 ||
        listOf(width, rowHeight, x, y).any { !it.isFinite() } || x < 0 || x >= width || y < 0 || y >= rows * rowHeight) return null
    val column = (x / (width / GRID_COLUMNS)).toInt()
    val row = (y / rowHeight).toInt()
    val slot = homeAppSlots(pages[page], rows).indexOf(column to row)
    if (slot < 0) return null // A widget owns this rectangle.
    val before = pages.take(page).sumOf { homeAppSlots(it, rows).size }
    if (before > lastIndex) return null // An ordered grid cannot invent empty preceding pages.
    return HomeDropSlot((before + slot).coerceAtMost(lastIndex), column, row)
}

fun previewHomeMove(entries: List<HomeEntry>, moving: HomeEntry, index: Int): List<HomeEntry> =
    entries.filterNot { it.id == moving.id }.toMutableList().apply { add(index.coerceIn(0, size), moving) }

/** Once paging wins, a later hold or diagonal reversal cannot turn that swipe into an icon drag. */
fun homeHorizontalSwipe(x: Float, y: Float, slop: Float): Boolean =
    x.isFinite() && y.isFinite() && kotlin.math.abs(x) > slop && kotlin.math.abs(x) > kotlin.math.abs(y) * .85f
