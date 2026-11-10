# Estado 1.0.0-alpha.1

## Implementado

- [x] Aplicación de escritorio y CLI.
- [x] Identificación de mundos, preferencias por partida y modos manual/automático.
- [x] Snapshots con SHA-256, CRC, detección de cambios y publicación temporal.
- [x] Restauración con respaldo previo, staging, journal y recuperación.
- [x] Cola durable, retención, importación/exportación y una instancia por equipo.
- [x] Detección de cierre del proceso y respaldo previo al lanzamiento desde el gestor.
- [x] Google OAuth PKCE/state/loopback, renovación y almacenamiento de sesión.
- [x] Drive real mediante API: listar, subir, reanudar, descargar y eliminar versiones.
- [x] Historiales divergentes sin sobrescritura automática.
- [x] Mod Lua opcional y traducciones EN/ES.
- [x] Suite de integración, build reproducible y workflow de portable Windows.

## Validaciones pendientes para una versión estable

- [ ] Conectar una cuenta Google real con su cliente OAuth y completar ida/vuelta entre dos PCs.
- [ ] Cargar una partida descartable restaurada dentro de B42.22.1.
- [ ] Validar botón y panel Lua con el menú de la build objetivo y otros mods de menú.
- [ ] Probar procesos, suspensión, Steam y cierre abrupto en el Windows 11 del usuario.
- [ ] Medir partidas grandes reales, discos lentos y cuota de Drive.

## Mejoras futuras

Respaldos incrementales/deduplicación, más proveedores y soporte específico de servidores. No forman parte de esta versión.
