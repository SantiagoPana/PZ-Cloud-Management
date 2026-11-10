# Arquitectura 2.1

Se conserva el motor de archivos de la 1.0 y se reemplazan la interfaz y la ejecución periódica.

| Componente | Función |
| --- | --- |
| ManagerWindow | Dos vistas principales, configuración guiada, modelos pequeños, tareas fuera del hilo de interfaz |
| SignInDialog | Ventana nativa de ingreso, navegador del sistema, retorno automático y cancelación sin sondeo |
| GoogleClientDefaults / PublisherConfig | Identidad de escritorio incluida por el autor al compilar, separada de las sesiones de usuarios |
| BackgroundService | WatchService para solicitudes; sondeo de proceso solo con protección; reintentos solo con pendientes |
| SaveManager | Catálogo lateral compacto, migración, cola, retención y operaciones verificadas |
| ConfiguredCloud | Selecciona y crea el proveedor solo cuando se solicita una operación |
| FolderCloud | ZIP y metadatos independientes en una carpeta sincronizada existente |
| GoogleDrive / GoogleAuth | API directa, cliente HTTP compartido/diferido, OAuth, IDs y sesiones reanudables |
| ArchiveEngine / FilesEx | Integridad de archivos, buffers reutilizados, SHA-256 y MD5 juntos, staging y recuperación |
| GameGuard / SessionMonitor | Proceso observado reutilizado, espera tras cierre, registro durable de salida |
| GameBridge | Catálogo/solicitudes Lua sin exponer tokens ni permitir restaurar un mundo abierto |

## Reposo

El ingreso utiliza el mismo trabajador de la aplicación y un callback loopback temporal. Al terminar, cancelar o vencer la espera se cierra el listener. No se incorpora un motor de navegador ni un servicio de autenticación residente. La sesión conserva el cliente con el que se creó para mantener compatibilidad entre actualizaciones.

Sin protección ni pendientes, no se programan tareas temporizadas. Un hilo daemon duerme esperando eventos del sistema para abrir la ventana o atender al mod; no lee las partidas. La ventana manual se cierra terminando el proceso.

Con protección hay un sondeo cada 15 segundos. Si el proceso ya se identificó, se consulta su handle en vez de enumerar todos los procesos. Las operaciones de respaldo/restauración siguen comprobando que el juego esté cerrado.

No hay refresco periódico de nube. Con pendientes se agenda un envío; si falla, se reintenta con espera de 30, 60, 120 segundos, hasta 10 minutos. Se cancela al deshabilitar el destino. La actividad propia de subida comprueba en bloques si abriste el juego y conserva una sesión pendiente al pausarse.

## Catálogo y memoria

Cada archivo lateral conserva un resumen sin la lista de archivos/directorios del mundo. Los metadatos 1.x se migran al primer uso. Las siguientes consultas abren JSON pequeños y comprueban presencia/tamaño; la integridad completa se verifica antes de subir o restaurar. Listar una copia no equivale a certificar su contenido.

Los objetos de la interfaz no retienen manifiestos completos. Un buffer por hilo se reutiliza para leer archivos. La verificación calcula SHA-256 y MD5 simultáneamente y el proveedor reutiliza el MD5 ya verificado, que compara con el calculado por Drive al recibir los bytes.

## Carpeta sincronizada

Cada versión tiene un UUID: `PZCloud-<id>.zip` más `PZCloud-<id>.json`. Primero se copia/verifica el ZIP temporal, luego se publica el marcador JSON. En otra PC solo se lista si ambos llegaron con tamaño consistente; se valida SHA-256 al descargar/recuperar. No se fusionan versiones ni se reemplaza una colisión con otro contenido.

La aplicación informa entrega a carpeta, nunca confirmación de subida de un cliente externo. Ese cliente administra Internet, cuota, disponibilidad offline y consumo de recursos.

## Integridad y restauración

Se conservan los escaneos/verificaciones de la 1.0: se rechazan enlaces, rutas inseguras, mutaciones durante copia, archivos corruptos y tamaños inconsistentes. Restaurar verifica el ZIP, respalda el mundo actual, extrae a staging y usa journal con recuperación del destino anterior. La retención no elimina el archivo elegido durante una restauración ni las copias pre_restore.

El runtime comprimido incluye solo los módulos requeridos. Arranca con heap de 8 MiB y permite crecer hasta 768 MiB de heap; RAM total incluye además JVM, librerías y memoria nativa. Es un presupuesto de heap, no un límite de RSS. Los trabajos usan un único hilo de baja prioridad. Ver mediciones y sus límites en PERFORMANCE.md.
