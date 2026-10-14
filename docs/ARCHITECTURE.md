# Arquitectura de Vitreo 0.2.x

## Flujo de datos

`PackageManager → AppCatalog (IO) → LauncherViewModel → StateFlow → Compose`.
El núcleo `LayoutOps` mantiene una lista ordenada de entradas, carpetas y cuatro
plazas de dock. `LauncherState.sanitized()` limita tamaños, elimina duplicados y
sanea la apariencia. Una app ausente conserva su acceso hasta que se quite.
Antes de lanzar se comprueba su componente contra el catálogo actual; una copia
no puede introducir intent URIs ejecutables.

`StateStore` lee y escribe con `AtomicFile` fuera del hilo de UI. Un canal de
estados y un escritor serial conservan el último cambio pendiente; al liberar el
ViewModel se cierra el canal y se drenan los guardados. Un cierre abrupto aún
puede perder un cambio pendiente. Un JSON ilegible se conserva para recuperación
cuando es posible. Las copias usan esquema 1 y un límite de 1 MiB.

## Pantallas e interacción

El pager contiene Hoy, una o más páginas de Inicio y la Biblioteca. Sus claves
conservan la identidad de las páginas cuando cambia el número de entradas.
La cuadrícula excluye barras del sistema y recortes de pantalla; el teclado de
Spotlight no cambia sus filas. La barra de edición ajusta el espacio entre filas
sin alterar su capacidad. Salir de Inicio termina la edición.

Un gesto vertical abre Spotlight o el Centro de control. El arrastre de edición
se mantiene en el padre durante el cambio de página y puede reordenar, agrupar
o intercambiar con el dock. Mientras haya un panel abierto se deshabilitan estos
gestos, se interceptan sus huecos y se oculta la semántica del inicio de fondo.
Los paneles respetan Reducir movimiento y la salida de búsqueda cierra el teclado.

## Widgets y datos locales

`WidgetController` usa un `AppWidgetHost` y preferencias privadas para los IDs
vinculados de Hoy. Solo ofrece proveedores visibles para la pantalla de inicio.
Una solicitud de alta conserva un ID pendiente; no admite otra hasta completarla
o cancelarla. Cada resultado de configuración se contrasta con ese ID, por lo
que un resultado tardío no completa una solicitud posterior. Cerrar la galería
cancela y libera el pendiente. Los IDs no se exportan en las copias portables.

El reloj y calendario integrados usan la hora y fecha locales. El ticker funciona
solo mientras la actividad está visible, cada segundo o cada minuto en modo
reducido/ahorro. Las sugerencias conservan localmente los últimos lanzamientos
desde Vitreo y no consultan el historial global de otras apps.

## Material de cristal

`WallpaperStore` genera fondos propios o copia una foto con lado largo máximo de
1600 px y precalcula una textura esmerilada. Carga e importación están serializadas.
`GlassPanel` coloca un `GlassView` pasivo detrás de Compose. Android 13+ usa AGSL
para refracción, tinte y reflejo. API 31/32, ahorro, modo reducido, canvas sin
hardware o error de shader usan la textura precalculada. El modo opaco evita la
textura. El shader no captura otras apps ni desenfoca sus contenidos.

## Publicación y límites

Se publican APK de release con firma privada, versión creciente y assets
inmutables. Un borrador solo se publica tras verificar todos sus uploads. Las
versiones numéricas son releases normales; Preview queda en artifacts de CI.
La publicación requiere las pruebas de API 31/35 en el mismo commit, JVM, lint y
comprobaciones de identidad, firma, permisos y hashes.

Vitreo es una Activity HOME. Android conserva SystemUI, Recientes, teclado y
bloqueo seguro. No hay root, servicio de accesibilidad ni soporte completo de
perfiles de trabajo/espacio privado. Las pruebas de emulador no sustituyen pruebas
físicas en un Pixel con GrapheneOS.
