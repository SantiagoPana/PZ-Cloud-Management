# Estado 2.0.0-alpha.1

## Implementado y probado

- [x] Interfaz con dos acciones principales y configuración sencilla.
- [x] Copias locales, carpeta sincronizada y Google Drive directo.
- [x] Historial compacto y migración desde los respaldos de la 1.0.
- [x] Trabajo por eventos, reposo manual sin sondeos y reintentos con espera creciente.
- [x] Buffers reutilizados, dos hashes en una lectura y clientes HTTP diferidos.
- [x] Cola durable, transferencias reanudables, restauración con respaldo previo y recuperación de interrupciones.
- [x] 66 pruebas de integración y flujo gráfico de configurar/guardar/recuperar.
- [x] Comparación reproducible de consumo con la 1.0.
- [x] Workflow de Windows/Linux y portable Windows con Java comprimido.

## Validación externa pendiente

- [ ] Sincronización real con Drive/OneDrive y conexión OAuth real.
- [ ] Cargar una copia restaurada en B42.22.1 con sus mods.
- [ ] Probar el menú Lua con otros mods en el Windows 11 del usuario.
- [ ] Sesiones largas, suspensión y partidas reales muy grandes.

Incrementales, proveedores adicionales y servidores siguen fuera del alcance de esta versión.
