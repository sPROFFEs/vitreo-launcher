# Pruebas en dispositivos físicos

La CI prueba emuladores Android 12/15. No sustituye una comprobación en Pixel,
Samsung o GrapheneOS. Anota el modelo, versión/build de Android, versión/app ID de Vitreo y SHA
antes de abrir una incidencia. El emulador AOSP no cubre estas diferencias.

## Instalación

Descarga el asset `.apk` de la release de tu repo, no el ZIP de código fuente.
Comprueba SHA-256 y la huella de firma de la release. Instálalo mediante el flujo
normal del sistema, habilitando instalar apps para ese origen solo si lo pide.
No necesitas Play Services, root, Shizuku, accesibilidad ni permisos de red adicionales. Internet se usa para las actualizaciones y las noticias opcionales.

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

CI first runs the application suite with AndroidJUnitRunner's `notClass` filter
for `HomeIntegrationTest`, then the optimized Preview walkthrough. It runs the
three real Home/Recents/overlay tests last, in a separate instrumentation process with the
`class` filter. Changing the global HOME role and Quickstep state must not leak
into subsequent ActivityScenario windows. Both phases are mandatory, independently
parsed for failures/skips, and their exact combined count is checked before
publication. Their XML/output/logcat files are retained separately; the Home phase
does not reinstall APKs or remove the earlier captures. See the official
[runner filters](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner).

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

## Optional lock surface (0.6.0)

Wallpaper regression checks measure actual rendered pixels at 0%, 30% and 60%
dim for both Cyberpunk experiences, photos and all nine palette selections. Check
the slider visually on Classic and Immersive / Neon city; their wallpaper should
respond immediately, retain the setting across theme changes and match Android
Home when synchronization is enabled. iOS uses the unchanged exposure path.

See [Lock screen / GrapheneOS](LOCK_SCREEN.md) for setup and boundaries. CI runs
secure keyguard tests **after** the app, optimized Preview and real HOME checks,
in a separate instrumentation process. It refuses physical devices and existing
credentials, sets a disposable PIN, and restores PIN/HOME/lock timing afterwards.
The release requires both tests in this phase on both APIs and exact accounting
of all three native phases. A missing, skipped or incomplete phase blocks release.

Physical Pixel / GrapheneOS checks must include fingerprint, PIN/passphrase,
lockdown, secondary PIN if enabled, cold boot, switching users, incoming calls,
secure camera and its return, System / Emergency, process death, delayed locking,
Battery Saver and large text. Always retain Android's native lock and system
launcher. No physical GrapheneOS result is claimed by emulator CI.

Lock customization evidence also includes the full-screen draft editor and Cyberpunk Home/status, Notification Center and Control Center. Native checks exercise Save / Cancel, height and font persistence without activation; the large-clock preview uses 210% vertical stretch.

Before application tests, the disposable-emulator fixture confirms Android’s immersive tutorial and performs one clean start of the image’s stock Pixel Launcher / Launcher3 after APK-install broadcasts. It requires stable window focus; any subsequent ANR fails preflight. Product ANRs and failed tests are never dismissed or retried. The tutorial setting is restored, and full window / last-ANR diagnostics accompany logcat on failure. Physical devices and unknown launchers are rejected before the stock restart.


## Version 0.8 network / lock regression checks

- Software Update: reject prereleases, altered APKs, wrong identity/signature and
  rollback; retain Android consent. Debug and Preview must schedule no update job.
- Today: News loads only when visible/resumed/focused; cached stories remain
  readable offline. Refresh retries. Switching back keeps all Today widget order.
- Lock: grant Android notification access with Home Notification Center disabled.
  App-name/count cards appear; private bodies and secret notifications never do.
  Tap, cancel native authentication, retry with PIN and verify destination opens.
  Check the configured idle handoff without interaction, screen-off/on and cancel.
- Manual, disposable-emulator-only positive update probe: build
  `:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleUpdateFixture`,
  install Debug and its test APK, then run `python3 scripts/test_self_update.py`.
  The fixture uses the Debug identity/key but is not debuggable and has version
  code +1. It is never included in release assets. The probe stages/commits through
  the same native installer implementation as production. ADB installs only the
  initial app; the observer requires the actual native version transition. Package
  replacement intentionally kills instrumentation. Use a disposable emulator;
  the fixture must be uninstalled before reinstalling the older Debug build.
- Manual live HTTPS/RSS check: run `adb shell am instrument -w -e mode network
  dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe`. This explicit
  probe is not selected by the normal JUnit suite and has no network mocks.

