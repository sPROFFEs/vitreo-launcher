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

El build realiza comprobaciones estructurales, pruebas de scripts, pruebas JVM,
lint de Android y build optimizado. Después ejecuta `apksigner verify`, comprueba
identificador/versión/permisos con `aapt` y calcula SHA-256. Una release nueva se
crea como borrador, se suben todos sus assets y solo entonces se publica.

`ci.yml` comprueba pull requests sin exponer secretos y genera un artifact Preview.
`device-smoke.yml` es reutilizable y también manual: sus emuladores API 31/35 son
obligatorios antes del build firmado. Comprueba instrumentación Debug y arranque
Preview optimizada; registra JUnit, logcat y captura del emulador. `device_smoke.py`
rechaza dispositivos físicos antes de instalar, borrar datos o cambiar HOME.
El emulador no es GrapheneOS ni sustituye al Pixel real. No ejecuta la APK release
firmada: esa APK se valida estáticamente mediante aapt, apksigner y comparación de
su certificado con la clave configurada.

El job `build` no tiene permiso de escritura del repo. `publish` recibe los assets,
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
En Windows, `gradlew.bat` usa `python` del PATH. El helper Python de publicación
puede invocarse directamente: `python scripts/bootstrap_repository.py OWNER/REPO`.

### Por qué hay un gradlew sin gradle-wrapper.jar

Este ZIP utiliza un **bootstrap de texto propio** (`scripts/gradle_bootstrap.py`),
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
| El helper se detiene con repo existente | Solo crea repos nuevos; para el existente configura los secrets según SIGNING.md y usa Git normal |
| Falla después de crear un repo vacío | No borres claves privadas; revisa el error, configura secrets en ese repo y sube con Git, o elimina expresamente el repo vacío antes de repetir |
| `Incomplete signing secrets` / `A release requires all four` | Configura los cuatro secrets; para una APK temporal ejecuta Pull request checks y descarga su artifact Preview |
| `Keystore was tampered with` / alias no encontrado | Revisa base64, tipo de almacén, alias y contraseñas; nunca imprimas los secretos en logs |
| 403 al publicar | Revisa políticas de Actions, permiso `contents: write` y reglas de etiquetas; no concedas un token personal amplio como arreglo automático |
| Borrador `v0.1.0` ya existente | Revisa el build interrumpido; elimina expresamente el borrador incompleto y reejecuta el mismo commit. No borres una release pública |
| Tag de versión en otro commit | Incrementa con `bump_version.py`; no muevas el tag ni sobrescribas una release |
| `homeIntentClosesOpenSheet`: espera `DESTROYED`, último estado `PAUSED` | El test debe restaurar en `finally` el intent de lanzamiento tras comprobar HOME: `ActivityScenario` deja de reconocer los eventos si `setIntent()` cambia sus categorías. El test corregido comprueba también el cierre del escenario |
| Error real de Kotlin/lint/AGSL | Conserva el log completo de la tarea y el SHA. Esta entrega no ha pasado una compilación Android local |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | No coincide la firma; consulta SIGNING.md antes de desinstalar |
| `VERSION_DOWNGRADE` | No se instala encima una versionCode menor; incrementa la versión manteniendo firma y app ID |

No hay un SBOM resuelto, verificación criptográfica completa de todas las
dependencias transitivas ni una promesa de builds reproducibles bit a bit. Esas
son tareas posteriores al primer build funcional, no verificaciones ya hechas.

## Estado de esta entrega revisada

La validación local no ha podido descargar Gradle por resolución DNS fallida.
Los workflows se han revisado y validado estructuralmente, pero no ejecutado en
GitHub desde este entorno. Consulta los logs en `verification/`; no confundas
`kotlin-syntax.log` con una compilación Android ni tests Python simulados con
una publicación real.

Los artifacts intermedios de pruebas/candidato se identifican por `run_id` y
commit, no por intento. Se pueden volver a generar dentro de esa ejecución:
así, **Re-run failed jobs** puede recuperar el resultado de un job previo que
ya pasó. Esta sustitución afecta a artifacts temporales de Actions, nunca a
los assets de una release publicada.
