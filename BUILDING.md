# Compilación y publicación

## Herramientas fijadas

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 8.9.2 |
| Gradle | 8.11.1 |
| JDK para Android build | 17 |
| Kotlin y plugin Compose | 2.1.20 |
| Compose BOM | 2025.04.01 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 31 |
| Android build tools | 35.0.0 |

Se elige un toolchain fijo y conservador; no se afirma que sean las versiones más
nuevas. Referencia oficial AGP/Gradle/JDK:
https://developer.android.com/build/releases/agp-8-9-0-release-notes

## Ruta de GitHub Actions

`release.yml` se ejecuta al subir a `main`, al subir una etiqueta `v*` o mediante
**Run workflow**. La etiqueta debe coincidir con `version.properties` y el build
manual debe ejecutarse en `main` o en la etiqueta exacta. `main` debe ser la rama
por defecto del repo. No se publican releases desde pull requests.

`source-checks` comprueba que estén presentes los cuatro secrets de firma antes
de ejecutar los emuladores. Si faltan, el error enumera sus nombres y el lugar
de configuración. Esta comprobación no escribe la clave ni exporta contraseñas;
la clave se materializa y verifica después en el job `build`.

`build` compila una vez Debug, su APK de tests, Preview optimizada, Release firmada
y el fixture de desinstalación; también ejecuta pruebas JVM y lint. Ambos emuladores
descargan los mismos APK del run y ejecutan AndroidJUnitRunner con
`adb shell am instrument -w -r`, sin repetir Gradle. El lector de resultados exige
eventos de inicio/fin completos, recuentos coherentes y un resumen final correcto.
Fallos, omisiones, crashes y salida truncada bloquean la publicación; se conservan
JUnit, logcat completo y capturas antes de retirar las APK.

Después de pasar ambos emuladores, `verify` ejecuta `apksigner verify`, comprueba
identificador/versión/permisos con `aapt` y calcula SHA-256. Una release nueva se
crea como borrador, se suben todos sus assets y solo entonces se publica como
release normal (`prerelease: false`). `Latest` se selecciona con la política de
versiones de GitHub. Una prerelease existente del mismo commit puede promoverse
sin reemplazar sus assets; los tags y APK publicados siguen siendo inmutables.

`ci.yml` comprueba pull requests sin exponer secretos y genera un artifact Preview.
`device-smoke.yml` es reutilizable y también manual: sus emuladores API 31/35 son
obligatorios antes de verificar y publicar el APK firmado. Comprueba instrumentación
Debug y arranque Preview optimizada. `device_smoke.py`
rechaza dispositivos físicos antes de instalar, borrar datos o cambiar HOME.
El emulador no es GrapheneOS ni sustituye al Pixel real. No ejecuta la APK release
firmada: esa APK se valida estáticamente mediante aapt, apksigner y comparación de
su certificado con la clave configurada.

La caché de tareas y dependencias de Gradle está activada. Solo los builds de main
pueden escribir cachés compartidas; PRs y otras referencias las leen. La caché de
configuración está desactivada. Se mantienen emuladores nuevos, sin snapshots de
estado de apps. La clave de firma es temporal en los jobs de compilación y
verificación; no se sube como artifact ni se envía a los emuladores.

Los jobs `build` y `verify` no tienen permiso de escritura del repo. `publish` recibe los assets,
revalida sus checksums y la evidencia del commit, y comprueba el digest SHA-256 que
GitHub devuelve para cada upload. Solo publica cuando todos los assets están
verificados. La clave de firma no se comparte con ese job.

Las acciones están fijadas por SHA y Dependabot propone sus actualizaciones.
Revisa las propuestas: no actualices automáticamente código que recibe claves de
firma. Los scripts de publicación requieren `contents: write`. Políticas de la
organización, reglas de etiquetas o restricciones de Actions pueden impedirlo.

## Compilar localmente

Instala JDK 17, Python 3.10+ y el Android SDK oficial. Configura `ANDROID_HOME` y
asegúrate de tener plataforma 35, build-tools 35.0.0 y platform-tools.
También puedes abrir el proyecto en Android Studio y usar su JDK/SDK apropiados.
El build requiere red para descargar Gradle y las dependencias.

```bash
bash gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assemblePreview
```

APK de pruebas: `app/build/outputs/apk/preview/app-preview.apk`.
Para desarrollo, usa `:app:assembleDebug`; tiene ID `dev.vitreo.launcher.debug`.
En Windows, `gradlew.bat` usa `python` del PATH.

### Por qué hay un gradlew sin gradle-wrapper.jar

El proyecto utiliza un **bootstrap de texto propio** (`scripts/gradle_bootstrap.py`),
no pretende incluir el wrapper oficial completo. Busca Gradle 8.11.1 ya instalado;
en su ausencia descarga el ZIP oficial, verifica el SHA-256 publicado y solo
entonces lo extrae y ejecuta. No ejecuta un JAR descargado sin verificar.
En Actions, `setup-gradle` proporciona esa versión directamente.

