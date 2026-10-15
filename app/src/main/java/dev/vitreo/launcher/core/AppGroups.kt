package dev.vitreo.launcher.core

/** Android's declared category takes precedence; older apps use a small deterministic name fallback. */
fun appGroup(label: String, packageName: String, category: Int): String = when (category) {
    0 -> "Games"
    1, 2 -> "Entertainment"
    3 -> "Creativity"
    4 -> "Social"
    5 -> "Information"
    6 -> "Travel"
    7 -> "Productivity"
    else -> {
        val key = searchKey("$label $packageName")
        when {
            listOf("camera", "camara", "gallery", "galeria", "photo", "foto").any(key::contains) -> "Creativity"
            listOf("message", "messaging", "whatsapp", "telegram", "contact", "dialer", "phone", "telefono").any(key::contains) -> "Social"
            listOf("calendar", "calendario", "notes", "notas", "mail", "drive", "docs").any(key::contains) -> "Productivity"
            listOf("music", "musica", "video", "youtube", "spotify", "player").any(key::contains) -> "Entertainment"
            listOf("maps", "mapas", "travel", "viajes").any(key::contains) -> "Travel"
            else -> "Utilities"
        }
    }
}
