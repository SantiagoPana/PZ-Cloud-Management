# Estado 2.3.1-alpha.1

## Implementado y probado

- [x] Cliente de usuario sin importación de credenciales ni herramientas de autor.
- [x] Identidad de Google incluida exclusivamente en la compilación, con sesiones compatibles conservadas.

- [x] Búsqueda sin escanear el mundo, listas de dos líneas y acciones según la selección.
- [x] Envío manual de pendientes y mensajes claros de destino y cola.
- [x] Comprobación de permisos de Drive después del ingreso y al reutilizar sesión.

- [x] Ventana de ingreso a Google con retorno automático, cancelación y reutilización de sesión.
- [x] Configuración de Google incluida por el autor, sin archivo JSON en el recorrido normal.
- [x] Compilación con requisito explícito de cliente y estado honesto cuando no está configurado.

- [x] Interfaz con dos acciones principales y configuración sencilla.
- [x] Copias locales, carpeta sincronizada y Google Drive directo.
- [x] Historial compacto y migración desde los respaldos de la 1.0.
- [x] Trabajo por eventos, reposo manual sin sondeos y reintentos con espera creciente.
- [x] Buffers reutilizados, dos hashes en una lectura y clientes HTTP diferidos.
- [x] Cola durable, transferencias reanudables, restauración con respaldo previo y recuperación de interrupciones.
- [x] 88 pruebas de integración y flujo gráfico de configurar/guardar/recuperar.
- [x] Comparación reproducible de consumo con la 1.0.
- [x] Workflow de Windows/Linux y portable Windows con Java comprimido.

## Validación externa pendiente

- [ ] Proporcionar la identidad OAuth registrada de PZ Cloud y generar la edición Google habilitada.

- [ ] Sincronización real con Drive/OneDrive y conexión OAuth real.
- [ ] Cargar una copia restaurada en B42.22.1 con sus mods.
- [ ] Probar el menú Lua con otros mods en el Windows 11 del usuario.
- [ ] Sesiones largas, suspensión y partidas reales muy grandes.

Incrementales, proveedores adicionales y servidores siguen fuera del alcance de esta versión.
