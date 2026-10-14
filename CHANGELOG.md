# 0.2.0 — Inicio inspirado en iOS

- Inicio sin cabecera de reloj ni controles permanentes; dock inferior de cristal y máscaras continuas para iconos adaptativos.
- Cuatro estilos de iconos y tres apariencias del sistema de ajustes; fondos originales renovados.
- Hoy a la izquierda, Spotlight al deslizar hacia abajo y Biblioteca categorizada después de la última página.
- Reloj y calendario integrados; host de widgets Android con autorización, configuración, persistencia local y eliminación.
- Carpetas centradas 3×3 con páginas y renombrado; edición con movimiento, intercambio con el dock, agrupación por arrastre y cambio de página por el borde.
- Ajustes con listas agrupadas, controles iOS y un Centro de control con accesos a paneles reales de Android.
- Compatibilidad con las copias anteriores, mismo applicationId y continuidad de firma.
- Nuevas regresiones de intercambio del dock, categorías, migración de apariencia, gestos y widgets.

# Cambios

## 0.1.0 - 2026-10-14 - prototipo inicial, publicación pendiente

Versión de fuente 0.1.0 (versionCode 100). Esta entrada no acredita que se haya
compilado o publicado una APK: ese estado lo determina tu ejecución de Actions.

Escritorio de cuatro columnas y páginas, dock, carpetas, biblioteca/búsqueda local,
acciones de iconos, selección HOME, fondos propios/locales, persistencia atómica,
backups y diagnóstico local. Shader AGSL propio con alternativa esmerilada.

Adaptaciones geométricas reducidas de Folio con avisos MIT conservados; no es un
fork completo. No se incorpora Liquid Touch ni Backdrop.

Workflows de CI, publicación de releases y emulador obligatorio/reutilizable. Helper para repo nuevo
y firma privada, variante Preview separada, verificación de assets y protección
contra sobrescritura de releases. Sin credenciales ni APKs precompiladas.

Pendiente de primera compilación Android, primer render AGSL real y prueba física.
Sin widgets nativos, icon packs, perfil de trabajo/espacio privado o SystemUI.

### Revisión de 0.1.0 (misma versión, aún sin publicar)

Correcciones de pérdida de accesos al crear carpetas con ID demasiado largo y al
añadir apps con el escritorio lleno; siete escenarios de regresión del núcleo.
Serialización de I/O del fondo, drenaje de guardados y nombres accesibles en iconos.
Publicación separada del build y de la firma, pruebas de emulador obligatorias,
validación de hashes antes/después de transferir, rechazo de tags de otro commit,
y bloqueo de Preview para releases versionadas. Procedencia Folio fijada a commit.
Documentación y evidencia local actualizadas. Ninguna APK se acredita como compilada.
