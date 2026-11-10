# Cómo está hecha PZ Cloud

PZ Cloud es un programa de escritorio escrito en Java. La interfaz usa Swing y el portable de Windows incluye un runtime Java 21 recortado a los módulos necesarios, así que el usuario abre el ejecutable sin instalar Java aparte. El código se compila con las APIs de Java 17.

| Parte | Qué hace |
| --- | --- |
| `ManagerWindow` | Interfaz en español, partidas, búsqueda, versiones y acciones de guardar/recuperar |
| `ArchiveEngine` y `FilesEx` | ZIP por streaming, hashes SHA-256/MD5, validación, restauración temporal y recuperación tras una interrupción |
| `SaveManager` | Catálogo pequeño, historial, retención y cola durable de copias pendientes |
| `GoogleAuth` | OAuth en el navegador, PKCE, callback local, sesión renovable y comprobación del permiso de Drive |
| `GoogleDrive` | API REST de Drive v3, subidas reanudables por bloques y verificación del archivo remoto |
| `TokenVault` | Sesión protegida con DPAPI del usuario de Windows; archivo privado en POSIX |
| `BackgroundService` | Eventos de archivos, un trabajador de baja prioridad y reintentos solo si hay pendientes |
| Mod Lua opcional | Botón dentro de Zomboid y solicitudes al programa externo mediante archivos |

El gestor crea una versión de la partida cuando el juego está cerrado. Conserva una copia local; si elegiste Google, la envía directamente al espacio privado de la aplicación en tu Drive. También admite una carpeta que ya sincronice otra aplicación. Para recuperar, verifica el contenido y prepara la restauración antes de instalarlo; si reemplazás un mundo, respalda su progreso anterior.

La búsqueda trabaja sobre los nombres ya cargados y no vuelve a recorrer los archivos del mundo. La ventana y las tareas de nube se actualizan por acciones/eventos. El modo manual sin pendientes ni protección no programa consultas periódicas de procesos o de nube. Comprimir o restaurar consume recursos durante la operación; las mediciones existentes de la 2.0 y sus límites están en [PERFORMANCE.md](PERFORMANCE.md), y no se presentan como nuevas mediciones de la 2.2.

Google recibe el ingreso en su propio navegador. El programa intercambia la autorización por una sesión, comprueba el acceso al espacio de respaldos y guarda la sesión para renovarla después. Hace falta que el autor registre un cliente OAuth de escritorio de PZ Cloud y habilite Drive API. El código implementado y las pruebas con simulador no crean ese registro ni prueban una cuenta real por sí solos. Ver [AUTHOR_GOOGLE.md](AUTHOR_GOOGLE.md).

El repositorio está dividido en `src/main/java`, `src/test/java`, `mod`, `scripts` y `docs`. GitHub Actions compila, prueba en Windows y Linux, ejecuta los recorridos gráficos y genera el portable. Las pruebas trabajan con partidas ficticias; sigue pendiente la aceptación con la cuenta Google del autor y una partida descartable cargada dentro de Zomboid.


Desde la 2.3, las herramientas del autor solo participan en la compilación. El cliente de usuario usa el recurso incluido y no ofrece importación de archivos ni reemplazos de identidad en ejecución.
