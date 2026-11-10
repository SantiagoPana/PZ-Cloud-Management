# Knox Net: integración para Workshop 2.3.5

**PZ Cloud Management**. Nombre del mod: `Knox Net`; ID elegido por el autor: `KNManagement`; autor: `47`.

La app de escritorio realiza los respaldos, inicia sesión con Google y mueve las copias a la nube. Puede usarse sin el mod. El mod añade una ventana de consulta/descarga al menú principal del juego; necesita que Knox Net 2.3.5 (o la app anterior PZ Cloud 2.3.1) siga abierta en la misma PC y conectada. El botón **Mostrar aplicación** pide mostrar una instancia que ya está ejecutándose; no puede iniciar un programa cerrado. **Descargar aplicación** abre la release pública en el navegador.

Workshop distribuye el mod Lua. El jugador descarga la app de Windows una vez desde [las releases del proyecto con Google y Java incluidos](https://github.com/SantiagoPana/PZ-Cloud-Management/releases/). No importa archivos de credenciales. Para descargar desde el juego, mantené la ventana de la app abierta; si tenés protección automática o envíos pendientes, también puede quedar en bandeja.

La aplicación de escritorio **2.3.5** ofrece idioma automático según Windows y selección manual de español/inglés. El mod ya dispone de traducciones JSON EN/ES/AR, determinadas por el juego.

## Cambios de esta integración

- Traducciones JSON para B42.15+, en EN/ES/AR, y texto inglés de respaldo si falta un idioma.
- Lista con nombre de partida, fecha sin fracciones de segundo, zona horaria, modo, PC y tamaño; máximo 500 copias del catálogo más reciente.
- Estados distintos para catálogo guardado, actualización completada, descarga finalizada, error y falta de respuesta.
- Botones de solicitud bloqueados durante una operación y descarga desactivada hasta tener un catálogo actualizado y una selección.
- Conservación de la solicitud pendiente al cerrar/reabrir la ventana o cambiar la resolución.
- Botón **Knox Net** centrado horizontalmente arriba del menú, con margen adaptable de 20 a 42 px en resoluciones habituales y ajuste automático al cambiar resolución.
- Textos y botones ajustados al ancho y al tamaño de fuente. Imagen verde incluida para el menú Mods y Workshop.
- Sin consultas de nube en Lua, sin acceso a mundos abiertos y sin consultas de archivos en reposo. Mientras hay una solicitud se lee un archivo de estado como máximo una vez por segundo. Se detiene al finalizar o agotar el plazo: 30 segundos para listar/mostrar la app y 5 minutos para descargar. Una descarga grande puede seguir ejecutándose en la app después de ese plazo; revisala allí.

La recuperación se hace en la app con Zomboid totalmente cerrado. La copia descargada no aparece automáticamente como mundo jugable. Solo partidas de un jugador; las builds y mods del mundo deben coincidir entre PC.

## Paquete de publicación

```sh
python3 scripts/test-mod.py
python3 scripts/package-workshop.py
```

Las pruebas requieren Lua 5.1. El segundo comando genera `build/workshop/KnoxNet-Workshop-2.3.5.zip` con `Contents/mods/PZCloudManagement/42/`, `common/`, `preview.png`, `workshop.txt` y `README_PUBLICAR.txt`. El workflow **Test and package** también ofrece el ZIP como artefacto **KnoxNet-Workshop-2.3.5**. Contiene solo el mod y los textos de publicación; no incluye ejecutables, credenciales ni sesiones personales.

1. Cerrá el juego. Para probar localmente, colocá la carpeta `Contents/mods/PZCloudManagement` en tu carpeta real `Zomboid/mods/`. Reemplazá por completo la versión local anterior.
2. Activá el mod y reiniciá Zomboid B42.22.1 para cargar las traducciones. No mantengas simultáneamente una copia local y otra de Workshop con el mismo ID.
3. Para publicarlo, copiá la carpeta `KnoxNet-Workshop` completa dentro de `Zomboid/Workshop/` y seleccionála en **Workshop → Crear y actualizar artículos**.
4. Revisá imagen, título, descripción y visibilidad. Conservá el aviso de que necesita la app externa. Steam asignará el ID del artículo: conservá ese ID para futuras actualizaciones.

La app anterior PZ Cloud 2.3.1 incluye la integración anterior; Knox Net 2.3.5 incluye este mod actualizado. **No uses Instalar botón en el juego desde esa app después de instalar el mod 2.3.5**, porque sobreescribiría los archivos nuevos. El instalador del código actualizado sí instala JSON y elimina únicamente los dos archivos de traducción obsoletos del mod.

## Verificación antes de ponerlo público

Las pruebas automáticas ejecutan el Lua real con una interfaz y archivos simulados. Cubren respuestas, errores, tiempos de espera, pausa de consultas, resolución, idiomas y solicitudes duplicadas. Otra prueba instala los recursos del JAR de consumo en una carpeta Zomboid temporal. No sustituyen cargar el mod dentro del juego.

Antes de publicar en Steam Workshop, verificá en B42.22.1 que el botón aparezca centrado arriba sin tapar controles vanilla, los textos en español/inglés, selección y desplazamiento de la lista, interfaz con fuente grande y cambio de resolución, Actualizar, Descargar copia, Mostrar aplicación y Descargar aplicación. Probá con la app cerrada y con una descarga grande. Luego cerrá el juego, restaurá una copia con **otro nombre** desde la app y cargá ese mundo con sus mods. Queda pendiente esa validación real en el juego y con Google; el paquete se entrega preparado, sin publicarlo automáticamente en Steam Workshop. El fichero `workshop.txt` conserva `id=` vacío hasta que Steam asigne el ID del artículo.

Referencias de compatibilidad: [cambio oficial a traducciones JSON y soporte para preview 512×512 en B42.15](https://theindiestone.com/forums/topic/92435-42150-unstable-released/) y [API oficial Lua `openURl`](https://www.projectzomboid.com/modding/zombie/Lua/LuaManager.GlobalObject.html).
