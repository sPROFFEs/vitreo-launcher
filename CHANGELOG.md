# Cambios

## 0.5.3 — Cyberpunk Immersive y Control Center por páginas — 2026-10-18

- Cyberpunk conserva Classic y añade Immersive, optativo: lluvia, iluminación ambiental, escáner y bordes HUD en movimiento. Un reloj compartido leído en dibujo; se detiene al perder foco, abrir un panel o salir de Home. Respeta Reducir movimiento, ahorro y HUD Effects.
- Dos fondos originales incluidos, Neon Rider y Digital Dusk, seleccionables sin descargas; conservar el tema no sustituye automáticamente una foto. Android y el launcher comparten el mismo póster estático al volver de Recientes.
- Control Center con módulos compactos, círculos sin etiquetas externas, páginas verticales de Favoritos, multimedia y conectividad e indicadores laterales. El botón de energía abre las opciones de batería de Android.
- Edición sobre los accesos: quitar, ampliar/contraer, mantener y arrastrar para ordenar; selector con búsqueda y flechas accesibles. Preferencias compatibles con copias previas y Classic como valor de migración.
- Regresiones de empaquetado de todos los tamaños, copias y migración, experiencia Immersive y movimiento reducido, páginas del panel, tamaños y búsqueda de controles. Sin permisos, servicios ni dependencias nuevos.

## 0.5.2 — Dock continuo, Today editable y Control Center — 2026-10-17

- Dock e indicador inferior siguen el desplazamiento entre Home, Today y App Library con deslizamiento y opacidad continuos. Home reserva su espacio sin recolocar las apps al aparecer o desaparecer.
- Today permite reordenar Clock & Calendar, Suggestions y el grupo de widgets con asas o flechas, quitar y recuperar las tarjetas integradas y trasladar Clock & Calendar a Home. Los widgets Android tienen orden propio persistente y conservan su vinculación.
- Control Center opcional, accesible con un gesto corto desde la zona superior derecha en las tres vistas o con el botón de Today. Cierre hacia arriba desde su borde inferior y activación en Settings → Control Center.
- Nueva composición de conectividad, reproducción, Focus, aspecto y deslizadores verticales. Volumen multimedia real, brillo limitado a la ventana de Vitreo, linterna cuando el hardware está disponible y accesos a temporizador, calculadora, cámara y paneles Android.
- Editor para añadir, quitar, ordenar y restablecer accesos del panel; preferencias incluidas en copias compatibles con versiones anteriores. No se añaden permisos; conectividad, Focus y rotación abren las pantallas oficiales del sistema.
- Regresiones de progreso intermedio del dock, persistencia de Today, acceso/cierre/desactivación del panel, volumen Android, brillo de ventana y orden de widgets.

## 0.5.1 — Cyberpunk y regreso integrado a Home — 2026-10-17

- Regreso desde Recientes con iconos que entran radialmente desde fuera de la pantalla, sin cambiar sus celdas ni animar el fondo con el escritorio; widgets con entrada vertical suave.
- Fondo compartido con Android Home cuando Vitreo es predeterminado, incluyendo foto, recorte, oscurecimiento y Cyberpunk. Ventana integrada con la superficie de wallpaper del sistema y fallback local con transición. Sync with Android Home se puede desactivar; se añade el permiso normal SET_WALLPAPER.

- Tema Cyberpunk opcional en Themes & Icons: HUD oscuro rojo/cian, esquinas recortadas, fondo de cuadrícula, tipografía Rajdhani y reloj/calendario digitales.
- Tratamiento compartido en Home, Dock, carpetas, biblioteca, búsqueda, menús, controles y ajustes. iOS sigue siendo el predeterminado y el layout se conserva.
- 23 iconos de familias de apps de Arcticons (CC BY-SA 4.0) y 13 controles CYBERCORE (MIT), con fuentes, hashes y licencias en el repo y APK.
- Packs ADW/Nova instalados: mapeo estático por actividad y selección individual con búsqueda desde More → Change Icon; fallback si falta el recurso o se desinstala el pack.
- Barrido de paneles y acentos glitch breves, sin bucle en reposo; interruptor HUD Effects y respeto al movimiento reducido/ahorro. Entradas con muelles críticamente amortiguados y cancelación continua.
- Copias compatibles con versiones anteriores, referencias de iconos portables, caché de recursos en IO y limpieza/migración de overrides al desinstalar/cambiar alias.
- Pruebas de parsers, límites, instalación/actualización/eliminación de un pack de fixture, selección y persistencia de iconos y recorrido visual del tema.

## 0.5.0 — Widgets en Home y movimiento — 2026-10-17

- Desinstalar elimina automáticamente los accesos de Home, dock, carpetas y sugerencias. Las actualizaciones conservan su posición; una comprobación al refrescar limpia fantasmas de desinstalaciones ocurridas con el launcher cerrado.

