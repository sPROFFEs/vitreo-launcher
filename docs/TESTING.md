# Pruebas en dispositivos físicos

La CI prueba emuladores Android 12/15. No sustituye una comprobación en Pixel,
Samsung o GrapheneOS. Anota el modelo, versión/build de Android, versión/app ID de Vitreo y SHA
antes de abrir una incidencia. El emulador AOSP no cubre estas diferencias.

## Instalación

Descarga el asset `.apk` de la release de tu repo, no el ZIP de código fuente.
Comprueba SHA-256 y la huella de firma de la release. Instálalo mediante el flujo
normal del sistema, habilitando instalar apps para ese origen solo si lo pide.
No necesitas Play Services, root, Shizuku, accesibilidad ni permiso de red.

Abre Vitreo y entra en **Hoy → Ajustes → App de inicio**, o usa el selector de aplicación
de inicio en Ajustes de Android. **No deshabilites ni desinstales el launcher de
serie.** Para volver, cambia la app de inicio predeterminada en Ajustes; Vitreo
no exige modificar componentes del sistema.

## Primera comprobación

- Abre varias apps, vuelve con Home y con Atrás, bloquea/desbloquea y gira el teléfono.
- Busca una app por nombre, por paquete y con/sin tildes; comprueba el teclado.
- Usa Editar y arrastra dentro de una página; mantén el icono junto al borde
  para cambiar de página. Mueve apps entre escritorio/dock y verifica el límite de cuatro.
- Crea, renombra y deshaz carpetas. Quita un acceso directo y refresca: no debe reaparecer solo.
- Cambia fondo, transparencia y modo opaco/reducido. Comprueba contraste y ahorro de batería.
- Abre Ajustes desde el modo edición: arrastrar sobre el panel no debe mover iconos detrás.
- Añade widgets reales a Home y Today; cancela y repite la autorización/configuración.
- Cambia Small/Medium/Large, página y posición por arrastre. Reinicia y comprueba la persistencia.
- Prueba proveedores altos/anchos: su contenido debe permanecer dentro del contenedor y la vista previa tener margen superior.
- Abre/cierra carpetas y categorías lentamente y con un flick; invierte un gesto a mitad y comprueba que vuelve sin saltar.
- Alterna apps y HOME repetidamente con las animaciones del sistema activadas.
- Abre Recientes y vuelve a Home: los iconos entran desde los bordes sin recolocar
  las celdas; el fondo permanece en la superficie del sistema. Repite con foto,
  dimming y Cyberpunk. Sync with Android Home solo aplica siendo launcher predeterminado.
- Arrastra despacio entre Home/Today y Home/Library, invierte a mitad y completa
  en ambos sentidos: dock y Search se deslizan/desvanecen sin cambiar las filas de Home.
- Today → Edit: mueve Suggestions antes del reloj con flechas y asas, quita ambas
  tarjetas y recupéralas. Traslada Clock & Calendar a Home y reinicia. Cambia el orden
  de dos widgets Android desde sus menús; sus tamaños y vinculaciones se conservan.
- Abre Control Center con un gesto corto desde arriba a la derecha, empezando debajo
  de la barra de Android, en las tres vistas. Usa también su botón de Today y ciérralo
  hacia arriba desde el asa inferior; desplazar su contenido no debe cerrarlo.
- Cambia volumen multimedia y comprueba el volumen de Android; cambia Home brightness
  y sal a otra app para comprobar su alcance. Restablece Use system brightness.
- Prueba reproducción con un reproductor activo, linterna libre/ocupada y accesos a
  temporizador, cámara, calculadora y paneles Android. Un acceso sin app compatible
  debe avisar. Redes/Focus/rotación abren ajustes, no simulan un interruptor concedido.
- Edita los accesos del panel, reinicia y restaura sus valores predeterminados.
  Desactívalo en Settings → Control Center: desaparece el botón de Today y ese gesto
  ya no abre el panel; Android Quick Settings sigue funcionando.
- Exporta un backup, cambia la distribución e impórtalo. El fondo de foto no viaja en la copia.
- Cierra el proceso y reinicia el teléfono: revisa que los cambios guardados permanezcan.
- Instala y desinstala una app de prueba: se añade al instalar y desaparece de Home/dock/carpetas al desinstalar. Comprueba también una actualización: debe conservar su posición.
- Desinstala una app con Vitreo cerrado, vuelve y comprueba que no quede su acceso. Una app solo temporalmente no disponible conserva su posición.
- Quitar un acceso desde Vitreo no desinstala la app.
- Selecciona Cyberpunk en Themes & Icons y revisa Home, Dock, carpetas, categorías,
  búsqueda, menús y widgets; vuelve a iOS y comprueba que no cambió la distribución.
