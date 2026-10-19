# Arquitectura de Vitreo 0.5.x

## Flujo de datos

`LauncherApps + MAIN/LAUNCHER → AppCatalog (IO) → LauncherViewModel → StateFlow → Compose`.
Se consulta el usuario Android actual y se respeta el estado efectivo de los
componentes; no se vuelve a filtrar por `ActivityInfo.enabled`, que conserva el
valor predeterminado del manifiesto. Los cambios de paquetes encolan la invalidación de iconos sin
bloquear el hilo de UI; la consulta IO drena esa cola antes de reutilizar la caché. Si un componente desaparece y otro del mismo paquete apunta a la misma
actividad, se remapea su acceso conservando el dock, carpeta o posición de inicio.
El núcleo `LayoutOps` mantiene una lista ordenada de entradas, carpetas y cuatro
plazas de dock. `LauncherState.sanitized()` limita tamaños, elimina duplicados y
sanea la apariencia. Una actividad temporalmente ausente conserva su posición. Una desinstalación
confirmada elimina todas las actividades de ese paquete de Home, dock, carpetas,
knownApps y sugerencias. Las carpetas de una sola app se convierten en accesos y las
vacías desaparecen. LauncherApps distingue la desinstalación de una actualización;
los broadcasts PACKAGE_REMOVED con EXTRA_REPLACING no borran la distribución.
Al refrescar también se contrastan los paquetes ausentes con LauncherApps y
PackageManager: solo se limpian cuando ambas APIs confirman su ausencia, sin
borrar apps deshabilitadas, archivadas, en actualización o con errores de consulta.
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
vinculados de Home y Today. Las colocaciones de Home (página, celda y tamaño)
se guardan junto a los IDs, sin introducir IDs locales en LauncherState. Solo ofrece proveedores visibles para la pantalla de inicio.
Una solicitud de alta conserva un ID pendiente; no admite otra hasta completarla
o cancelarla. Cada resultado de configuración se contrasta con ese ID, por lo
que un resultado tardío no completa una solicitud posterior. Cerrar la galería
cancela y libera el pendiente. Los IDs no se exportan en las copias portables.

`HomeGrid` reserva primero los rectángulos de widgets y el espacio del reloj/calendario
integrado; después distribuye apps en orden por las celdas libres. Si una página no
tiene espacio, continúa en la siguiente. Cambiar orientación, tamaño de icono o
escala de texto vuelve a calcular el espacio sin eliminar entradas. La preferencia
de tamaño se conserva aunque una ventana pequeña necesite menos filas. Los IDs
anteriores sin colocación de Home se migran a Today. Mover o redimensionar no libera
ni vuelve a vincular el ID. Solo Remove Widget libera el recurso del host.

El reloj y calendario integrados usan la hora y fecha locales. El ticker funciona
solo mientras la actividad está visible, cada segundo o cada minuto en modo
reducido/ahorro. Las sugerencias conservan localmente los últimos lanzamientos
desde Vitreo y no consultan el historial global de otras apps.

El host de cada widget tiene recorte nativo por outline y por sus límites; no
depende solo del clip de Compose para encerrar RemoteViews. Respeta la altura
mínima y escala proporcionalmente si su tamaño mínimo supera el rectángulo disponible. Los cambios de opciones de
tamaño solo se envían cuando cambian sus dimensiones, no en cada tick del reloj.
El host conserva el padding predeterminado de Android y lo incluye al calcular
el mínimo disponible; no fuerza las RemoteViews contra el borde redondeado.
En Home se respetan los mínimos redimensionables del proveedor y se usa la API de
tamaños exactos de Android 12 para que RemoteViews pueda elegir su diseño adaptable.
La galería renderiza el diseño a su proporción original y ajusta el bitmap dentro
de la tarjeta, con margen exterior e interior. Prioriza las previsualizaciones
generadas de Android 15 y las imágenes suministradas sobre layouts que pueden
depender de un host vivo; no asigna un ID para la previsualización.

El movimiento y sus referencias están descritos en [MOTION.md](MOTION.md).

## Today y Control Center

`Appearance` conserva `todaySections`, `hiddenTodaySections`, `controlCenter`,
`controlShortcuts` y `homeBrightness`. `sanitized` limita las claves a tarjetas y
acciones conocidas; no admite URIs ejecutables. Las copias anteriores reciben los
valores predeterminados. El orden de los widgets nativos se guarda en la lista de
IDs privada de `WidgetController`, sin cambiar las colocaciones de Home.

