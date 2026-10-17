# Firma de APK y continuidad de actualizaciones

## Tres identidades deliberadamente distintas

| Variante | App ID | Firma |
|---|---|---|
| release | `dev.vitreo.launcher` | Clave privada propia, obligatoria |
| preview | `dev.vitreo.launcher.preview` | Clave debug local/temporal del runner, sin garantía entre builds |
| debug | `dev.vitreo.launcher.debug` | Clave debug del entorno de desarrollo |

No uses una clave privada publicada en el repo como firma estable: cualquier
persona que la posea podría firmar código con esa identidad. Vitreo no incluye una.
Para actualizar la misma app normalmente deben conservarse app ID y firma y
subir versionCode; no se implementa rotación de claves.
Referencia: https://developer.android.com/studio/publish/app-signing

## Conservar la clave existente

Las actualizaciones deben usar la clave de la primera release instalada. No generes
otra clave para arreglar un workflow. Conserva una copia cifrada offline del almacén
y sus contraseñas; nunca los añadas al repositorio, una issue o una release.

Las instalaciones preparadas con el antiguo helper pueden tener su copia fuera del
repo, en `~/.local/share/vitreo/signing/OWNER__REPO/` (`release.p12` y `secrets.json`).
Los permisos 0700/0600 no cifran esos archivos. El helper de creación de repositorios
se retiró porque no corresponde al mantenimiento de un repo ya publicado; los
almacenes existentes siguen siendo válidos y no se modifican al actualizar.

## Secrets de GitHub Actions

En el repo, **Settings > Secrets and variables > Actions**, configura exactamente:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Carga sus valores desde tu gestor de secretos. El base64 es codificación, no cifrado.
Con el PKCS12 generado por el helper antiguo, ambas contraseñas son la misma.

Para reponer los secrets en un repo ya existente sin mostrar valores, ejecuta
este fragmento desde la raíz del proyecto y cambia `repo`:

```python
import base64, json, os, subprocess
from pathlib import Path
repo = "TU_USUARIO/vitreo-launcher"
root = Path(os.environ.get("XDG_DATA_HOME", str(Path.home()/".local/share")))
folder = root/"vitreo/signing"/repo.replace("/", "__")
meta = json.loads((folder/"secrets.json").read_text())
values = {
    "ANDROID_KEYSTORE_BASE64": base64.b64encode((folder/"release.p12").read_bytes()).decode(),
    "ANDROID_KEYSTORE_PASSWORD": meta["password"],
    "ANDROID_KEY_ALIAS": meta["alias"],
    "ANDROID_KEY_PASSWORD": meta["password"],
}
for key, value in values.items():
    subprocess.run(["gh", "secret", "set", key, "--repo", repo], input=value, text=True, check=True)
```

En CI, la clave se decodifica solo en `RUNNER_TEMP` y se elimina con un paso
`always()`. No se incluye en cache ni artifacts. Los secretos sí están disponibles
para el código de build de la rama que publicas: protege `main`, limita colaboradores
y revisa cambios en Gradle/scripts/workflows antes de permitir que se ejecuten.
GitHub Secrets no protege de un workflow malicioso al que tú concedas acceso.

## Firma local

Para compilar `assembleRelease`, configura las variables de entorno
`VITREO_STORE_FILE`, `VITREO_STORE_PASSWORD`, `VITREO_KEY_ALIAS` y
`VITREO_KEY_PASSWORD` mediante tu gestor de secretos. No guardes contraseñas en
`gradle.properties`, `local.properties` ni historial del shell.
Sin todas las variables, las tareas de empaquetado release fallan.

## Cambiar desde Preview

Preview y release pueden coexistir porque tienen app IDs distintos. Exporta la
distribución desde Preview, instala release, importa y vuelve a elegir tu fondo.
La copia no contiene la imagen. Elige release como app de inicio y desinstala
Preview después de comprobar el resultado. Al probar otro build de Preview,
conserva primero una copia: las firmas pueden ser diferentes.

## Publicación

`Release APK` exige firma privada. Preview queda como artifact de pruebas.
El job de publicación no recibe la clave; vuelve a comprobar hashes y los digests
de los assets recibidos por GitHub. El script impide sobrescribir tags y assets
públicos; cada revisión publicada necesita una versión superior.
