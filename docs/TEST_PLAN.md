# Validación

## Suite automatizada

`bash scripts/test.sh` o `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1`.

50 pruebas de integración con mundos ficticios, temporales y un servidor HTTP loopback que reproduce OAuth/Drive. Cubren:

- JSON, Unicode, enteros grandes, nombres portables, traversal y rutas duplicadas.
- Binarios, directorios vacíos, manifiesto, SHA-256, CRC y contenido corrupto con CRC recompuesto.
- Límites de expansión, archivos inesperados, enlaces y directorios de respaldo superpuestos.
- Partidas cambiando durante compresión y proceso PZ que se abre antes de publicar.
- Restauración en otra PC, conservación del estado anterior y recuperación de journals interrumpidos.
- Identidad de mundos, cola recuperable, subidas offline, retención y protección de la versión que se está restaurando.
- Salida automática, reinicio del compañero, respaldo previo al inicio y rechazo de restauraciones desde el mod.
- HTTP real local: subidas parciales aceptadas con respuesta perdida, reanudación, confirmación idempotente, paginación, borrado, rangos, descarga completa sin Range, SHA incorrecto y renovación después de 401.
- OAuth PKCE S256, state inválido, callback loopback, refresh tras reiniciar, invalid_grant, cambio de cliente y cancelación.

La prueba de enlaces puede omitir su fixture en Windows si el sistema no permite crear symlinks. La suite Windows incluye almacenamiento y lectura real con DPAPI CurrentUser.

## Interfaz

`xvfb-run -a -s '-screen 0 1280x900x24' java -cp build/classes:build/test-classes pzcloud.UiSmoke` genera `build/ui-smoke.png` con datos ficticios. Requiere Java con soporte gráfico y Xvfb. El test principal se ejecuta sin entorno gráfico.

## Prueba manual de aceptación

1. Usar una partida descartable cerrada y crear un respaldo. Modificarla jugando, salir completamente y verificar la versión automática.
2. Cortar Internet antes de sincronizar; comprobar Pendiente. Reconectar y verificar que la misma copia pase a En la nube.
3. En otra PC, conectar la misma cuenta y el mismo cliente OAuth; descargar, restaurar con otro nombre y cargar el mundo en una build/mods compatibles.
4. Restaurar una versión anterior sobre la descartable. Verificar tanto `pre_restore` como la carpeta anterior indicada en el registro.
5. Con el gestor abierto, activar el mod Lua en B42 y comprobar Cloud Saves/F8, catálogo y descarga. La restauración debe pedir cerrar el juego.
6. Intentar respaldo/restauración con el juego abierto: ambos deben rechazarse. Cerrar PZ y comprobar la cola automática tras la espera.

Estas pruebas reales todavía no se han ejecutado en este entorno. El simulador verifica el protocolo del cliente; no sustituye una cuenta Google real ni la carga del mundo dentro de PZ.
