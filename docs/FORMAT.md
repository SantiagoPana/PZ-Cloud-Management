# Datos y formato

Windows usa `%LOCALAPPDATA%\PZCloudManagement`; POSIX usa `~/.pzcloud`. `-Dpzcloud.home=...` permite cambiarlo. La carpeta debe estar separada del árbol de las partidas.

| Ruta relativa | Contenido |
| --- | --- |
| `config.json` | Carpeta Zomboid, build declarada, equipo y opciones por modo/nombre de partida |
| `backups/<snapshotId>.zip` | Versión completa e inmutable del mundo |
| `backups/<snapshotId>.meta.json` | SHA-256, estado local/pendiente/sincronizado, ID remoto y último error |
| `sessions/<snapshotId>.json` | ID de Drive preasignado y URI de subida mientras está pendiente |
| `google-client.json` | Cliente OAuth de escritorio importado |
| `google-session.token` | Tokens de sesión; DPAPI CurrentUser en Windows, permisos 0600 en POSIX |
| `session-state.json` | Registro de sesión PZ que espera respaldo al salir |
| `activity.log` | Operaciones/errores, con rotación a `activity.previous.log`; no contiene tokens |

## ZIP schema 1

Raíz `pzcloud-manifest.json` y archivos de partida bajo `save/`. Se preservan subdirectorios y directorios vacíos. El manifiesto contiene schema, snapshotId, worldId, parentId, nombre/modo, fecha UTC, equipo, build declarada, motivo, destino, totalBytes, fingerprint y lista de archivos con ruta relativa, tamaño, mtime y SHA-256.

El SHA-256 del ZIP completo se conserva en el catálogo local y en metadatos de Drive. Drive calcula además MD5 del contenido remoto y se compara al finalizar la subida. La extracción comprueba tamaños, CRC y hashes por archivo. No se admiten rutas absolutas, `..`, colisiones de mayúsculas ni nombres incompatibles con Windows.

Cada modo/nombre tiene un UUID estable. Restaurar desde otra PC conserva worldId; cada nuevo respaldo crea otro snapshotId. La huella de contenido excluye timestamps para no crear versiones automáticas cuando solo cambió una fecha de archivo.

## Recuperación de restauraciones

En `Saves/<modo>/` se crean temporalmente `.pzcloud-stage-<uuid>`, `.pzcloud-previous-<uuid>` y `.pzcloud-restore-<uuid>.json`. El journal registra prepared → previous_moved → complete. El destino anterior conserva todos sus archivos y sigue siendo recuperable. Las carpetas anteriores y copias `pre_restore` no se borran automáticamente.

Si el sistema se interrumpe entre movimientos, arrancar el gestor con el juego cerrado o ejecutar `recover` recupera el anterior cuando falta el destino. Un journal complete nunca vuelve a instalar un mundo eliminado posteriormente por el usuario.

## Protocolo Lua

En `Zomboid/Lua/`: `pzcloud-request.txt` con `1|nonce|OPEN`, `1|nonce|LIST` o `1|nonce|DOWNLOAD|fileId`; `pzcloud-status.txt` con respuesta al mismo nonce; `pzcloud-catalog.txt` con filas `fileId|nombre|modo|fecha|equipo|tamaño`. No hay comando de restauración, ruta de destino ni ejecución de comandos. Los nonces procesados persisten para evitar repetir una solicitud al reiniciar el gestor.
