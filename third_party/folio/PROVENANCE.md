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
utilizado por `homeGeometry`.

Se han eliminado las reglas del rail de estado, zonas de widgets y dispositivos
plegables. Se han añadido límites finitos, cuatro columnas fijas, filas acotadas y
pruebas independientes. Las funciones no son intercambiables con la API original.

La licencia upstream conservada atribuye el copyright a **Duo Launcher contributors**,
aunque el repositorio se llama Folio. No se ha sustituido esa atribución.

## Lo que no contiene este ZIP

No contiene un clon ni un fork completo de Folio: no reutiliza su Activity, su
repositorio de estado, sus carpetas, widgets, pantallas, centro de control,
Dynamic Island ni pruebas. Esos componentes de Vitreo se han implementado
por separado o están expresamente fuera del alcance de 0.1.0.

No se pudo descargar y compilar un checkout completo de Folio en el entorno de
preparación. La revisión inicial consultó `main`; esta revisión añade la
referencia inmutable comprobada arriba. No se presenta como un checkout completo
compilado. Las partes adaptadas están incluidas y el build no consulta ese repositorio.

La interfaz, persistencia, shader, herramientas y workflows restantes de Vitreo
son implementaciones nuevas. No se ha copiado código ni recursos de Liquid Touch,
ni se ha integrado Backdrop/AndroidLiquidGlass. La semejanza visual buscada es
una referencia de diseño, no una promesa de equivalencia con iOS.
