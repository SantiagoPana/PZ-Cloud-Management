# Probar la 2.0 en Windows

1. Descargá `PZCloudManagement-2.0-Windows-x64` de un workflow correcto de Actions y extraelo completo.
2. Abrí **PZCloudManagement.exe**. No hace falta instalar Java.
3. Elegí **Solo en esta PC** para la primera prueba. Seleccioná una partida descartable y cerrá Zomboid.
4. **Guardar partida** debe crear una copia. En **Recuperar partida → En esta PC**, elegila y recuperala con un nombre nuevo.
5. Abrí PZ y comprobá que carga. Después configurá la nube y probá el recorrido con otra PC.

Desde GitHub Desktop: `development` → Fetch origin → Pull origin. Si ejecutás desde el código, instalá JDK 21 y abrí `Start-PZCloud.cmd`. `scripts/test.ps1` ejecuta las 66 pruebas sin tocar partidas reales ni conectar una cuenta.

La prueba gráfica del workflow usa mundos ficticios: selecciona configuración, guarda y recupera haciendo clic en los controles reales.
