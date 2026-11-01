package dev.vitreo.launcher.core

const val ID_BUILTIN_CLOCK = -100
const val ID_BUILTIN_CALENDAR = -101

fun isBuiltinWidget(id: Int): Boolean = id == ID_BUILTIN_CLOCK || id == ID_BUILTIN_CALENDAR

/** Widget IDs are installation-local. Never put these in portable LauncherState backups. */
data class HomeWidgetPlacement(val id: Int, val page: Int = 0, val column: Int = 0, val row: Int = 0,
    val columns: Int = 2, val rows: Int = 2) {
    fun sanitized() = copy(page = page.coerceIn(0, 63), column = column.coerceIn(0, 3), row = row.coerceIn(0, 5),
        columns = if (columns <= 2) 2 else 4, rows = if (rows <= 2) 2 else 4)
}

data class HomeAppCell(val entry: HomeEntry, val column: Int, val row: Int)
data class HomeGridPage(
    val apps: List<HomeAppCell>,
    val widgets: List<HomeWidgetPlacement>,
    val builtins: Boolean,
    val builtinClock: Boolean = builtins,
    val builtinCalendar: Boolean = builtins,
    val clockPlacement: HomeWidgetPlacement? = null,
    val calendarPlacement: HomeWidgetPlacement? = null,
)

/** Reserve widget rectangles first, then flow apps through free cells in their saved order.
 * Repacking after a rotation/font/icon-size change never removes an app or a widget. */
fun planHomeGrid(entries: List<HomeEntry>, widgets: List<HomeWidgetPlacement>, rows: Int, builtins: Boolean,
    builtinClock: Boolean = builtins, builtinCalendar: Boolean = builtins): List<HomeGridPage> {
    require(rows in 1..6)
    val hasClock = builtins && builtinClock && rows >= 4
    val hasCalendar = builtins && builtinCalendar && rows >= 4
    class Page {
        val occupied = BooleanArray(rows * GRID_COLUMNS)
        val apps = mutableListOf<HomeAppCell>()
        val widgets = mutableListOf<HomeWidgetPlacement>()
        var clockPlacement: HomeWidgetPlacement? = null
        var calendarPlacement: HomeWidgetPlacement? = null

        fun fits(column: Int, row: Int, width: Int, height: Int): Boolean =
            column + width <= GRID_COLUMNS && row + height <= rows &&
                (row until row + height).all { y -> (column until column + width).all { x -> !occupied[y * GRID_COLUMNS + x] } }
        fun reserve(column: Int, row: Int, width: Int, height: Int) {
            repeat(height) { y -> repeat(width) { x -> occupied[(row + y) * GRID_COLUMNS + column + x] = true } }
        }
    }
    val pages = mutableListOf(Page())
    fun page(index: Int): Page { while (pages.size <= index) pages.add(Page()); return pages[index] }

    if (hasClock) {
        val saved = widgets.firstOrNull { it.id == ID_BUILTIN_CLOCK }
        val target = (saved ?: HomeWidgetPlacement(ID_BUILTIN_CLOCK, page = 0, column = 0, row = 0, columns = 2, rows = 2))
            .sanitized().let { it.copy(rows = minOf(it.rows, rows)) }
        var index = target.page
        while (true) {
            val p = page(index)
            val preferred = target.row * GRID_COLUMNS + target.column
            val cells = listOf(preferred) + p.occupied.indices.filter { it != preferred }
            val slot = cells.firstOrNull { p.fits(it % GRID_COLUMNS, it / GRID_COLUMNS, target.columns, target.rows) }
            if (slot != null) {
                val column = slot % GRID_COLUMNS; val row = slot / GRID_COLUMNS
                p.reserve(column, row, target.columns, target.rows)
                p.clockPlacement = target.copy(page = index, column = column, row = row)
                break
            }
            index++
        }
    }

    if (hasCalendar) {
        val saved = widgets.firstOrNull { it.id == ID_BUILTIN_CALENDAR }
        val defaultCol = if (hasClock && page(0).clockPlacement?.let { it.page == 0 && it.row == 0 && it.column == 0 } != false) 2 else 0
        val target = (saved ?: HomeWidgetPlacement(ID_BUILTIN_CALENDAR, page = 0, column = defaultCol, row = 0, columns = 2, rows = 2))
            .sanitized().let { it.copy(rows = minOf(it.rows, rows)) }
        var index = target.page
        while (true) {
            val p = page(index)
            val preferred = target.row * GRID_COLUMNS + target.column
            val cells = listOf(preferred) + p.occupied.indices.filter { it != preferred }
            val slot = cells.firstOrNull { p.fits(it % GRID_COLUMNS, it / GRID_COLUMNS, target.columns, target.rows) }
            if (slot != null) {
                val column = slot % GRID_COLUMNS; val row = slot / GRID_COLUMNS
                p.reserve(column, row, target.columns, target.rows)
                p.calendarPlacement = target.copy(page = index, column = column, row = row)
                break
            }
            index++
        }
    }

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
    return pages.map {
        HomeGridPage(
            apps = it.apps.toList(),
            widgets = it.widgets.toList(),
            builtins = it.clockPlacement != null || it.calendarPlacement != null,
            builtinClock = it.clockPlacement != null,
            builtinCalendar = it.calendarPlacement != null,
            clockPlacement = it.clockPlacement,
            calendarPlacement = it.calendarPlacement,
        )
    }
}
