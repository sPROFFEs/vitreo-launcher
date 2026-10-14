# Revisión de Vitreo 0.1.0

Fecha: 2026-10-14. Revisión de código y pruebas disponibles, no auditoría de
seguridad exhaustiva ni validación de una APK. No se han hecho cambios en GitHub.

## Fallos reproducidos y corregidos

| Hallazgo | Antes | Corrección y prueba |
|---|---|---|
| ID de carpeta mayor de 128 caracteres | Dos accesos pasaban a cero | Validación previa; se mantiene el estado |
| App nueva en dock con 2.000 apps colocadas | Se perdía otro acceso del escritorio | Rechazo previo a capacidad; no se desplaza otro |
| App nueva en carpeta con 2.000 apps colocadas | Se perdía otro acceso | Mismo control; mover una existente sigue permitido |

Los logs `regressions-before.log` y `regressions-after.log` se generaron ejecutando
un programa Kotlin contra ambos estados del código. Además, hay siete escenarios
permanentes nuevos dentro del harness del núcleo.

## Otros cambios revisados

La carga/importación del fondo ahora comparte exclusión mutua. El escritor del
estado no depende de la cancelación del ViewModel y drena su canal al cerrarse;
esto no evita la pérdida del último cambio si el sistema mata el proceso.
Se añaden etiquetas accesibles al dock y a iconos sin nombres visibles, y se
conecta la etiqueta de cada switch a su control. Se deshabilita el ajuste de
esmerilado en API 31/32, donde el fallback fijo no lo utilizaba.

## Cambios de publicación

Los emuladores API 31/35 pasan a ser requisito y se exige evidencia del mismo
commit. Se separan `build` y `publish`: solo el segundo tiene `contents: write`
y no recibe las claves de firma. La release exige todos los secrets; Preview
queda en artifacts de CI. El certificado de la APK se compara con la clave
configurada. Se comprueban versiones, identidad, permisos, SDK, no-debuggable,
checksums completos y digests de upload antes de publicar el borrador.

Una release existente con tag de otro commit ya no devuelve éxito silencioso:
se detiene y exige subir la versión. No se sobrescriben archivos publicados. Los artifacts intermedios admiten
reintentos de jobs fallidos sin exigir reejecutar los jobs que ya pasaron.
Los tests Python simulan GitHub; prueban fallos de upload, hashes alterados,
evidencia incompleta, rutas/archivos indebidos, Preview y tags incompatibles.
No equivalen a una release ejecutada contra GitHub.

## Documentación y procedencia

README, BUILDING, arquitectura, firma, changelog y validación se han actualizado.
Folio se referencia por commit inmutable comprobado, y su LICENSE coincide con
el blob publicado. No se ha convertido Vitreo en un fork completo: sigue siendo
un launcher nuevo con un fragmento geométrico adaptado y shader propio.

## Bloqueos reales

La integración GitHub de esta conversación permite lectura y reconoce la cuenta,
pero no expone operaciones de crear repositorio, hacer push, escribir secrets o
lanzar workflows. La consulta al nombre previsto devolvió 404; eso no demuestra
que no pueda existir un repositorio privado fuera del acceso de la integración.
No hay CLI GitHub autenticada en este entorno. No se ha realizado ninguna escritura.

El intento Android se detuvo en la descarga de Gradle por resolución DNS. No se
han ejecutado el compilador Android, lint, R8, emulador o la APK. Las pruebas que
requieren Android están implementadas para Actions, pero pendientes de ejecutar.
El archivo `VALIDATION.md` separa resultados medidos de tareas pendientes.