- Widgets Android en Home y Today, tamaños Small/Medium/Large, cambio de página, arrastre en edición y traslado entre vistas sin volver a vincularlos.
- Cuadrícula que reserva rectángulos de widgets y redistribuye las apps sin eliminarlas. Conserva tamaño/página al reiniciar y migra los widgets anteriores a Today.
- Más margen superior/interior en el catálogo; preferencia por imágenes completas del proveedor frente a layouts que dependen de un host vivo. Los widgets solo notifican cambios de tamaño cuando cambian sus dimensiones.
- Cierres interactivos con velocidad, cancelación al invertir el gesto y continuidad del muelle. Navegación de ajustes y More/Back con dirección coherente; movimiento de celdas al cambiar el tamaño del widget.
- Pulsación/edición de iconos e indicador segmentado animados en la capa de dibujo, sin recomposición o layout por fotograma. Se mantienen movimiento reducido y ahorro de batería.
- Las invalidaciones de paquetes ya no esperan en el hilo de UI al bloqueo del renderizado de iconos. El host conserva el padding Android del proveedor y lo incluye al calcular su tamaño mínimo.
- Contactos usa una agenda vectorial original con pestañas de color; se conservan los otros 22 recursos de SVG Stack y sus licencias.
- README reorganizado, referencias de movimiento y guía de pruebas físicas actualizadas. Retirados logs/manifiesto de la preparación inicial y helper de creación de repositorios obsoleto.
- Regresiones de empaquetado de cuadrícula, vinculación y persistencia, redimensionado y cambio de vista, márgenes de catálogo y capturas sincronizadas con el fotograma actual.

## 0.4.1 — Iconos, widgets y catálogo — 2026-10-17

- 23 recursos de iconos de SVG Stack, con fuentes, hashes y atribución CC BY 4.0 incluidos; reloj y calendario mantienen hora y fecha reales.
- Tamaño de iconos de inicio y dock hasta 84 puntos, con límite según el ancho real de cada columna y altura del dock adaptada.
- Widgets nativos con recorte redondeado en Android, altura mínima del proveedor y ajuste proporcional cuando no cabe su ancho. Las vistas previas se renderizan a su proporción y se muestran completas dentro de las tarjetas; calendario integrado adaptado a alturas pequeñas y fuentes grandes.
- Preparación de la regresión de alias mediante la API de componentes con identidad de shell, comprobación explícita del override y sin depender de una segunda consulta de enabled.
- Catálogo mediante LauncherApps: incluye alias activados en tiempo de ejecución, como los iconos alternativos de Telegram, sin descartarlos por el estado predeterminado del manifiesto.
- Unión con MAIN/LAUNCHER para conservar actividades cuando una consulta omite entradas o falla; el diagnóstico registra contadores y errores, sin nombres de apps.
- Al cambiar de icono se conservan la posición en inicio, dock y carpetas y las sugerencias recientes cuando el alias apunta a la misma actividad. Los cambios de paquetes invalidan su arte en caché.
- Refracción más visible en los bordes de controles y paneles, con centro difuso para los textos, reflejos proporcionales a la densidad de pantalla y filtrado bilineal explícito de las texturas AGSL.
- Vista previa de cristal sobre el propio fondo; ajustes explican cuándo el modo opaco, movimiento reducido o ahorro sustituyen el efecto. Diagnóstico local incluye configuración gráfica y número de actividades disponibles, sin nombres de apps.
- Regresión nativa de alias desactivados en el manifiesto y activados por Android, cambio de icono sin duplicar accesos y comparación de píxeles entre cristal plano y refractado en ambos materiales.

## 0.4.0 — Continuidad, biblioteca y menús — 2026-10-16

- Icono adaptativo con fondo completo, marca sin recuadro interior, recurso roundIcon circular y soporte monocromático.
- Widgets, licencias y editor de fotos recuperan la página de ajustes de origen.
- Caché de arte por componente, versión instalada y configuración: volver de una app reutiliza los bitmaps y evita recomponer y guardar el escritorio sin cambios.
- Apertura desde los límites reales del icono en inicio, dock, Hoy, búsqueda, carpetas y biblioteca, con LauncherApps y sourceBounds de pantalla.
- Al volver de una app con Atrás o HOME se conserva la carpeta, categoría o búsqueda y su consulta; un segundo HOME vuelve al inicio.
- Animación coordinada del escritorio al abrir y regresar; prevención de dobles aperturas y recuperación si Android rechaza el cambio de tarea.
- Menús contextuales junto al icono, elevación del icono original y colocación arriba/abajo según el espacio; acciones principales, More y accesos rápidos reales de la app cuando Android concede al launcher acceso a ellos.
- La categoría, carpeta o búsqueda permanece debajo del menú; cerrar y cancelar una selección de carpeta recupera su contexto.
- Cierre de paneles hacia el origen y gesto vertical desde la cabecera con cancelación; mismo progreso para superficie, desenfoque y scrim.
- Búsqueda de biblioteca en lista alfabética con índice lateral, distinta de las sugerencias de Spotlight.
- Cristal dibujado directamente en Compose sin AndroidView ni listeners por panel; normales analíticas del shader, borde curvo, doble reflejo y brillo que sigue la pulsación. Alternativa esmerilada en Android 12 y opciones de movimiento/transparencia conservadas.
- Pruebas de regresión de menús anclados, navegación, gesto de cierre, lista alfabética y apertura/retorno de una app real con animaciones activadas.

