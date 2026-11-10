# Primera prueba en Windows

1. En GitHub Desktop, seleccionar `SantiagoPana/PZ-Cloud-Management` y rama `development`; Fetch origin → Pull origin.
2. Instalar JDK 21 si hace falta: `winget install Microsoft.OpenJDK.21`. Abrir una terminal nueva después.
3. Ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1` en el repositorio. Usa mundos ficticios en TEMP y HTTP local. Resultado: `OK: 50 integration tests`.
4. Abrir `Start-PZCloud.cmd`; seleccionar la carpeta correcta de Zomboid en Configuración.
5. Crear una partida descartable, cerrar el juego y usar Respaldar ahora. La copia debe aparecer en el historial.
6. Seguir [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md) para conectar la cuenta. Subir, actualizar nube y descargar en la segunda PC.
7. Restaurar con un nombre nuevo, abrir PZ y probar que carga.

Si se usa el portable de Actions, Java ya viene incluido. La suite desde el código requiere JDK; el portable solo requiere extraer su carpeta completa.

La rama `main` conserva la base anterior. No instalar la aplicación ni el JAR dentro de los archivos del juego. El botón Lua se instala desde Configuración si se desea.
