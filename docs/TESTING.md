# Pruebas en dispositivos físicos

La CI prueba emuladores Android 12/15. No sustituye una comprobación en Pixel,
Samsung o GrapheneOS. Anota el modelo, versión/build de Android, versión/app ID de Vitreo y SHA
antes de abrir una incidencia. El emulador AOSP no cubre estas diferencias.

## Instalación

Descarga el asset `.apk` de la release de tu repo, no el ZIP de código fuente.
Comprueba SHA-256 y la huella de firma de la release. Instálalo mediante el flujo
normal del sistema, habilitando instalar apps para ese origen solo si lo pide.
No necesitas Play Services, root, Shizuku, accesibilidad ni permiso de red.

Abre Vitreo y entra en **Hoy → Ajustes → App de inicio**, o usa el selector de aplicación
de inicio en Ajustes de Android. **No deshabilites ni desinstales el launcher de
serie.** Para volver, cambia la app de inicio predeterminada en Ajustes; Vitreo
no exige modificar componentes del sistema.

## Primera comprobación

- Abre varias apps, vuelve con Home y con Atrás, bloquea/desbloquea y gira el teléfono.
- Busca una app por nombre, por paquete y con/sin tildes; comprueba el teclado.
- Usa Editar y arrastra dentro de una página; mantén el icono junto al borde
  para cambiar de página. Mueve apps entre escritorio/dock y verifica el límite de cuatro.
- Crea, renombra y deshaz carpetas. Quita un acceso directo y refresca: no debe reaparecer solo.
- Cambia fondo, transparencia y modo opaco/reducido. Comprueba contraste y ahorro de batería.
- Abre Ajustes desde el modo edición: arrastrar sobre el panel no debe mover iconos detrás.
- Añade widgets reales a Home y Today; cancela y repite la autorización/configuración.
- Cambia Small/Medium/Large, página y posición por arrastre. Reinicia y comprueba la persistencia.
- Prueba proveedores altos/anchos: su contenido debe permanecer dentro del contenedor y la vista previa tener margen superior.
- Abre/cierra carpetas y categorías lentamente y con un flick; invierte un gesto a mitad y comprueba que vuelve sin saltar.
- Alterna apps y HOME repetidamente con las animaciones del sistema activadas.
- Exporta un backup, cambia la distribución e impórtalo. El fondo de foto no viaja en la copia.
- Cierra el proceso y reinicia el teléfono: revisa que los cambios guardados permanezcan.
- Instala una app nueva y elimina otra: la nueva se añade, la no disponible conserva
  su referencia hasta que la quites. No se desinstalan apps al quitar accesos.

Prueba después texto grande, orientación horizontal, navegación por gestos y de
tres botones. Los perfiles de trabajo y el espacio privado están fuera de alcance;
no interpretes su ausencia como una implementación terminada.

## Diagnóstico

En Ajustes de Vitreo, usa **Acerca de Vitreo → Guardar diagnóstico**. Incluye versión,
modelo, API, modo de build y el último error local registrado. No incluye el
inventario de apps ni la imagen de fondo. Revisa el informe antes de adjuntarlo:
un stacktrace puede contener información técnica o contextual.

Para un cierre inesperado, adjunta también los pasos exactos y, cuando sea posible,
un logcat filtrado. Los builds optimizados tienen `mapping.txt` para retrace.
No compartas un logcat completo con información de otras aplicaciones sin revisarlo.

Si la pantalla no permite recuperar Home, entra en Ajustes desde los accesos del
sistema y selecciona el launcher de serie. Desinstalar o borrar datos elimina
la distribución y el fondo local: exporta antes cuando sea posible.
