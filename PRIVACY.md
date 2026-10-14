# Privacidad — Vitreo 0.2.0

Vitreo no tiene conexión propia a Internet, cuentas, anuncios ni telemetría.
La búsqueda y la clasificación de aplicaciones son locales y se limitan al usuario Android actual.
La consulta de apps se restringe a actividades de inicio y proveedores de widgets.

La distribución, las preferencias, una copia reducida del fondo elegido y las últimas
16 apps abiertas desde Vitreo se guardan en el almacenamiento privado de la app.
Las sugerencias utilizan ese historial local; no se solicita acceso al historial de uso de Android.

## Widgets

Se declara `android.permission.BIND_APPWIDGET` siguiendo el contrato de un host Android.
En una app normal no se concede como permiso de sistema: si no hay autorización previa,
Vitreo abre `ACTION_APPWIDGET_BIND` y Android pide autorizar el widget. Si lo rechazas,
se libera el ID pendiente. Los widgets añadidos se configuran con su propio proveedor.
Sus actualizaciones se escuchan solo mientras el launcher está visible.

Cada widget pertenece a su app proveedora, que conserva sus propias políticas de datos,
red y permisos. Los IDs de widgets se guardan aparte de las copias exportables; los widgets
se añaden de nuevo en otro dispositivo.

## Imágenes, copias y diagnóstico

Las fotos se eligen mediante el selector del sistema y se copian reducidas al almacenamiento
privado de Vitreo. No se lee toda la galería. La copia JSON conserva la distribución y la
apariencia, pero no la foto, los IDs de widgets ni el historial de sugerencias.

El diagnóstico se exporta únicamente cuando lo solicitas. Incluye versión, dispositivo,
estado gráfico y el último stack de cierre inesperado; revísalo antes de compartirlo.
No contiene un inventario de tus apps, fotografías o contraseñas.

El cristal procesa exclusivamente el fondo propio del launcher. No captura otras apps ni
sustituye las funciones de seguridad, notificaciones, bloqueo o Recientes de Android.
