# Validación de la revisión 0.1.0

Fecha: 2026-10-14. Estos resultados pertenecen al ZIP revisado; no se actualizan
automáticamente al hacer commits. La evidencia está en `verification/`.

| Comprobación | Resultado ejecutado aquí |
|---|---|
| Compilar/ejecutar núcleo Kotlin puro | 45 escenarios correctos, incluidos 2.000 pasos aleatorios |
| Runtime de esas pruebas | Kotlin CLI 1.9.0, OpenJDK 21; no el compilador Android |
| Pruebas Python de scripts/evidencia/publicación | 60 tests correctos; GitHub simulado |
| Parser Kotlin | 26 archivos, cero errores de parser; no comprueba tipos Android/Compose |
| XML/manifiesto/acciones/archivos requeridos | Correctos mediante check_project.py |
| YAML de los tres workflows | Parseado y comprobada la estructura/dirección de dependencias localmente |
| Shell/Python | Bash syntax y byte-compilation correctos |
| Regresiones del modelo | Tres fallos reproducidos antes y ausentes después |
| LICENSE Folio | Hash del blob local coincide con la referencia comprobada en GitHub |
| Intento de compilación Android | BLOQUEADO al descargar Gradle: fallo de resolución DNS |
| Creación del repo/push/ejecución Actions/release | NO realizados: no hay herramienta de escritura GitHub ni CLI autenticada |

## No ejecutado y no acreditado

No se han ejecutado Gradle Android, pruebas JVM que dependan de org.json/JUnit del
proyecto, Android lint, R8, tests instrumentados, compilación AGSL en Android,
instalación de APK, verificación del APK con apksigner/aapt ni pruebas físicas
en GrapheneOS. No se ha producido un binario APK. Los scripts y workflows
incluyen esas comprobaciones para realizarlas en un entorno con SDK y red.

La puerta de publicación requiere instrumentación Debug y arranque de Preview
optimizada. Incluso cuando pase, eso no equivale a ejecutar la release firmada
en el Pixel. `build-info.json` registra ambas variantes y la falta de prueba física.
No se prometen builds reproducibles, rendimiento medido ni seguridad auditada.

## Repetir las comprobaciones disponibles

```bash
python3 scripts/check_project.py
python3 -m unittest discover -s tests -v
bash scripts/test_core.sh
KOTLIN_HOME=/ruta/a/kotlin bash scripts/test_syntax.sh
```

Con JDK 17, Android SDK y red, ejecutar además:

```bash
bash gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintPreview :app:assemblePreview
```

El ZIP no contiene secrets, claves privadas, artefactos APK ni logs que afirmen
una publicación inexistente. `verification/status.json` es el resumen de esta
revisión local, no un resultado de GitHub Actions.
