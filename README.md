# Vitreo Launcher 0.2.0

Un inicio Android inspirado en la pantalla de inicio actual de iOS y en
[Folio](https://github.com/McCal-Codes/folio), con una interfaz más pequeña y directa.
Android 12 o posterior, sin root, cuentas, anuncios ni telemetría.

<p>
  <img src="docs/screenshots/home.png" width="240" alt="Inicio de Vitreo con widgets y dock de cristal">
  <img src="docs/screenshots/settings.png" width="240" alt="Ajustes agrupados de Vitreo">
</p>

Capturas reales del APK Preview en Android 12.

## La experiencia

- **Inicio:** cuatro columnas, iconos con esquinas continuas y un dock de cristal de cuatro plazas.
- **Widgets de inicio:** reloj analógico y calendario con la fecha real, que puedes mostrar u ocultar.
- **Hoy:** desliza a la derecha. Sugerencias basadas en las apps que abres desde Vitreo y widgets reales de tus apps Android.
- **Spotlight:** toca Buscar o desliza hacia abajo. Búsqueda local por nombre o paquete, con acceso inmediato al teclado.
- **Biblioteca de apps:** después de la última página. Grupos automáticos y acceso a todas tus aplicaciones.
- **Carpetas:** panel central de cristal, cuadrícula de tres por tres, páginas y nombre editable.
- **Edición:** mantén pulsado un espacio libre, o elige Editar pantalla de inicio en el menú de un icono. Los iconos se mueven suavemente; puedes reordenarlos, moverlos entre páginas manteniéndolos junto al borde y cambiarlos con los del dock. Mantén una app sobre otra para crear una carpeta o añadirla a una existente.
- **Personalización:** iconos claros, oscuros, tintados o de cristal; arte propio para las funciones comunes y opción de conservar los iconos de las apps; aspecto claro, oscuro o automático; cuatro fondos originales y fotos locales.
- **Ajustes:** listas agrupadas, controles segmentados e interruptores al estilo iOS. Se abren desde la rueda de Hoy o desde el modo edición.
- **Centro de control:** desliza hacia abajo desde la zona superior derecha del inicio. Accesos funcionales a Internet, Bluetooth, sonido y pantalla, además del aspecto y widgets de Vitreo.

El sistema mantiene su teclado, pantalla de bloqueo, notificaciones y Recientes.
Los controles de conectividad y sonido abren los paneles de Android. Los widgets
nativos se añaden a Hoy mediante la autorización del sistema; sus IDs pertenecen
a esta instalación y no se incluyen en copias portables.

## Instalar y actualizar

Descarga la APK firmada en [Releases](https://github.com/sPROFFEs/vitreo-launcher/releases).
La identidad de release es `dev.vitreo.launcher`. La versión 0.2.0 conserva la firma
anterior y sube el versionCode a 200: se puede actualizar sin desinstalar ni perder
la distribución. Las copias de 0.1.0 siguen siendo compatibles.

Preview (`dev.vitreo.launcher.preview`) y Debug (`dev.vitreo.launcher.debug`) son
apps separadas. Preview usa una firma temporal del entorno de pruebas.

## Compilar y publicar

Con JDK 17 y SDK Android 35:

```bash
bash gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assemblePreview
python3 scripts/check_project.py
python3 -m unittest discover -s tests -v
```

`Release APK` se ejecuta al subir a `main`. Comprueba fuente y firma configurada,
prueba emuladores API 31 y 35, compila la release optimizada y verifica identidad,
permisos, certificado y hashes antes de publicarla. Solo publica una APK con los
cuatro secrets privados de firma. Consulta [BUILDING.md](BUILDING.md) y
[SIGNING.md](docs/SIGNING.md).

Para la siguiente versión:

```bash
python3 scripts/bump_version.py 0.2.1
git add app scripts version.properties README.md CHANGELOG.md
git commit -m "Update Vitreo"
git push origin main
```

Los assets publicados son inmutables: cada revisión utiliza una versión nueva.

## Diseño, privacidad y procedencia

La referencia visual es [iOS 27](https://www.apple.com/os/ios/) y sus controles
Liquid Glass, junto con las interacciones de [Folio](https://github.com/McCal-Codes/folio).
Vitreo dibuja sus propios fondos y materiales y utiliza el arte de las aplicaciones
instaladas. La geometría adaptada de Folio conserva su licencia y atribución en
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

El cristal solo procesa el fondo propio del launcher. Los datos se conservan en
el dispositivo. El único permiso específico de esta versión es la vinculación
opcional de widgets a través del diálogo de Android. Más detalles en
[PRIVACY.md](PRIVACY.md). Licencia MIT.
