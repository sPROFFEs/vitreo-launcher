package dev.vitreo.launcher.core

/** Widget IDs are installation-local. Never put these in portable LauncherState backups. */
data class HomeWidgetPlacement(val id: Int, val page: Int = 0, val column: Int = 0, val row: Int = 0,
    val columns: Int = 2, val rows: Int = 2) {
    fun sanitized() = copy(page = page.coerceIn(0, 63), column = column.coerceIn(0, 3), row = row.coerceIn(0, 5),
        columns = if (columns <= 2) 2 else 4, rows = if (rows <= 2) 2 else 4)
}

data class HomeAppCell(val entry: HomeEntry, val column: Int, val row: Int)
data class HomeGridPage(val apps: List<HomeAppCell>, val widgets: List<HomeWidgetPlacement>, val builtins: Boolean)

/** Reserve widget rectangles first, then flow apps through free cells in their saved order.
 * Repacking after a rotation/font/icon-size change never removes an app or a widget. */
fun planHomeGrid(entries: List<HomeEntry>, widgets: List<HomeWidgetPlacement>, rows: Int, builtins: Boolean): List<HomeGridPage> {
    require(rows in 1..6)
    class Page(val hasBuiltinPair: Boolean) {
        val occupied = BooleanArray(rows * GRID_COLUMNS)
        val apps = mutableListOf<HomeAppCell>()
        val widgets = mutableListOf<HomeWidgetPlacement>()
        init { if (hasBuiltinPair) repeat(2 * GRID_COLUMNS) { occupied[it] = true } }
        fun fits(column: Int, row: Int, width: Int, height: Int): Boolean =
            column + width <= GRID_COLUMNS && row + height <= rows &&
                (row until row + height).all { y -> (column until column + width).all { x -> !occupied[y * GRID_COLUMNS + x] } }
        fun reserve(column: Int, row: Int, width: Int, height: Int) {
            repeat(height) { y -> repeat(width) { x -> occupied[(row + y) * GRID_COLUMNS + column + x] = true } }
        }
    }
    val pages = mutableListOf(Page(builtins && rows >= 4))
    fun page(index: Int): Page { while (pages.size <= index) pages.add(Page(false)); return pages[index] }
    widgets.distinctBy { it.id }.filter { it.id >= 0 }.forEach { raw ->
        val widget = raw.sanitized().let { it.copy(rows = minOf(it.rows, rows)) }
        var index = widget.page
        while (true) {
            val p = page(index)
            val preferred = widget.row * GRID_COLUMNS + widget.column
            val cells = listOf(preferred) + p.occupied.indices.filter { it != preferred }
            val slot = cells.firstOrNull { p.fits(it % GRID_COLUMNS, it / GRID_COLUMNS, widget.columns, widget.rows) }
            if (slot != null) {
                val column = slot % GRID_COLUMNS; val row = slot / GRID_COLUMNS
                p.reserve(column, row, widget.columns, widget.rows)
                p.widgets.add(widget.copy(page = index, column = column, row = row))
                break
            }
            index++
        }
    }
    var appPage = 0
    entries.forEach { entry ->
        while (true) {
            val p = page(appPage)
            val slot = p.occupied.indexOfFirst { !it }
            if (slot >= 0) {
                p.occupied[slot] = true
                p.apps.add(HomeAppCell(entry, slot % GRID_COLUMNS, slot / GRID_COLUMNS))
                break
            }
            appPage++
        }
    }
    return pages.map { HomeGridPage(it.apps.toList(), it.widgets.toList(), it.hasBuiltinPair) }
}