Installer limitations follow the public
[PackageInstaller contract](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).
Notification taps use the public
[background activity launch opt-in](https://developer.android.com/guide/components/activities/secure-bal).


## Illustrated news / service-independent feeds (0.8.1)

- Publisher RSS is the default, including existing News configurations without a
  provider field. Validate language/topic and actual source attribution. The
  German publisher groups Technology/Science/Health under Wissen and omits
  unsupported Sports/Entertainment choices. Spain/Mexico share EL PAÍS in Spanish.
- With Google source selected, block only Google and refresh: enabled fallback
  shows the independent publisher and its banner. Disable fallback: no publisher
  request may occur. Cancellation and permission denial must not start fallback.
- Toggle Show article images: no photo requests while off. Headline and thumbnail
  cards preserve readable text, clipped photos and large-font layout in both themes.
  Failed or missing photos never prevent a headline from being read.
- Offline refresh retains the cached source, headlines and photo URLs. Changing
  provider, edition or topic must not display another configuration's cached feed.
- GrapheneOS: revoke Vitreo's Network permission, then restore and retry. Also
  check private DNS/VPN restrictions. No Google account or Play Services is required.
- Live opt-in probe on an emulator without Google services:
  `adb shell am instrument -w -e mode news -e market ES -e provider direct -e images true
  dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe`.
  It requires real HTTPS headlines, downloads/decodes publisher images and checks
  an offline refresh against the persisted cache. Repeat with US, FR and DE and
  Google without `-e images true` (Google RSS omits article photographs). This
  manual check is separate from deterministic CI and makes no physical Pixel claim.


## Groups, icon dimensions, Classic and direct updates (0.9.0)

- Hold an app on Home and move it without entering Edit first. Hold over another
  app/folder: hover outline, then the ready outline after 500 ms. Release to group.
  Release early to reorder; cancel without changing the layout. A stationary hold
  still opens app actions. Drag from Dock, check full folders and page edges.
- Open the resulting folder and compare actual icon bounds with Home at 60 and
  100 dp. Library mirrors Home by default; choose its independent 100 dp setting
  and inspect category/alpha lists. Narrow windows should fit previews and adapt
  expanded columns. Check large fonts and each folder page without clipping.
- Select iOS 16 in Themes & Icons, return Home, open categories/controls and inspect
  uniform frost, quiet corners/shadows and original color icons. Return to iOS 26;
  layout, wallpaper and saved optical preferences must remain. Classic's Frost
  control works across its range; Refraction explicitly requires iOS 26.
- The uniform glass pixel case runs on API 33+, using actual hardware AGSL. Existing
  checkerboard and moving-lens cases remain required. Retain both full/reduced captures.
- Run `adb shell am instrument -w -e mode updates-web
  dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe` for a live,
  explicit website-only metadata check (no API request, no installation). It also
  exercises HEAD compatibility with releases before apkSize metadata was published.
- JVM cases inject API rate limiting, enforce the server retry deadline, validate
  direct metadata/checksums and reject altered identity/version/length/path data.
  Cancellation/invalid API metadata must not silently switch verification paths.

Notification island coverage uses a second-UID native overlay fixture to verify
that focus loss and outside dismissal preserve Home's arrival serial. Real native
notification posts cover identity icons, expansion, outside collapse, dismissal,
permission revocation, no inbox replay, media coexistence, screen-off cleanup and
the shared Home/Lock Screen status-bar switch. A slowed native animator verifies
horizontal centre entrance/exit with offset handles, immediate input release,
replacement cleanup and Reduce Motion. Channel shortcuts open actual Android
category settings without removing the notification. The final Home phase requires all
three tests, including the foreign-overlay regression. Pure dp geometry tests
exercise all twelve Aoede controls, safe insets, narrow/landscape screens and yield
policies. Preview permission grants are confined to guarded disposable emulators.

Overlay gestures use WindowInspector for coordinates in the app's own windows and
UiAutomation input injection, which synchronizes Android's native input-window
transactions. Layout completion alone does not guarantee a newly attached overlay
can receive a shell-injected tap. The clock editor waits for dialog focus before
testing its font panel. These checks use actual input and retain all assertions.
Foreign-overlay dismissal uses the same synchronized native input: focus alone
does not ensure its input region has reached SurfaceFlinger on Android 12.
Island tests wait for Home's initial appearance effect. Reduce Motion uses the
persisted application setting, restored afterward, so a listener reconnect or a
late composition cannot overwrite a window-only test override.
Island motion regressions reverse an in-flight morph, replace an alert during
collapse and refresh foreground state without jumping to the old destination.
On API 34+, a compositor screenshot checks both card edges immediately after
expansion/collapse with reduced app motion and slower Android window animations.
This detects a separate window-surface slide that view-coordinate checks miss.
Animation settings and persisted appearance are restored after the test.
