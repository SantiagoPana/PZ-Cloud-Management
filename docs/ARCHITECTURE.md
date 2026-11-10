# Arquitectura implementada

## Componentes

| Componente | Responsabilidad |
| --- | --- |
| `PzCloudApp` | Punto de entrada GUI/CLI, registro de actividad y bloqueo de una sola instancia por carpeta de datos |
| `ManagerWindow` | Ventana Swing en español; operaciones de disco/red en un único trabajador externo al hilo de interfaz |
| `Config` | Carpeta Zomboid, identidad del equipo, identidades de mundos y preferencias por partida |
| `ArchiveEngine` | Escaneo, manifiesto, hashes, ZIP, validación, extracción a staging y transacciones de restauración |
| `SaveManager` | Historial, cola durable, retención, importación, descarga, restauración y protección temporal del ZIP elegido |
| `GoogleAuth` / `TokenVault` | OAuth de escritorio PKCE S256 y state; renovación; DPAPI CurrentUser en Windows, permisos 0600 en POSIX |
| `GoogleDrive` | Drive v3 appDataFolder, paginación, IDs preasignados, sesiones de subida persistentes, rangos de descarga, SHA-256/MD5 |
| `GameGuard` / `SessionMonitor` | Detección del proceso de PZ, espera después del cierre y registro durable de una salida pendiente |
| `GameBridge` / mod Lua | Protocolo de archivos fijos en Zomboid/Lua para catálogo, apertura del gestor y descarga; nunca restauración en vivo |

## Flujo de respaldo

1. Rechazar el trabajo si un cliente o servidor PZ está abierto.
2. Comprobar separación de Saves y respaldos; rechazar enlaces/junctions y archivos especiales.
3. Recorrer el mundo, obtener tamaños y hashes; comprobar espacio disponible.
4. Escribir ZIP temporal con manifiesto y contenido, comprobando los hashes de lo leído.
5. Volver a comprobar proceso y contenido del mundo; verificar completamente el ZIP.
6. Publicar el ZIP mediante rename, registrar estado local/pendiente y actualizar el historial.
7. Subir las copias pendientes en orden. Marcar sincronización únicamente después de verificar tamaño y MD5 calculado por Drive, más el SHA-256 del manifiesto remoto.

Si el programa se cierra entre publicar el ZIP y registrar la cola, esta se recupera a partir del destino guardado en el manifiesto. La retención conserva siempre pendientes y copias `pre_restore`. En destino solo Drive conserva una copia local ordinaria después de sincronizar; en ambos/local usa la cantidad configurada.

## Restauración

Validar el archivo completo antes de tocar la partida. Si el destino existe, crear y verificar un respaldo `pre_restore`. Proteger el archivo elegido frente a la retención. Extraer a una carpeta temporal hermana, comprobar el proceso otra vez, guardar un journal, mover el destino anterior a una carpeta de recuperación y colocar la nueva carpeta.

Ante un error entre los dos movimientos se intenta volver a instalar la anterior. Al arrancar con el juego cerrado, los journals incompletos permiten recuperar una carpeta anterior si el destino quedó ausente. Nunca se sobrescribe un destino que ya existe durante la recuperación. Las carpetas anteriores se conservan; no se eliminan mediante retención automática.

Un sistema de archivos puede no soportar rename atómico; el journal y la copia anterior cubren interrupciones entre movimientos, pero no prometen persistencia absoluta ante daño del disco o pérdida de energía.

## Nube y conflictos

Cada versión ocupa un archivo independiente e inmutable. Los metadatos incluyen worldId, snapshotId, parentId, equipo, fecha, build declarada y checksums. Dos hojas de un mismo historial se señalan como divergencia; no hay reemplazo automático ni fusión de archivos de mundos.

Las subidas guardan ID preasignado y URI de sesión. Al reintentar primero consultan el archivo/estado remoto: el caso de confirmación perdida no crea otra versión. Las descargas solo se publican como ZIP después de verificar su SHA-256.

## Integración con el juego

Se eligió un compañero externo en lugar de cargar Java dentro de PZ. Esto permite atender el cierre completo del proceso y evita depender de APIs internas de un cargador. No existe un hook de preentrada para un inicio directo desde Steam. El botón de apertura del gestor hace explícito el respaldo previo; el lanzamiento posterior sigue pasando por Steam.

El mod B42 usa `getFileReader/getFileWriter`, `ISUI` y eventos Lua de menú. Sus llamadas de archivos coinciden con la API documentada; su presentación/ciclo real deben validarse en B42.22.1.

## Límites operativos

Copias completas, no incrementales. Máximo de contenido 256 GiB, manifiesto 64 MiB y dos millones de archivos. Necesita espacio para la copia/staging y conserva el destino anterior en una restauración. El detector de procesos es conservador y se vuelve a consultar antes de publicar/copiar, pero no instala un bloqueo compartido dentro del propio juego: no abras PZ durante una operación.