Distribución: https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`
Fuente: https://gradle.org/release-checksums/

Puedes generar después el wrapper oficial con Gradle verificado y revisar/commitear
los archivos resultantes. No mezcles un wrapper de otra versión con el checksum.

## Pruebas offline adicionales

```bash
python3 scripts/check_project.py
python3 -m unittest discover -s tests -v
bash scripts/test_core.sh
```

La última requiere Kotlin CLI. El núcleo también se prueba con Gradle/JUnit.
`scripts/test_syntax.sh` requiere `KOTLIN_HOME` y solo analiza sintaxis Kotlin.
No comprueba tipos Android, Compose, recursos ni ejecución AGSL.

## Fallos habituales

| Síntoma | Acción |
|---|---|
| No aparece ningún workflow | Confirma que `.github/workflows/` está en la raíz, que la rama es `main` y Actions está habilitado |
| `Incomplete signing secrets` / `A release requires all four` | Configura los cuatro secrets; para una APK temporal ejecuta Pull request checks y descarga su artifact Preview |
| `Keystore was tampered with` / alias no encontrado | Revisa base64, tipo de almacén, alias y contraseñas; nunca imprimas los secretos en logs |
| 403 al publicar | Revisa políticas de Actions, permiso `contents: write` y reglas de etiquetas; no concedas un token personal amplio como arreglo automático |
| Borrador `v0.1.0` ya existente | Revisa el build interrumpido; elimina expresamente el borrador incompleto y reejecuta el mismo commit. No borres una release pública |
| Tag de versión en otro commit | Incrementa con `bump_version.py`; no muevas el tag ni sobrescribas una release |
| `homeIntentClosesOpenSheet`: espera `DESTROYED`, último estado `PAUSED`, o actividad destruida | La regla inicia el launcher con `MAIN/HOME` desde el principio. Android distingue las tareas HOME de las de apps: cambiar desde LAUNCHER puede abrir otra instancia, y `ActivityScenario` solo sigue intents equivalentes. El test comprueba que HOME reutilice la actividad y quede en `RESUMED`; la regla gestiona el cierre |
| Error real de Kotlin/lint/AGSL | Conserva el log completo de la tarea y el SHA; consulta también los reports de Actions |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | No coincide la firma; consulta SIGNING.md antes de desinstalar |
| `VERSION_DOWNGRADE` | No se instala encima una versionCode menor; incrementa la versión manteniendo firma y app ID |

No hay un SBOM resuelto, verificación criptográfica completa de todas las
dependencias transitivas ni una promesa de builds reproducibles bit a bit. Esas
son tareas posteriores al primer build funcional, no verificaciones ya hechas.

## Evidencia de verificación

Los builds Android, las pruebas JVM, lint y los emuladores se ejecutan en Actions.
Cada release incluye `build-info.json` con el commit, la firma y los resultados
reales de API 31/35. Las capturas y los JUnit completos quedan en los artifacts
de esa ejecución. Un análisis de sintaxis o un test Python simulado no sustituye
a una compilación Android ni a las pruebas de dispositivo.

Los artifacts intermedios de pruebas/candidato se identifican por `run_id` y
commit, no por intento. Se pueden volver a generar dentro de esa ejecución:
así, **Re-run failed jobs** puede recuperar el resultado de un job previo que
ya pasó. Esta sustitución afecta a artifacts temporales de Actions, nunca a
los assets de una release publicada.

## Pruebas de widgets y movimiento

La instrumentación incluye un proveedor aislado en la APK de tests, habilitado
solo durante su escenario. Comprueba alta real desde la galería, tamaños, cambio
de página, persistencia tras recrear la actividad, movimiento a Today y liberación
del ID. No se incluye ese proveedor en las APK de usuario. Las pruebas de píxeles
comprueban el margen de la vista previa y el recorte nativo de contenido desbordado.

Para ejecutar la matriz sin publicar una release, usa **Emulator smoke tests → Run
workflow** sobre la rama deseada. El script de smoke borra datos y cambia HOME
únicamente en un emulador desechable. La guía de pruebas físicas está en
[docs/TESTING.md](docs/TESTING.md); el diseño de movimiento en
[docs/MOTION.md](docs/MOTION.md).

La matriz también compila `:uninstallFixture:assembleDebug`, una APK Java mínima
de `test-fixtures/uninstall-app`. `device_smoke.py` comprueba antes que el destino
sea un emulador desechable y copia esta APK a `/data/local/tmp/`. La prueba instala,
actualiza y desinstala exclusivamente `dev.vitreo.fixture.uninstall`; comprueba Home,
dock, carpetas y recientes. No toca apps de usuario ni se distribuye ese fixture
con la release. Una prueba separada comprueba la limpieza de paquetes ausentes al
refrescar y la conservación de alias deshabilitados y paquetes en actualización.