Today usa una LazyColumn con claves estables para secciones y widgets; no anida
RemoteViews en una columna gigante. El dock está siempre compuesto sobre el pager,
con progreso continuo en la capa gráfica. Cada Home reserva su altura antes de
medir la cuadrícula, incluso cuando el dock sale de pantalla.

`ControlCenterController` concentra AudioManager, CameraManager y lecturas de
Settings. Los callbacks/observadores se registran mientras el panel está visible y
la actividad iniciada y se retiran al cerrar/detener. La cámara se descubre en IO;
la linterna no abre una sesión de captura. TorchCallback confirma el estado y el
volumen se vuelve a leer después de escribirlo. Los servicios pueden rechazar una
operación: la UI conserva el estado real y muestra un mensaje. El brillo se prueba
en la ventana durante el arrastre y se guarda al soltar, sin escribir Settings.System.
No se añaden permisos de cámara, red, notificaciones o modificación global de ajustes.

## Material de cristal

`HomeBackground` compone una sola imagen para el fallback local y Android Home.
`SystemHomeWallpaper` aplica solo FLAG_SYSTEM, sin backup del sistema, cuando la
opción de sincronizar está activa y Vitreo tiene el rol HOME. El hash de píxeles
y el ID de wallpaper evitan escrituras repetidas. Se usan las dimensiones de la
ventana completa para que mostrar el teclado no cambie el wallpaper. La ventana
pide SHOW_WALLPAPER; al completar la sincronización, el fallback se desvanece y
Android mantiene una superficie común durante Recientes y Home.

El preset Cyberpunk usa materiales HUD nativos y omite `GlassRenderer`. Sus tokens,
formas y tipografía se comparten mediante `VitreoTheme` y `LocalCyber`; iOS mantiene
su ruta de cristal. La selección del tema es independiente del layout y de la fuente
de iconos. [CYBERPUNK.md](CYBERPUNK.md) describe formatos, límites y referencias.

`IconPacks` descubre paquetes mediante intents ADW/Nova y lee únicamente sus recursos.
Los índices se parsean y las imágenes se rasterizan en IO. No se ejecuta código del
pack. El caché de bitmaps tiene límite de 8 MiB y se invalida por versión/configuración;
no se reinicia en un regreso normal desde una app. Los overrides se guardan por
actividad, migran con los alias y se eliminan solo al confirmar la desinstalación.
Si falta un pack o una imagen se conserva el catálogo y se usa el icono predeterminado.

`WallpaperStore` genera fondos propios o copia una foto con lado largo máximo de
1600 px y precalcula una textura esmerilada. Carga e importación están serializadas.
`GlassPanel` llama a `GlassRenderer` en el Canvas de Compose. Android 13+ usa AGSL
para refracción, tinte y reflejo. API 31/32, ahorro, modo reducido, canvas sin
hardware o error de shader usan la textura precalculada. El modo opaco evita la
textura. Los inputs de AGSL usan filtrado bilineal explícito. El centro del
material regular mantiene escarcha y tinte; el borde curvo deja ver la lente.
El shader no captura otras apps ni desenfoca sus contenidos. La prueba nativa
compara imágenes con refracción desactivada y activada, exige cambios visibles en
los bordes y estabilidad del centro y exporta las cuatro imágenes de comparación.

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

## Optional Android integration

`SystemBridge` holds ephemeral notification/media StateFlows. The sole new service,
`VitreoNotificationListener`, is non-exported and protected by Android's notification
binding permission. Android binds it after explicit user authorization. Feature
switches independently gate notification collection and MediaSession callbacks;
disable/revocation clears the corresponding memory. No content enters StateStore,
portable backups or diagnostics. Network state is a normal permission used by the
optional Home-only status bar, with no cellular-strength/SSID/location access.

`homePanelAt` defines upper-left/right gesture zones. Opening progress is a separate
State consumed by SurfaceHost; blur/scrim progress is read in drawing layers so
finger movement does not recompose the Home grid every frame. The retained modal
uses one continuous progress for entry, interruption, cancellation and dismissal.
Android still owns its physical edge and all SystemUI outside Vitreo's window.
