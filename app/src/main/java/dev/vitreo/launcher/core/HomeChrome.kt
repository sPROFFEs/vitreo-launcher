package dev.vitreo.launcher.core

/** Both adjacent pages can expand without changing Home's measured grid. */
fun homeChromeVisibility(pagePosition: Float, homePages: Int): Float {
    if (!pagePosition.isFinite() || homePages < 1) return 0f
    return minOf(pagePosition, homePages + 1f - pagePosition, 1f).coerceIn(0f, 1f)
}

val DEFAULT_TODAY_SECTIONS = listOf("clock_calendar", "suggestions", "widgets")
val DEFAULT_CONTROL_SHORTCUTS = listOf("flashlight", "timer", "calculator", "camera", "battery", "cast", "widgets", "settings")
val CONTROL_SHORTCUTS = DEFAULT_CONTROL_SHORTCUTS + listOf("nfc", "accessibility", "alarms", "display")

fun moveTodaySection(sections: List<String>, id: String, target: Int): List<String> {
    val source = sections.indexOf(id)
    if (source < 0 || target !in sections.indices || source == target) return sections
    return sections.toMutableList().apply { add(target, removeAt(source)) }
}
