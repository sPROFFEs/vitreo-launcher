# Seguridad del proyecto

No se ha realizado una auditoría de seguridad de Vitreo. Es un prototipo.
No incluyas tokens, claves de firma, contraseñas, backups completos ni datos de
otras aplicaciones en una issue pública.

Para un repo propio, habilita el canal de notificación privada de vulnerabilidades
de GitHub o define tu contacto antes de una publicación estable. Este ZIP no
inventa una dirección de soporte ni un equipo de respuesta.

Protege la rama que ejecuta el build firmado. Cualquier cambio de Gradle, scripts
o workflows que reciba secrets puede abusar de ellos. Las pull requests no
reciben claves y no utilizan pull_request_target.

Las claves de firma no son recuperables desde la APK. Conserva una copia cifrada
y separada de sus credenciales. No subas un keystore al repositorio para facilitar
el build. Ante una posible filtración, detén las releases antes de seguir publicando.
