# Vitreo Launcher 0.1.0

Launcher Android experimental, local y FOSS, con distribución de cuatro columnas
y materiales de cristal propios. Pensado para empezar a probar en GrapheneOS sin
root, Shizuku, servicios de accesibilidad ni superposiciones sobre otras apps.

**Estado de esta revisión:** se han corregido fallos reproducidos y ejecutado
45 escenarios del núcleo Kotlin y 60 tests Python. El parser acepta 26 archivos
Kotlin. **No se ha compilado una APK Android, ejecutado un emulador ni publicado
una release desde este entorno.** El intento de build falló al descargar Gradle
por falta de resolución DNS. No se afirma que el primer build de Actions vaya a pasar.
Consulta [VALIDATION.md](VALIDATION.md) y [AUDIT.md](AUDIT.md).

Es una implementación reducida con fragmentos geométricos adaptados de Folio,
**no un fork completo de Folio**. El shader es nuevo: no se ha copiado Liquid Touch
ni integrado Backdrop. Las partes reutilizadas conservan licencia y procedencia.

## Crear tu repo y obtener la primera APK

Requisitos en el ordenador para esta ruta: Python 3.10+, Git, GitHub CLI (`gh`) y
un JDK con `java` y `keytool` disponibles. No necesitas Android Studio ni SDK local.
La compilación Android ocurre en GitHub Actions.

```bash
unzip vitreo-launcher-0.1.0-reviewed.zip
cd vitreo-launcher-0.1.0
gh auth login --scopes repo,workflow
bash scripts/publish.sh TU_USUARIO/vitreo-launcher
```

Sustituye `TU_USUARIO/vitreo-launcher` por el nombre del **repositorio nuevo**.
El helper crea un repo **privado** por defecto. Añade `--public` para publicarlo
abiertamente. Ejecútalo fuera de cualquier repo Git existente.

El script crea una clave privada propia fuera del proyecto, configura cuatro
secrets en GitHub **antes de subir el código** y hace el primer push a `main`.
No sube la clave como archivo del repositorio. Se autentica con tu sesión local
de `gh`; no pide que pegues tokens en el código ni en el chat.

Abre **Actions > Release APK**. Si las pruebas de fuente, emuladores API 31/35, lint y compilación terminan bien,
publicará **Releases > v0.1.0** como versión preliminar con:

- `vitreo-0.1.0-release.apk`, instalable y firmada con tu clave.
- `SHA256SUMS`, `signing-certificate.txt` y `build-info.json`.
- `mapping.txt` de R8 para depurar errores de la versión optimizada.

**Haz una copia cifrada de la carpeta privada de firma que imprime el script.**
Contiene la clave y su contraseña. Perderla impide mantener esa identidad de firma
en futuras actualizaciones. Detalles y recuperación en [SIGNING.md](docs/SIGNING.md).
Los minutos de Actions están sujetos a la cuota y políticas de tu cuenta/organización.

### Preview sin firma privada

Para probar sin secrets, sube el contenido a un repo y ejecuta manualmente
**Pull request checks**. Su artifact contiene una APK Preview optimizada con ID
`dev.vitreo.launcher.preview`. No reserva ni publica el tag `v0.1.0`.
**Release APK exige los cuatro secrets**: ya no degrada una release a firma temporal.

Preview utiliza una firma temporal del runner. Entre builds puede ser necesario
exportar la distribución, desinstalar e instalar de nuevo. Para actualizaciones
continuas, usa la variante release con tu clave privada conservada.

### Condiciones de publicación

El flujo es `source-checks -> device-tests -> build -> publish`. Las pruebas de
emulador deben pasar para API 31 y 35 en el mismo commit. Incluyen instrumentación
Debug y arranque real de Preview optimizada; **no son una prueba de la APK release
firmada ni del Pixel con GrapheneOS**. `build-info.json` conserva esa distinción.
Solo el job de publicación tiene permiso `contents: write`; no recibe la clave de
firma. Comprueba todos los hashes locales y los devueltos por GitHub antes de
hacer público el borrador. Una transferencia fallida deja un borrador para revisar.

## Qué implementa 0.1.0

| Área | Implementación incluida |
|---|---|
| Inicio | Cuatro columnas, páginas, dock de cuatro apps, selección como HOME |
| Organización | Reordenar en modo edición, carpetas, renombrar/deshacer, acciones de iconos |
| Biblioteca | Apps del usuario Android actual, búsqueda local por nombre/paquete |
| Cristal | Refracción AGSL en API 33+, bordes, esmerilado y opacidad configurables |
| Alternativa gráfica | Textura esmerilada en API 31/32, modo reducido, ahorro de batería y fallback de shader |
| Fondo | Cuatro fondos propios y copia de una imagen elegida mediante selector del sistema |
| Persistencia | Archivo atómico, conservación de accesos no disponibles, importación/exportación JSON |
| Diagnóstico | Informe local exportado expresamente, registro del último cierre inesperado |

El cristal **solo muestrea el fondo propio del launcher**. No captura la pantalla,
no refracta otras apps ni equivale al compositor de iOS. Las carpetas abiertas y
paneles usan ese material; los iconos conservan el aspecto de sus apps.

## Límites que no están ocultos

No hay widgets nativos, paquetes de iconos, espacio privado, perfil de trabajo,
Dynamic Island, centro de control, bloqueo ni sustitución de Recientes. No cambia
las animaciones de sistema de GrapheneOS. Arrastrar funciona en la página visible;
para pasar una app a otra página se usan las acciones Anterior/Siguiente (no hay
autodesplazamiento al borde). El orden del dock se cambia quitando y fijando apps.

Mínimo Android 12/API 31; compile/target SDK 35. La compatibilidad concreta con tu
build de GrapheneOS necesita prueba física. No hay cifras verificadas de consumo,
fluidez ni seguridad auditada. Mantén instalado y disponible el launcher de serie.

## Iterar sin romper las releases

```bash
python3 scripts/bump_version.py 0.1.1
git add .
git commit -m "Fixes for 0.1.1"
git push
```

El workflow vuelve a compilar y ejecutar las pruebas. Nunca sobrescribe assets
publicados. Si la versión existe en otro commit, falla y pide incrementarla;
si ya existe en el mismo commit, no la modifica. Las pruebas de emulador también
pueden ejecutarse manualmente desde **Emulator smoke tests**.

## Documentación

[Compilar y solucionar fallos](BUILDING.md) · [Firma y secrets](docs/SIGNING.md) ·
[Arquitectura](docs/ARCHITECTURE.md) · [Pruebas en GrapheneOS](docs/TESTING_GRAPHENEOS.md) ·
[Privacidad](PRIVACY.md) · [Validación realizada](VALIDATION.md) · [Revisión y regresiones](AUDIT.md) · [Cambios](CHANGELOG.md).

Licencia MIT para el código propio; avisos conservados de Folio y dependencias en
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). No se suministran claves privadas,
APKs ya compiladas ni un checkout upstream oculto.
