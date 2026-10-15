# Cambios

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
