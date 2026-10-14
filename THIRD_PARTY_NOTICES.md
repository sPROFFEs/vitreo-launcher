# Licencias y atribuciones

## Código propio de Vitreo

MIT, Copyright (c) 2026 Vitreo contributors. Texto completo en `LICENSE`.
El nombre, vector de icono, fondos procedurales y shader `glass.agsl` son propios.
No se incluyen recursos de Apple. No existe afiliación con Apple, GrapheneOS o Folio.

## Folio: fragmentos geométricos adaptados

MIT, Copyright (c) 2026 Duo Launcher contributors.
Conservar `third_party/folio/LICENSE`, el encabezado de `FolioGeometry.kt` y
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
incluyen como binarios en este ZIP. Antes de una distribución pública estable,
revisar el árbol realmente resuelto y los avisos de sus versiones transitivas:
`bash gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
La configuración de empaquetado combina los avisos META-INF comunes en lugar de
eliminarlos. Este documento no sustituye esa verificación de dependencias.

JUnit, JSON-java y las herramientas de compilación se usan para pruebas/build;
no son dependencias runtime adicionales de la aplicación. Android proporciona
`org.json` en el dispositivo. Los workflows usan acciones de terceros fijadas
por SHA; no se ha incluido su código dentro de Vitreo.

## Exclusiones deliberadas

Liquid Touch no se incorpora. Backdrop tampoco es una dependencia. No hay
iconos de iOS, fuentes comerciales, APKs upstream ni una clave privada de firma
compartida en el repositorio.