## 0.3.0 — Glass, movimiento y personalización — 2026-10-15

- Interfaz, mensajes, fechas y categorías en inglés por defecto.
- Cristal con refracción en los bordes, reflejos direccionales y respuesta a la pulsación; material más denso y texto adaptado al tema para carpetas y categorías.
- Transiciones con muelles, apertura diferenciada de paneles, desenfoque progresivo y pulsación de iconos; respeto de Reducir movimiento y ahorro de batería.
- Ajustes reorganizados con vistas previas, navegación animada, deslizadores de pista fina, controles de refracción y escarcha y oscurecimiento del fondo.
- Contraste global corregido en el tema oscuro, etiquetas y barra de estado adaptadas a fondos claros; animación del desenfoque sin recomponer todo el escritorio.
- Apertura de apps desde el icono con la transición nativa de Android; guardado del fondo preparado antes de cerrar el editor para evitar destellos del fondo anterior.
- Galería de widgets con búsqueda y previsualizaciones suministradas por las apps, sin asignar IDs para mostrarlas.
- Editor de fotos con pellizco, arrastre, Fit/Fill/Reset, guardar y cancelar; extensión desenfocada para ver la imagen completa y original conservado para ajustes posteriores.
- Geometría del fondo compartida entre editor y cristal; zoom y posición persistentes con compatibilidad de copias anteriores.


## 0.2.1 — Revisión y releases normales — 2026-10-15

- Publicación como release normal y selección de Latest por versión; promoción de las prereleases existentes sin cambiar tags ni assets.
- Los paneles modales bloquean los toques y arrastres hacia el inicio y ocultan sus controles de fondo a accesibilidad.
- Cuadrícula estable al editar y abrir el teclado; el modo edición termina al salir de las páginas de inicio.
- Cancelación y resultados tardíos de configuración de widgets controlados por ID; una solicitud activa impide dobles asignaciones.
- El selector respeta los widgets ocultos y la categoría de pantalla de inicio.
- Restablecer la distribución recupera el dock de apps preferidas; las carpetas llenas dejan de ofrecerse como destino.
- Las transiciones de los paneles respetan Reducir movimiento; se retira el teclado al salir de la búsqueda.
- Nuevas pruebas de regresión de publicación, edición, teclado, restablecimiento y ciclo de vinculación de widgets.
- Documentación actualizada para reflejar el build, la interfaz y la publicación reales.

## 0.2.0 — Inicio inspirado en iOS — 2026-10-14

- Inicio de cuatro columnas sin cabecera permanente, dock inferior de cristal e iconos con esquinas continuas.
- Cuatro estilos de iconos, aspecto claro/oscuro/automático y fondos originales.
- Hoy a la izquierda, Spotlight al deslizar hacia abajo y Biblioteca categorizada después de la última página.
- Reloj y calendario integrados; widgets Android en Hoy con autorización, configuración y persistencia local.
- Carpetas 3×3 con páginas y renombrado; agrupación por arrastre, intercambio con el dock y cambio de página por el borde.
- Ajustes agrupados y Centro de control con accesos a paneles de Android.
- Compatibilidad con las copias anteriores, mismo applicationId y continuidad de firma.
- Release firmada publicada tras pasar JVM/lint y las pruebas de emulador API 31/35.

## 0.1.0 — Primera release — 2026-10-14

- Escritorio paginado, dock, carpetas, búsqueda local, selección HOME, fondos locales y persistencia atómica.
- Backups y diagnóstico local. Shader AGSL propio con alternativa esmerilada.
- Geometría adaptada de Folio con atribución MIT conservada.
- CI y publicación firmada con verificación de APK, hashes y pruebas de emuladores obligatorias.
- Correcciones de conservación de accesos al crear carpetas y con el escritorio lleno.
- Regresión HOME corregida iniciando ActivityScenario en una tarea MAIN/HOME.

Las pruebas de emulador no acreditan pruebas físicas de GrapheneOS.
