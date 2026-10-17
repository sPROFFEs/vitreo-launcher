# Privacidad — Vitreo

Vitreo no tiene conexión propia a Internet, cuentas, anuncios ni telemetría.
La búsqueda y la clasificación de aplicaciones son locales y se limitan al usuario Android actual.
El catálogo combina LauncherApps y la consulta MAIN/LAUNCHER para las actividades de inicio que Android pone a disposición del usuario actual; no consulta perfiles ocultos. Los proveedores de widgets se consultan por separado.
Los menús pueden consultar accesos rápidos de las apps mediante LauncherApps cuando Android reconoce a Vitreo como launcher predeterminado. Se muestran localmente y no se incluyen en las copias exportadas.
El arte de iconos se conserva en memoria y se invalida al cambiar la versión instalada o la configuración.

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
red y permisos. Los IDs, páginas y tamaños de widgets se guardan localmente, aparte de las copias exportables; los widgets
se añaden de nuevo en otro dispositivo.

## Control Center

El panel es opcional y no añade permisos al manifiesto. Mientras está visible lee
volumen multimedia, batería, brillo del sistema y, cuando Android lo permite,
indicadores de modo avión/Wi-Fi/Bluetooth. No consulta SSID, redes cercanas,
notificaciones, metadatos de canciones ni historial de reproducción. Los listeners
de ajustes, reproducción y linterna se retiran al cerrar el panel o detener la actividad.

El volumen modifica el canal multimedia de Android. Los botones de reproducción
envían comandos al reproductor que el sistema seleccione. La linterna usa la API
pública sin abrir una sesión de cámara ni capturar imágenes. Los accesos a redes,
Focus y ajustes del dispositivo abren pantallas del sistema. El brillo de Home
modifica únicamente la ventana de Vitreo. Se guardan localmente sus preferencias,
los accesos elegidos y el orden/visibilidad de las tarjetas de Today; estos ajustes
forman parte de la copia portable. Los IDs de widgets siguen siendo locales.

## Imágenes, copias y diagnóstico

La opción **Sync with Android Home** está activada por defecto y solo se aplica
cuando Vitreo es el launcher predeterminado. Usa el permiso normal SET_WALLPAPER
para entregar a Android una copia del fondo de Home ya compuesto, con recorte,
oscurecimiento y tema. Android la conserva como wallpaper del sistema. No se lee
el fondo ajeno, no se solicita acceso a toda la galería y se desactiva el backup
del sistema para esta copia. La opción se puede apagar en Wallpaper. Se configura
únicamente FLAG_SYSTEM; el fondo de bloqueo sigue bajo gestión de Android.

Las fotos se eligen mediante el selector del sistema y se copian reducidas al almacenamiento
privado de Vitreo. No se lee toda la galería. La copia JSON conserva la distribución y la
apariencia, pero no la foto, los IDs de widgets ni el historial de sugerencias.

El diagnóstico se exporta únicamente cuando lo solicitas. Incluye versión, dispositivo,
estado y ajustes gráficos, número de actividades disponibles y el último stack de cierre inesperado; revísalo antes de compartirlo.
No contiene un inventario de tus apps, fotografías o contraseñas.

Los iconos de SVG Stack se incluyen en la APK; no se descargan al utilizar el launcher.
El tema Cyberpunk incluye arte atribuido de Arcticons y CYBERCORE, y fuentes Rajdhani,
también locales. Los packs de iconos se descubren mediante intents de temas ADW/Nova;
solo se leen sus recursos, sin ejecutar código del pack ni enviar datos a su autor.
Las referencias de pack e icono elegido se guardan en el layout y en sus copias;
no se copian imágenes de packs externos al exportar.

El cristal procesa exclusivamente el fondo propio del launcher. No captura otras apps ni
sustituye las funciones de seguridad, notificaciones, bloqueo o Recientes de Android.

Las fotos elegidas se copian al almacenamiento privado únicamente al guardar en el editor. Cancelar no sustituye el fondo. Se conserva una copia del original (limitada a 1600 píxeles en su lado mayor); el zoom y la posición se guardan por separado. Las previsualizaciones de widgets proceden de recursos de las apps instaladas y se muestran sin asignar ni vincular un ID de widget.
