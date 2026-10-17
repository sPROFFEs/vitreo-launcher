# Procedencia de las partes adaptadas de Folio

Repositorio: https://github.com/McCal-Codes/folio
Archivo consultado: https://raw.githubusercontent.com/McCal-Codes/folio/9a6a54d7a90e57caa8d5ff845a9c652af348890f/app/src/main/java/com/mccal/folio/LayoutModel.kt
Licencia consultada: https://raw.githubusercontent.com/McCal-Codes/folio/9a6a54d7a90e57caa8d5ff845a9c652af348890f/LICENSE
Fecha de consulta y revisión: 2026-10-14.
Commit upstream contrastado mediante GitHub: `9a6a54d7a90e57caa8d5ff845a9c652af348890f`.
Blob Git de LayoutModel.kt: `230999933c14ae5b6d5939ec643657e32ae4a4f6`.
Blob Git de LICENSE: `fba5bb186e6e71aa1f594958f63fed77b5184f0e`.
La licencia incluida coincide con ese blob. En esta revisión se contrastaron
la licencia y las declaraciones geométricas del archivo en la referencia inmutable.

## Qué se reutiliza

`app/src/main/java/dev/vitreo/launcher/core/FolioGeometry.kt` adapta una parte reducida
 del modelo geométrico de Folio: `LayoutPreset`, saneamiento de medidas,
`DEFAULT_COLUMN_GAP = 16f`, `MIN_LABELED_COLUMN = 64f`, cálculo de posiciones y
altura mediante `HomeCellLayout`, y el límite de icono del 80% del ancho de columna
utilizado por `homeGeometry` (ajustado al 90% sobre el ancho útil real en Vitreo 0.4.1).

Se han eliminado las reglas del rail de estado, zonas de widgets y dispositivos
plegables. Se han añadido límites finitos, cuatro columnas fijas, filas acotadas y
pruebas independientes. Las funciones no son intercambiables con la API original.

La licencia upstream conservada atribuye el copyright a **Duo Launcher contributors**,
aunque el repositorio se llama Folio. No se ha sustituido esa atribución.

## Alcance

No es un clon completo de Folio. La persistencia, los widgets, las pantallas y el
shader se integran en la arquitectura de Vitreo. El build no descarga Folio y no
se atribuyen a Vitreo resultados de rendimiento de ese launcher. Se conservan las
referencias inmutables y licencias de las partes adaptadas.

## Revisión de movimiento y menús — 2026-10-16

Checkout consultado: `be9f0a908ab620fbc5ddc391f434eb3e33ed2215`.
Archivos: `FolioTokens.kt` (roles y muelles de movimiento), `AppContextMenu.kt`
(anclaje al icono, elección de espacio superior/inferior y consulta de accesos
rápidos con LauncherApps), `MainActivity.kt` (sourceBounds y contrato del launcher),
`FolioSheet.kt` (retención del contenido y recuperación al salir al fondo) y
`FolioGlass.kt` (jerarquía de materiales y contraste).

`SurfaceMotion.kt` adapta los roles Panel/Menu/Close. `AppContextMenu.kt` adapta
la colocación del menú y la consulta de shortcuts. Conservan el aviso MIT de
Duo Launcher contributors incluido en `third_party/folio/LICENSE` y en la APK.
El menú, navegación, renderizado y pruebas se han integrado en la arquitectura
de Vitreo; no se han añadido servicios, Dynamic Island ni código de shaders
externos. No se ha compilado Folio ni se atribuyen a Vitreo sus resultados.

Se revisó también `egip31/ios-launcher`, commit
`260b48de26ca615406f576c8544c0e00f9c64eff`, licencia MIT (Egi Pratama).
Su menú central y su biblioteca de lista son más básicos para este caso. No
se copió código ni recursos de ese repositorio.
