# Compatibilidad con el prototipo

`LocalBackupEngine.snapshot(Path, Path)` y `BackupCli <saveDir> <backupDir>` siguen disponibles para las pruebas antiguas, pero ahora delegan en el motor completo de manifiestos e integridad.

El formato cambió: el contenido del mundo está bajo `save/` y `pzcloud-manifest.json` describe los archivos. No extraer el ZIP completo directamente dentro de una partida; usar la restauración del gestor.

`scripts/smoke-test.ps1` es un alias de la suite completa `test.ps1`. El prototipo anterior ya no es la interfaz principal. Ver [README](../README.md) y [formato](FORMAT.md).
