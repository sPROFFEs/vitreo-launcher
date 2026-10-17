# Licencias y atribuciones

## Código propio de Vitreo

MIT, Copyright (c) 2026 Vitreo contributors. Texto completo en `LICENSE`.
El nombre, vector de icono, fondos procedurales y shader `glass.agsl` son propios.
No existe afiliación con Apple, GrapheneOS, SVG Stack o Folio.

## Arte de iconos de SVG Stack

22 imágenes WebP del conjunto [Apple IOS Icon Set](https://svgstack.com/collections/apple-ios-icon-set),
publicadas por [SVG Stack](https://svgstack.com), según su [política CC BY 4.0](https://svgstack.com/page/license).
Se conservan sin modificar; el launcher aplica máscara, tamaño y tratamientos de
color, y superpone hora y fecha actuales. Procedencia y hashes por recurso en
`third_party/svgstack/assets.json`; licencia completa en `third_party/licenses/CC-BY-4.0.txt`.
La atribución y las fuentes también se incluyen en los avisos de la APK.

## Folio: geometría, movimiento y anclaje de menús adaptados

MIT, Copyright (c) 2026 Duo Launcher contributors.
Conservar `third_party/folio/LICENSE`, los encabezados de `FolioGeometry.kt`, `SurfaceMotion.kt`, `AppContextMenu.kt` y
`third_party/folio/PROVENANCE.md` al redistribuir. La procedencia exacta y la
limitación de la referencia upstream están documentadas allí.

## Bibliotecas de la aplicación

Las dependencias declaradas de AndroidX/Jetpack Compose, Material Components,
Kotlin y kotlinx.coroutines utilizan Apache License 2.0. El texto se incluye en
`third_party/licenses/Apache-2.0.txt` y en los assets de la APK.

Fuentes primarias para revisar sus avisos:
- AndroidX: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt
- Kotlin: https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt
- kotlinx.coroutines: https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt
- Material Icons: https://github.com/google/material-design-icons/blob/master/LICENSE

Las dependencias se descargan de Google Maven/Maven Central al compilar, no se
incluyen como binarios en el repositorio. Para revisar el árbol resuelto y los
avisos de sus versiones transitivas:
`bash gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
La configuración de empaquetado combina los avisos META-INF comunes en lugar de
eliminarlos. Este documento no sustituye esa verificación de dependencias.

JUnit, JSON-java y las herramientas de compilación se usan para pruebas/build;
no son dependencias runtime adicionales de la aplicación. Android proporciona
`org.json` en el dispositivo. Los workflows usan acciones de terceros fijadas
por SHA; no se ha incluido su código dentro de Vitreo.

## Exclusiones deliberadas

Liquid Touch no se incorpora. Backdrop tampoco es una dependencia. No hay
fuentes comerciales, APKs upstream ni una clave privada de firma
compartida en el repositorio.

El icono de Contactos (`drawable/ios_contacts.xml`) es una ilustración vectorial original de Vitreo: agenda gris con pestañas de color, bajo la licencia MIT del proyecto. Sustituye al icono azul de SVG Stack utilizado en 0.4.1.
