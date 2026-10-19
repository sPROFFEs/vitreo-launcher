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

El panel es opcional. La barra propia de Home, Notification Center y la conexión
con el reproductor tienen interruptores independientes, apagados por defecto.
La barra se limita a la ventana del launcher: no sustituye SystemUI ni la pantalla
de bloqueo. ACCESS_NETWORK_STATE permite indicar la conexión activa; no consulta
SSID, redes cercanas, ubicación ni intensidad de señal telefónica.

Notification Center y el reproductor conectado requieren autorización explícita
en la pantalla de acceso a notificaciones de Android. El único servicio declarado
es un NotificationListenerService no exportado, protegido por
BIND_NOTIFICATION_LISTENER_SERVICE y vinculado por el sistema. No hay servicio de
accesibilidad, overlay, grabación de pantalla ni servicio en primer plano.

Solo con Notification Center activado se leen las notificaciones actuales del
usuario Android actual: aplicación, título, texto, hora y acciones. Los resúmenes
de grupo se omiten y los avisos no borrables se conservan. No se muestra contenido
mientras el dispositivo indica que está bloqueado. Android puede restringir
notificaciones sensibles y perfiles de trabajo. Solo con Connect media player
activado se consultan sesiones multimedia, metadatos, carátulas (máximo 512 píxeles)
y acciones compatibles. Los botones usan la sesión real, sin historial de escucha.

Estos datos se mantienen únicamente en memoria. **No se guardan en disco, copias,
diagnósticos ni registros**, ni se envían a ninguna red. Desactivar cada opción
borra sus datos y retira los listeners multimedia correspondientes. Revocar el
permiso en Android desconecta el servicio y borra ambos. El propio sistema puede
mantener autorizado el listener hasta que revoques el acceso; desactivar una
opción no cambia permisos del sistema automáticamente.

El panel lee volumen, batería, brillo e indicadores públicos de redes. Los accesos
a redes, Focus y ajustes abren pantallas del sistema; el brillo modifica únicamente
Vitreo. El volumen cambia el canal multimedia real y la linterna usa la API pública
sin capturar imágenes. Sin conexión multimedia autorizada, los comandos genéricos
solo se habilitan mientras Android informa de audio activo. Las preferencias de
integración viajan en el layout, pero la autorización de Android nunca se exporta.

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
sustituye las funciones de seguridad, bloqueo o Recientes de Android. El panel opcional de notificaciones presenta los avisos que Android autoriza.

Las fotos elegidas se copian al almacenamiento privado únicamente al guardar en el editor. Cancelar no sustituye el fondo. Se conserva una copia del original (limitada a 1600 píxeles en su lado mayor); el zoom y la posición se guardan por separado. Las previsualizaciones de widgets proceden de recursos de las apps instaladas y se muestran sin asignar ni vincular un ID de widget.

Neon Rider y Digital Dusk son fondos incluidos en la APK: no se descargan ni
realizan peticiones. Los efectos ambientales de Cyberpunk Immersive se dibujan
localmente mientras Home está visible y con foco. Se detienen al salir, activar
Reducir movimiento o entrar en ahorro de batería. Android recibe la misma imagen
estática base; no se añade servicio de fondo ni de wallpaper animado.
