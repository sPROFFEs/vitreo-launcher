# Datos y permisos

Vitreo 0.1.0 es local: el manifiesto propio no solicita permisos. No incluye
INTERNET, QUERY_ALL_PACKAGES, accesibilidad, listener de notificaciones, overlay,
root, Shizuku, telemetría ni SDK de analítica. Esto describe el código entregado,
no equivale a una auditoría de seguridad del binario o de sus dependencias.

El manifiesto consulta actividades MAIN/LAUNCHER para mostrar apps del usuario
actual. AndroidX puede añadir el permiso de firma interno
`${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no es un permiso
sensible que el usuario tenga que conceder. El workflow revisa los permisos
solicitados de la APK final y rechaza otros inesperados.

Almacenamiento privado de la app: distribución JSON, copia de la imagen elegida y
registro acotado del último cierre inesperado. Los backups automáticos de Android
se deshabilitan en el manifiesto. No se envían datos desde Vitreo.

El selector del sistema concede acceso solo al documento elegido para
importar/exportar o a la imagen elegida. Vitreo no pide leer toda la galería ni
captura la pantalla. Los documentos exportados salen del almacenamiento privado
porque el usuario elige su destino y dejan de estar protegidos por el sandbox
de Vitreo. Un backup revela identificadores y orden de apps: trátalo como dato personal.

El informe de diagnóstico exportado no incluye inventario ni fondo. Sí incluye
modelo/fabricante, API, app ID y errores locales; revisa su contenido antes de
compartirlo. Reinstalar/borrar datos elimina datos locales; los documentos que
exportaste a otra ubicación no se eliminan automáticamente.

La construcción y publicación son otra cosa: GitHub Actions, Gradle y los
repositorios de dependencias necesitan red. Los secretos de firma se usan solo
en el proceso de build autorizado, no se incrustan como secretos en la APK.
