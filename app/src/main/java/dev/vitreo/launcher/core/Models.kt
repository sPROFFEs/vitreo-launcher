package dev.vitreo.launcher.core

import java.text.Normalizer
import java.util.Locale

const val STATE_SCHEMA = 1
const val MAX_ITEMS = 2000
const val MAX_FOLDER_APPS = 100
const val MAX_FOLDER_ID_LENGTH = 128

private val APP_ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z_.$][A-Za-z0-9_.$]*")
fun isFolderId(value: String): Boolean = value.startsWith("folder:") &&
    value.length in 8..MAX_FOLDER_ID_LENGTH

fun isAppId(value: String): Boolean = value.length in 3..512 &&
    APP_ID_PATTERN.matches(value)

sealed interface HomeEntry {
    val id: String
    data class App(override val id: String) : HomeEntry
    data class Folder(override val id: String, val title: String, val apps: List<String>) : HomeEntry
}

data class Appearance(
    val iconSize: Float = 60f,
    val showLabels: Boolean = true,
    val refraction: Float = .65f,
    val frost: Float = .38f,
    val reducedEffects: Boolean = false,
    val opaquePanels: Boolean = false,
    val palette: Int = 0,
    val usePhoto: Boolean = false,
    val iconStyle: Int = 0,
    val tintColor: Int = 0xff8fbfff.toInt(),
    val interfaceStyle: Int = 0,
    val showWidgets: Boolean = true,
    val iosIcons: Boolean = true,
) {
    fun sanitized() = copy(
        iconSize = if (iconSize.isFinite()) iconSize.coerceIn(40f, 68f) else 60f,
        refraction = if (refraction.isFinite()) refraction.coerceIn(0f, 1f) else .65f,
        frost = if (frost.isFinite()) frost.coerceIn(0f, 1f) else .38f,
        palette = palette.coerceIn(0, 3),
        iconStyle = iconStyle.coerceIn(0, 3),
        interfaceStyle = interfaceStyle.coerceIn(0, 2),
        tintColor = tintColor or 0xff000000.toInt(),
    )
}

data class LauncherState(
    val schema: Int = STATE_SCHEMA,
    val initialized: Boolean = false,
    val home: List<HomeEntry> = emptyList(),
    val dock: List<String> = emptyList(),
    val knownApps: Set<String> = emptySet(),
    val appearance: Appearance = Appearance(),
) {
    /** Dock wins duplicate conflicts; unavailable apps are deliberately retained. */
    fun sanitized(): LauncherState {
        val used = mutableSetOf<String>()
        val folderIds = mutableSetOf<String>()
        val cleanDock = dock.asSequence().filter(::isAppId).distinct().take(DOCK_SLOTS).toList()
        used.addAll(cleanDock)
        val cleanHome = home.take(MAX_ITEMS).mapNotNull { entry ->
            when (entry) {
                is HomeEntry.App -> entry.takeIf { isAppId(it.id) && used.size < MAX_ITEMS && used.add(it.id) }
                is HomeEntry.Folder -> {
                    if (!isFolderId(entry.id) || !folderIds.add(entry.id)) null
                    else {
                        val apps = entry.apps.asSequence().filter(::isAppId)
                            .filter { it !in used }.distinct().take(minOf(MAX_FOLDER_APPS, (MAX_ITEMS - used.size).coerceAtLeast(0))).toList()
                        used.addAll(apps)
                        when (apps.size) {
                            0 -> null
                            1 -> HomeEntry.App(apps.first())
                            else -> entry.copy(title = entry.title.trim().take(48).ifBlank { "Carpeta" }, apps = apps)
                        }
                    }
                }
            }
        }
        return copy(schema = STATE_SCHEMA, home = cleanHome, dock = cleanDock,
            knownApps = (used + knownApps.filter(::isAppId)).take(MAX_ITEMS * 4).toSet(),
            appearance = appearance.sanitized())
    }
    fun placedApps(): List<String> = dock + home.flatMap {
        when (it) { is HomeEntry.App -> listOf(it.id); is HomeEntry.Folder -> it.apps }
    }
}

fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