- Instala un pack ADW/Nova, selecciónalo y elige un icono individual desde More →
  Change Icon. Actualiza/desinstala el pack y restaura una copia sin él instalado:
  los accesos siguen presentes con su arte predeterminado. Prueba alias de Telegram.
- Comprueba HUD Effects, Reduce Motion y ahorro de batería; el reposo no debe animar
  scanlines ni glitches. Compara recorridos repetidos con efectos activados/desactivados.

Prueba después texto grande, orientación horizontal, navegación por gestos y de
tres botones. Los perfiles de trabajo y el espacio privado están fuera de alcance;
no interpretes su ausencia como una implementación terminada.

## Diagnóstico

En Ajustes de Vitreo, usa **Acerca de Vitreo → Guardar diagnóstico**. Incluye versión,
modelo, API, modo de build y el último error local registrado. No incluye el
inventario de apps ni la imagen de fondo. Revisa el informe antes de adjuntarlo:
un stacktrace puede contener información técnica o contextual.

Para un cierre inesperado, adjunta también los pasos exactos y, cuando sea posible,
un logcat filtrado. Los builds optimizados tienen `mapping.txt` para retrace.
No compartas un logcat completo con información de otras aplicaciones sin revisarlo.

Si la pantalla no permite recuperar Home, entra en Ajustes desde los accesos del
sistema y selecciona el launcher de serie. Desinstalar o borrar datos elimina
la distribución y el fondo local: exporta antes cuando sea posible.

System Home integration tests create the real HOME task with an implicit shell
intent after selecting Vitreo as the default. App launch/Back/HOME restoration and
Recents therefore run against Android's actual Home task, rather than an explicit
standard task owned by ActivityScenario. App return launches the separate fixture
APK, whose single activity uses Android's default Back behavior; Settings can
consume Back inside its own navigation. Settings still seeds a real task for
Overview, and system animations stay enabled. The Overview check waits for the stock emulator's visible
`action_screenshot` control: Quickstep may host Overview in different activities.
The tests keep the same Activity/ViewModel, search query, app layout and wallpaper
identity assertions. This fixture depends on the pinned Google APIs emulators;
it is not a compatibility claim for a manufacturer's private Recents implementation.

The default HOME role and system animation scales are held for the whole integration
test class, then restored once. Each method replaces the real Home task with an
implicit clear-task launch. This avoids switching Quickstep's asynchronous default
Home observer back and forth between adjacent tests. Back and HOME use actual
system key events. CI invokes the same AndroidJUnitRunner directly against prebuilt
APKs; JUnit XML and full logcat are retained in instrumentation artifacts.

`LocalWallpaperRule` disables system synchronization before unrelated UI scenarios
start and restores the saved setting after their class. This prevents SystemUI's
delayed wallpaper color overlays from recreating an unrelated menu/search test.
The dedicated Home/Recents integration test explicitly enables real synchronization
and still requires the system surface, stable wallpaper ID and retained ViewModel.
Readiness checks follow the current ActivityScenario instance across configuration
changes, and HOME helpers wait for their marked intent to be delivered.
Search closing also checks Android's actual IME inset visibility, rather than the
presence of an accessibility window. The retained closing text field is disabled
immediately, and removing its surface cancels pending keyboard visibility requests.

## Optional system panels (0.5.4)

`LauncherSmokeTest.optionalPanelsShowRealNotificationsAndControlTheActiveMediaSession`
authorizes the notification listener **only on an emulator**, creates a real
MediaSession with deterministic metadata/callbacks and posts alerts from the
separate disposable fixture APK. An additional active-but-idle assistant session
must not displace the paused track; JVM cases also cover player priority and empty/error sessions.
It verifies the real Play callback, unsupported
Previous action, notification dismissal, custom status-bar visibility/restoration,
disable cleanup and access revocation. It restores listener authorization and
appearance and removes the fixture in `finally`. Production authorization always
uses Android's user-facing Settings screen. Required captures include the custom
bar, connected player and actual notifications on both API levels.

The widget scenario completes the search with the keyboard's Done action and
waits for the real IME insets to clear before locating/tapping Add. This avoids
injecting a touch into a row moving with Gboard's viewport animation; binding,
resizing, page moves and removal remain mandatory. A failed add saves the actual
screen for inspection.

Core gesture tests cover panel boundaries, the reserved Android edge, disabled
panels and cancellation/flick thresholds. Codec tests verify off-by-default
migration, backup round trips and clamped gesture preferences. Python manifest
checks reject exported/unprotected listeners, extra services and extra permissions;
APK collection independently checks the compiled listener contract.
