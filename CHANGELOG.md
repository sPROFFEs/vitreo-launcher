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
