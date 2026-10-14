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
subir versionCode; no se implementa rotación de claves en 0.1.0.
Referencia: https://developer.android.com/studio/publish/app-signing

## Bootstrap recomendado

`bash scripts/publish.sh OWNER/REPO` crea una clave RSA de 3072 bits con `keytool`,
en un almacén PKCS12. Ruta predeterminada, fuera del proyecto:

```text
~/.local/share/vitreo/signing/OWNER__REPO/
    release.p12
    secrets.json
```

Respeta `XDG_DATA_HOME` si está configurado. La carpeta es 0700 y los archivos
0600 en sistemas POSIX. **La clave y su contraseña se guardan juntas** para poder
repetir builds: esos permisos no equivalen a cifrado ni a un gestor de secretos.
Haz una copia cifrada offline o migra ambas piezas a almacenamiento de secretos.
Nunca adjuntes estos archivos a una issue ni los subas a releases.

El helper no sobrescribe material existente. Si encuentra una carpeta incompleta,
se detiene. No vuelve a generar otra identidad silenciosamente.

## Secrets de GitHub Actions

En el repo, **Settings > Secrets and variables > Actions**, configura exactamente:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

El helper los carga por stdin a `gh secret set`, no como argumentos que muestren
contraseñas en la lista de procesos. Con PKCS12 generado por el helper, ambas
contraseñas son la misma. El base64 es codificación, no cifrado.

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

Si ya se publicó `v0.1.0` en modo Preview, no se reemplaza esa release al configurar
los secrets. Incrementa a `0.1.1` y publica la versión privada correctamente firmada.

## Regla de la revisión actual

`Release APK` exige firma privada y ya no publica una Preview como `v0.1.0`.
Preview queda disponible como artifact de `Pull request checks`. El job que
publica la release no tiene la clave; compara nuevamente hashes y verifica los
digests de los assets recibidos por GitHub. No se ha habilitado administrativamente
la inmutabilidad de releases en tu cuenta: es el script el que se niega a sobrescribir.
