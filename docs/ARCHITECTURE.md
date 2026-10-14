# Arquitectura de Vitreo 0.1.0

## Flujo de datos

`PackageManager -> AppCatalog (IO) -> LauncherViewModel -> StateFlow -> Compose`.
Las operaciones de escritorio se realizan mediante funciones Kotlin puras de
`core/LayoutOps.kt`; `LauncherState.sanitized()` limita tamaños, elimina duplicados,
conserva identidades explícitas de componentes y sanea valores gráficos.

Las referencias se limitan a actividades lanzables del usuario Android actual.
Una actividad no disponible conserva su hueco; el lanzador comprueba que siga
en el catálogo antes de lanzar el `ComponentName`. No ejecuta intent URIs de un
backup. Una app borrada del escritorio manualmente no reaparece por cada refresco.

La carga y los guardados se realizan fuera del hilo de UI. Un canal que conserva el estado pendiente más reciente
serializa escrituras mediante `AtomicFile`. El escritor usa un scope separado:
al liberar el ViewModel, se cierra el canal y se intenta terminar lo pendiente
en vez de cancelarlo inmediatamente. Un cierre abrupto podría perder el
último cambio aún no persistido, pero no se presenta un archivo a medio escribir
como estado válido. Un JSON ilegible provoca una copia local de recuperación
cuando es posible y un aviso. Los backups se limitan a 1 MiB y esquema 1.

## Material de cristal

`WallpaperStore` genera un fondo propio o copia una imagen elegida por el usuario,
reducida a un lado largo máximo de 1600 px. Precalcula una versión esmerilada de
menor resolución. La imagen es solo de Vitreo: no cambia el fondo de bloqueo. Carga e importación
están sincronizadas porque AtomicFile no sustituye un bloqueo entre escritores.

`GlassPanel` coloca `GlassView` detrás del contenido Compose. En Android 13+,
`RuntimeShader` ejecuta `assets/glass.agsl`: distancia a rectángulo redondeado,
normal aproximada del borde, desplazamiento de muestreo, ligera dispersión,
mezcla esmerilada, tinte y reflejo. Las coordenadas se alinean con el fondo,
incluso al cambiar la posición de un panel.

No hay captura de pantalla, desenfoque de otras apps ni refracción del contenido
que esté debajo del panel. No se ejecuta una animación continua en reposo. El coste
real debe medirse en el Pixel; el uso de GPU por sí solo no garantiza eficiencia.

API 31/32, ahorro de batería, modo reducido, renderizado sin hardware o error de
shader usan textura esmerilada. El modo opaco evita la textura. El último error de
shader se incluye en diagnóstico, y la prueba de instrumentación API 33+ compila
el shader directamente para no ocultar un error tras el fallback.

## Límites del sistema

Una Activity HOME no sustituye SystemUI, Recientes ni el bloqueo seguro. No se
intenta integrar con Quickstep mediante privilegios, root o accesibilidad. No se
implementa el host de AppWidgets en esta versión ni soporte de otros perfiles.

## Próximas prioridades técnicas

1. Obtener un build Android real verde y corregir incompatibilidades de API/build.
2. Probar iniciar apps, volver a Home, importar/exportar y reiniciar en un Pixel.
3. Medir frames y memoria con el material activo/reducido antes de más efectos.
4. Mejorar arrastre entre páginas/dock, accesibilidad y gestos.
5. Añadir icon packs o AppWidgets solo después de estabilizar persistencia y UI.

No se presenta ninguna de estas tareas como ya terminada o validada.

## Invariantes añadidas en la revisión

No se crea una carpeta con ID inválido ni demasiado largo. Al llegar a 2.000 apps
colocadas, añadir otra se rechaza antes de modificar el estado; mover una ya
colocada sigue permitido. No se ofrece una carpeta llena como destino en la UI.
Los iconos sin texto tienen un nombre accesible y los switches enlazan su etiqueta
a la acción. Estas mejoras de UI siguen pendientes de verificación en dispositivo.
