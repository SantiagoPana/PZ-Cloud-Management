# PZ Cloud Management

Gestor de partidas de **Project Zomboid Build 42** para Windows, con aplicación de escritorio, respaldos locales y sincronización con Google Drive. Autor: Santiago Martin.

**1.0.0-alpha.1 — sistema implementado y probado con partidas ficticias.** La conexión con una cuenta real de Drive, la carga de partidas reales y el menú Lua en B42.22.1 todavía requieren una prueba en el juego. Trabajo en `development`; `main` conserva la base anterior.

## Qué permite hacer

- Descubrir partidas de un jugador y elegir cuáles proteger.
- Crear versiones manuales o automáticas; elegir solo local, Google Drive o ambos.
- Ver todos los puntos de guardado de Drive, descargarlos en otra PC y restaurar la versión elegida.
- Mantener las subidas pendientes sin Internet y continuarlas después, incluso tras reiniciar el gestor.
- Continuar subidas y descargas grandes por bloques sin cargarlas completas en memoria.
- Verificar SHA-256 de cada archivo y del ZIP, detectar cambios durante la copia y rechazar archivos dañados.
- Respaldar el progreso actual antes de restaurar y conservar además la carpeta anterior para recuperación.
- Conservar versiones de PCs distintas sin sobrescribirlas; señalar historias divergentes.
- Importar/exportar respaldos y consultar/descargar desde el botón opcional **Cloud Saves** del juego.

## Empezar en Windows

**Desde el código:** en GitHub Desktop elegí `development`, hacé **Fetch origin → Pull origin**. Instalá JDK 21 una vez (`winget install Microsoft.OpenJDK.21`), abrí una terminal nueva y ejecutá **Start-PZCloud.cmd**. Compila y abre el gestor.

**Portable:** cuando termine el workflow **Test and package** de [Actions](https://github.com/SantiagoPana/PZ-Cloud-Management/actions), descargá el artifact `PZCloudManagement-Windows-x64`, extraelo completo y abrí `PZCloudManagement.exe`. Ese paquete incluye Java; no hace falta instalarlo.

En **Configuración**, revisá la carpeta `Zomboid` y seguí [la conexión de Google Drive](docs/GOOGLE_DRIVE.md). En la segunda PC importá el mismo cliente OAuth y conectá la misma cuenta. [Guía completa de uso](docs/QUICKSTART.md).

## Cuándo se respalda

| Opción | Comportamiento |
| --- | --- |
| Manual | Botón **Respaldar ahora**, con el juego completamente cerrado |
| Al abrir desde el gestor | **Respaldar y abrir Zomboid** hace la copia antes de pedir a Steam que abra el juego |
| Al cerrar Zomboid | El gestor detecta el fin del proceso, espera 10 segundos y respalda las partidas protegidas que cambiaron |
| Ambos | Combina los dos momentos anteriores |

El gestor debe permanecer abierto o en segundo plano. Abrir Zomboid directamente desde Steam permite el respaldo de salida; no puede garantizar una copia previa al inicio de esa sesión. Volver al menú del juego mantiene el proceso abierto: la copia automática espera el cierre completo.

## Alcance

Partidas de un jugador. No incluye servidores dedicados, mundos alojados multijugador, Steam Cloud, copias de un mundo en ejecución ni instalación automática de mods de Workshop. Las dos PCs necesitan builds y mods compatibles. La build mostrada en los respaldos es la declarada en Configuración.

La aplicación Java funciona como proceso externo. El mod Lua es opcional; no se requiere ZombieBuddy ni modificar archivos Java del juego.

## Desarrollo y pruebas

JDK 17+; sin Maven, Gradle ni dependencias de ejecución externas.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Package
```

Linux/macOS: `bash scripts/test.sh`; abrir con `java -jar build/PZCloudManagement.jar`. Windows es el destino principal; el empaquetado portable se hace en Windows.

La suite usa carpetas temporales y un simulador HTTP local de Google OAuth/Drive: no abre partidas reales ni solicita una cuenta. Ver [pruebas](docs/TEST_PLAN.md), [arquitectura](docs/ARCHITECTURE.md), [formato](docs/FORMAT.md), [privacidad](docs/PRIVACY.md) y [estado de la versión](docs/ROADMAP.md).
