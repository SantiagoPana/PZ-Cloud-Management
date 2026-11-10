# PZ Cloud 2.0

**Guardá tu partida. Recuperala en otra PC.** Aplicación de escritorio en español para partidas de un jugador de Project Zomboid B42. Autor: Santiago Martin.

## Empezar

Descargá `PZCloudManagement-2.0-Windows-x64` del último workflow correcto de [Actions](https://github.com/SantiagoPana/PZ-Cloud-Management/actions), extraelo completo y abrí **PZCloudManagement.exe**. Java viene incluido.

1. En **Configuración**, elegí **Solo en esta PC**, una **carpeta de Drive/OneDrive que ya uses**, o tu conexión **Google Drive directo**.
2. Elegí la partida y pulsá **Guardar partida**. Activá **Guardar automáticamente al cerrar Zomboid** si querés protección automática.
3. En otra PC, configurá la misma carpeta/cuenta y usá **Recuperar partida → En mi carpeta / nube**.

El modo carpeta no necesita crear un proyecto de Google ni importar credenciales. Tu aplicación de sincronización debe terminar de subir los archivos antes de cambiar de PC. El gestor muestra **En tu carpeta**, no asegura una subida que depende de otra aplicación. [Guía sencilla](docs/QUICKSTART.md).

## Qué cambió en la 2.0

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

**2.0.0-alpha.1**. Conserva las partidas, las copias schema 1, identidades y preferencias de la 1.0. Si ya había una conexión directa con Google, se reutiliza. Las copias anteriores se indexan una sola vez y luego se consulta su resumen pequeño.

66 pruebas automáticas con mundos ficticios y simulador HTTP local, más una prueba gráfica que hace clic en configurar, guardar y recuperar. Sigue pendiente probar la conexión con una cuenta real y cargar un mundo restaurado en B42.22.1.

Solo un jugador; no servidores dedicados ni partidas multijugador alojadas. Usá las mismas builds y mods para cargar una copia en otra PC. Volver al menú no cierra el proceso: el respaldo automático espera a salir completamente de Zomboid.

Cuando cerrás la ventana, el gestor sigue en bandeja si hay protección automática o envíos pendientes configurados. **Salir** en la bandeja lo detiene por completo. Sin ese trabajo pendiente, cerrar la ventana termina la aplicación.

## Desde el código

JDK 17+ para compilar el JAR; JDK 21 para el portable comprimido. Rama de trabajo `development`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -Package
```

`Start-PZCloud.cmd` compila y abre. Linux: `bash scripts/test.sh`; abrir con `java -Xms8m -XX:+UseSerialGC -jar build/PZCloudManagement.jar`.

[Arquitectura](docs/ARCHITECTURE.md) · [Formato](docs/FORMAT.md) · [Google directo](docs/GOOGLE_DRIVE.md) · [Pruebas](docs/TEST_PLAN.md) · [Privacidad](docs/PRIVACY.md).
