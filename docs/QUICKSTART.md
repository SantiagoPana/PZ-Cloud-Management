# Usar PZ Cloud Management

## Primera PC

1. Abrí el portable `PZCloudManagement.exe`, o `Start-PZCloud.cmd` desde el repositorio con JDK instalado.
2. En **Configuración**, la carpeta elegida debe ser `C:\Users\TU_USUARIO\Zomboid`, la que contiene `Saves`; no la carpeta de instalación de Steam. Si usás `-cachedir`, elegí esa carpeta real.
3. Conectá Drive siguiendo [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md).
4. En **Mis partidas**, seleccioná el mundo y abrí **Opciones de partida**. Elegí destino y, si querés, respaldos automáticos al cerrar o ambos.
5. Con el juego cerrado, **Respaldar ahora** crea y verifica una versión. Si Drive está conectado y el destino lo incluye, intenta subirla; si falla, permanece pendiente.
6. **Google Drive → Actualizar nube** muestra las versiones guardadas. **Subir pendientes** fuerza un reintento; el gestor también lo intenta cada minuto cuando hay sesión conectada.

Podés cerrar la ventana eligiendo **Segundo plano**. El icono de bandeja o volver a abrir la aplicación muestra la misma instancia. Elegir **Salir** detiene también la automatización.

## Continuar en otra PC

1. Instalá/abrí el gestor e importá el mismo cliente OAuth de escritorio.
2. Conectá la misma cuenta Google y verificá tu carpeta Zomboid.
3. Actualizá la nube y seleccioná la versión por partida, fecha y equipo.
4. **Descargar y restaurar…** verifica el archivo, permite poner otro nombre y, tras aceptar, instala el mundo con el juego cerrado.
5. Abrí PZ y cargá la partida. Usá una build y un conjunto de mods compatibles. `mods.txt` se conserva cuando existe, pero los archivos de los mods no se instalan automáticamente.
6. Al terminar, salí completamente de PZ y dejá que el gestor respalde y sincronice. Antes de cambiar de PC, comprobá **En la nube** y actualizá el catálogo del otro equipo.

No se descarga ni restaura automáticamente una versión remota por encima del progreso local. Si ambas PCs avanzaron separadamente, quedan ambas versiones y se señala **Versiones divergentes**.

## Volver a un punto anterior

Seleccioná una versión del historial o de Drive y pulsá **Restaurar…**. Mantener el nombre reemplaza ese mundo después de guardar un respaldo `pre_restore`; cambiarlo crea una copia jugable independiente. También se conserva la carpeta anterior, cuya ubicación aparece en el registro. Los archivos exclusivos del estado nuevo no se mezclan con la versión antigua.

Los respaldos pendientes nunca se eliminan por retención. Drive conserva todas las versiones hasta que uses **Eliminar de Drive**. Las copias `pre_restore` y carpetas de recuperación se conservan para revisión manual.

## Botón dentro del juego

Con PZ cerrado: **Configuración → Instalar botón Cloud Saves**. Activá `PZCloudManagement` en Mods de B42 y dejá el gestor abierto. El menú principal tendrá **Cloud Saves**; también se solicita el panel con F8 en el menú.

El panel permite consultar y descargar las versiones. Para restaurarlas hay que cerrar completamente el juego y elegir la versión en el gestor. No hace falta ZombieBuddy. La integración de menú sigue pendiente de validación dentro de B42.22.1.

## Herramientas por consola

```powershell
java -jar build/PZCloudManagement.jar list
java -jar build/PZCloudManagement.jar backup Sandbox "Mi partida"
java -jar build/PZCloudManagement.jar history
java -jar build/PZCloudManagement.jar login "C:\ruta\cliente-oauth.json"
java -jar build/PZCloudManagement.jar sync
java -jar build/PZCloudManagement.jar cloud
java -jar build/PZCloudManagement.jar download ID_DE_DRIVE
java -jar build/PZCloudManagement.jar restore "C:\ruta\respaldo.zip" "Otra partida"
```

Para reemplazar un nombre existente en consola agregá `--replace`. `recover` procesa transacciones interrumpidas con el juego cerrado. `install-mod` instala el mod incluido; `logout` elimina la sesión local. Cerrá el gestor antes de usar la CLI. `-Dpzcloud.home=C:\otra\carpeta` antes de `-jar` permite elegir otra carpeta de datos; debe estar fuera de Saves.
