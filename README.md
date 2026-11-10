# Knox Net 2.3.5

**PZ Cloud Management** — nombre anterior del gestor y descripción de Knox Net.

**Guardá tu partida. Recuperala en otra PC.** Aplicación de escritorio en español para partidas de un jugador de Project Zomboid B42. Autor: Santiago Martin.

## Inicio de sesión sencillo

La 2.1 agrega la ventana **Iniciar sesión con Google**: abre el selector oficial de cuenta en el navegador, recibe la autorización automáticamente y recuerda la sesión. Permite cancelar, reabrir el navegador y elegir otra cuenta. El recorrido normal ya no pide archivos JSON.

La 2.3 usa exclusivamente la identidad de Google incluida en el instalable. El cliente no ofrece importación de credenciales ni herramientas del autor; tampoco acepta archivos locales, variables de entorno o propiedades de JVM para reemplazarla. Una sesión compatible se conserva. Los respaldos y partidas existentes no se borran.

Las herramientas de compilación y su configuración son para el desarrollador y no se distribuyen con el cliente. [Preparar una compilación](docs/AUTHOR_GOOGLE.md).

## Empezar

**Knox Net 2.3.5 está en pruebas en `development`**. Los ZIP de prueba de Windows y del mod están en [GitHub Actions](https://github.com/SantiagoPana/PZ-Cloud-Management/actions/workflows/build.yml). La [versión pública anterior 2.3.4](https://github.com/SantiagoPana/PZ-Cloud-Management/releases/tag/v2.3.4) sigue disponible. Extraé toda la carpeta del portable y abrí **KnoxNet.exe**; Java y la identidad de aplicación de Google vienen incluidos en el artefacto Windows-Google.

La [release publicada anterior, PZ Cloud 2.3.1](https://github.com/SantiagoPana/PZ-Cloud-Management/releases/tag/v2.3.1), conserva su nombre y ejecutable originales.

1. En **Configuración**, elegí **Solo en esta PC**, una **carpeta de Drive/OneDrive que ya uses**, o **Google Drive directo** si tu edición ya lo trae habilitado.
2. Elegí la partida y pulsá **Guardar partida**. Activá **Guardar automáticamente al cerrar Zomboid** si querés protección automática.
3. En otra PC, configurá la misma carpeta/cuenta y usá **Recuperar partida → En mi carpeta / nube**.

El modo carpeta no necesita crear un proyecto de Google ni importar credenciales. Tu aplicación de sincronización debe terminar de subir los archivos antes de cambiar de PC. El gestor muestra **En tu carpeta**, no asegura una subida que depende de otra aplicación. [Guía sencilla](docs/QUICKSTART.md).

## Idioma de la interfaz (2.3.5)

Knox Net detecta automáticamente el idioma de Windows al iniciar: español para cualquier variante `es-*` y inglés para `en-*` y otros idiomas no disponibles. En la barra superior o al principio de **Configuración** podés elegir **Auto / Automático**, **Español** o **English**. El cambio se aplica al momento y se guarda en la configuración habitual sin modificar partidas ni sesiones de Google.

## Interfaz y consumo

La 2.2 agrega búsqueda por nombre/modo con el catálogo ya cargado, listas de dos líneas, acciones desactivadas cuando no hay una partida/copia elegida y **Enviar pendientes**. Los mensajes distinguen copia local, envío pendiente y destino remoto. No agrega sondeos de red en reposo.

- Dos tareas principales: **Guardar partida** y **Recuperar partida**.
- Configuración inicial guiada y preferencias sensatas; las herramientas técnicas quedan en Más opciones.
- Recuperación con otro nombre por defecto; respaldo previo si elegís reemplazar un mundo existente.
- Catálogo pequeño: ver versiones no descomprime los ZIP.
- Modo manual sin temporizadores de procesos ni consultas periódicas de nube.
- Protección automática con consultas cada 15 segundos y un proceso ya identificado reutilizado.
- Solo se reintenta si hay copias pendientes. Los fallos espacian los reintentos de 30 segundos hasta 10 minutos.
- Subidas propias pausables al detectar que abriste el juego; no se copian mundos abiertos.
- Lecturas con buffers reutilizados; SHA-256 y MD5 se calculan juntos.
- Clientes HTTP creados solo cuando se necesitan y compartidos para Google.
- Runtime portable comprimido, heap inicial pequeño y un solo trabajador de baja prioridad.

## Consumo medido

En una prueba de Linux con OpenJDK 21, mismas partidas ficticias y configuración de lanzamiento de cada versión:

| Medición | 1.0 | 2.0 |
| --- | --- | --- |
| RAM residente al final de 15 s en reposo con la ventana abierta | 111.8 MiB | 82.1 MiB |
| Lecturas durante ese reposo | 240 | 0 |
| Tiempo para 50 consultas del historial | 846 ms | 23 ms |
| Bytes leídos en esas 50 consultas | 19.98 MB | 92.66 KB |

Son mediciones de este entorno, no un límite de RAM ni una promesa para cada PC. Copiar/restaurar partidas grandes necesita más recursos temporalmente. Los clientes externos de Drive/OneDrive tienen su propio consumo. [Metodología, límites y resultados completos](docs/PERFORMANCE.md).

## Compatibilidad y estado

**2.3.5 en preparación**. Conserva las partidas, las copias schema 1, identidades y preferencias de la 1.0. Si ya había una conexión directa con Google, se reutiliza. Las copias anteriores se indexan una sola vez y luego se consulta su resumen pequeño.

88 pruebas automáticas con mundos ficticios y simulador HTTP local, más pruebas gráficas de configurar/guardar/recuperar y de la ventana de inicio de sesión con un simulador local. Ninguna prueba automatizada conecta una cuenta real. Sigue pendiente probar la conexión con una cuenta real y cargar un mundo restaurado en B42.22.1.

Solo un jugador; no servidores dedicados ni partidas multijugador alojadas. Usá las mismas builds y mods para cargar una copia en otra PC. Volver al menú no cierra el proceso: el respaldo automático espera a salir completamente de Zomboid.

El botón **Minimizar a la bandeja** deja Knox Net activo incluso sin copias pendientes; el icono lo reabre y **Salir** lo cierra por completo. **La X de la esquina superior derecha siempre cierra Knox Net por completo**. Si hay un respaldo o restauración en curso, termina esa operación antes de salir. Si el sistema no soporta bandeja, el botón minimiza a la barra de tareas.

## Desde el código

JDK 17+ para compilar el JAR; JDK 21 para el portable comprimido. Rama de trabajo `development`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -Package
```

`Start-KnoxNet.cmd` compila y abre. Linux: `bash scripts/test.sh`; abrir con `java -Xms8m -XX:+UseSerialGC -jar build/PZCloudManagement.jar`.

[Arquitectura](docs/ARCHITECTURE.md) · [Formato](docs/FORMAT.md) · [Google directo](docs/GOOGLE_DRIVE.md) · [Pruebas](docs/TEST_PLAN.md) · [Privacidad](docs/PRIVACY.md).

[Cómo está hecha la aplicación](docs/HOW_BUILT.md) · [Controles de seguridad y Avast](docs/SECURITY.md).

## Rama independiente de Workshop

La rama [`workshop`](https://github.com/SantiagoPana/PZ-Cloud-Management/tree/workshop) es la **fuente del mod editada por el autor**. GitHub Actions la descarga aparte, copia sus archivos solo al espacio de compilación y prepara el ZIP y el mod que incluye el programa. No fusiona ni modifica esa rama. Conserva el icono `cloud.png`, el nombre elegido, el ID `KNManagement` y la estructura de carpeta `Knox Net`.

## Mod opcional para Workshop

El mod **2.3.5** agrega un botón **Knox Net** centrado arriba del menú principal (con margen adaptable), una ventana de gestión y el acceso **F8**. Requiere **Knox Net 2.3.5 o la aplicación anterior PZ Cloud 2.3.1**, abierta en la misma PC y conectada a Google. La app funciona sin el mod; el mod necesita la app para listar y descargar copias. La suscripción de Workshop instala la integración Lua; el programa de Windows se descarga una vez desde la release.

Las traducciones de B42.15+ usan JSON en inglés, español y español de Argentina. La ventana muestra fechas legibles, diferencia el catálogo guardado de uno actualizado, evita solicitudes simultáneas y deja de consultar archivos después de responder o agotar el tiempo de espera. Para recuperar una partida, cerrá Zomboid completamente y usá la aplicación.

El mod aparece como **Knox Net**, con ID `KNManagement` y autor `47`. Knox Net 2.3.5 lo incluye actualizado; la release anterior PZ Cloud 2.3.1 conserva su integración anterior. [Preparar, probar y publicar el mod](docs/WORKSHOP.md).

## Identificación de instalaciones en la 2.3.1

La ventana diferencia Google incluido de Solo copias locales. Una edición nueva no reutiliza silenciosamente otra compilación que ya estaba abierta. El comando `diagnose <archivo>` comprueba el ejecutable/JAR actual sin abrir una cuenta ni tocar la configuración personal. Los artefactos de CI sin cliente se identifican como `Preview-local-only`. La prueba de Windows arranca una copia aislada del ejecutable con su runtime privado y una identidad de prueba incluida, sin contactar a Google ni modificar el portable distribuido.

## Cambio de nombre

Knox Net conserva las carpetas de datos, formato de copias, conexión de Google y protocolo local del gestor anterior. Los nombres técnicos `PZCloudManagement`, `.pzcloud`, el paquete Java `pzcloud` y las claves `UI_PZCloud_*` se conservan para que las instalaciones existentes encuentren sus datos. El nombre elegido para el mod, `KNManagement`, se conserva según la configuración del autor. [Detalles del cambio de marca](docs/BRANDING.md).
