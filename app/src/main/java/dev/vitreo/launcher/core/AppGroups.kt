package dev.vitreo.launcher.core

/** Android's declared category takes precedence; older apps use a small deterministic name fallback. */
fun appGroup(label: String, packageName: String, category: Int): String = when (category) {
    0 -> "Juegos"
    1, 2 -> "Entretenimiento"
    3 -> "Creatividad"
    4 -> "Social"
    5 -> "Información"
    6 -> "Viajes"
    7 -> "Productividad"
    else -> {
        val key = searchKey("$label $packageName")
        when {
            listOf("camera", "camara", "gallery", "galeria", "photo", "foto").any(key::contains) -> "Creatividad"
            listOf("message", "messaging", "whatsapp", "telegram", "contact", "dialer", "phone", "telefono").any(key::contains) -> "Social"
            listOf("calendar", "calendario", "notes", "notas", "mail", "drive", "docs").any(key::contains) -> "Productividad"
            listOf("music", "musica", "video", "youtube", "spotify", "player").any(key::contains) -> "Entretenimiento"
            listOf("maps", "mapas", "travel", "viajes").any(key::contains) -> "Viajes"
            else -> "Utilidades"
        }
    }
}
