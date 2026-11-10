# Knox Net · Seguridad y alertas de antivirus

Knox Net es una aplicación de respaldo para Project Zomboid. Es normal que permanezca ejecutándose en la bandeja **solo cuando el usuario la minimiza expresamente** o al cerrar la ventana con protección automática/envíos pendientes activos. La bandeja ofrece **Abrir Knox Net** y **Salir**; no instala un servicio del sistema ni modifica las exclusiones del antivirus.

## Alertas IDP.Generic y falsos positivos

`IDP.Generic` es una detección heurística de Avast. No demuestra por sí sola que el archivo contenga malware, ni prueba que sea inocuo. Por precaución, analizá el ejecutable de la release oficial y revisá el origen. No deshabilites Avast, no excluyas carpetas ni distribuyas versiones alteradas para evadir el análisis.

El programa utiliza un ejecutable Java creado con `jpackage`, un runtime Java privado, un observador de archivos locales y tareas de respaldo. Son comportamientos legítimos en este proyecto, pero no podemos afirmar qué regla concreta originó la alerta sin un informe de Avast.

1. Descargá exclusivamente desde la [página oficial de releases](https://github.com/SantiagoPana/PZ-Cloud-Management/releases).
2. Compará el SHA-256 del ZIP con `SHA256SUMS.txt` de esa release. En PowerShell: `Get-FileHash -Algorithm SHA256 .\KnoxNet-*.zip`.
3. Actualizá las definiciones de Avast y analizá `KnoxNet.exe` con Avast y, si está habilitado, con Microsoft Defender.
4. Si continúa la alerta, anotá el nombre de detección y el **Alert ID** de Avast. Enviá la muestra original como **False positive** usando la [guía oficial de Avast](https://support.avast.com/avast/en/ww/home/current/solutions/submit-file-url). Avast puede corregir la detección tras revisarla.
5. Si algún motor confirma una amenaza real, interrumpí la distribución y examiná el paquete y su proceso de compilación.

No compartas archivos de sesiones, datos privados de partidas ni tokens OAuth en informes públicos.

## Controles reproducibles en GitHub Actions

- Pruebas automatizadas de las funciones de copia/recuperación y de la interfaz.
- Compilación del ejecutable desde la fuente del repositorio en un runner limpio, con Java integrado.
- Ejecución nativa aislada y verificación de la identidad de Google incorporada.
- `scripts/check-windows-security.ps1`: registra hashes SHA-256 del EXE y JAR, verifica el estado de la firma Authenticode y solicita un escaneo personalizado de Microsoft Defender **solo cuando el antivirus está realmente activo**.
- El resultado se guarda en `SECURITY_STATUS.json` dentro del artefacto del portable. Si el runner no ofrece Microsoft Defender, se registra `not-run`; nunca se presenta como `passed`.
- Una prueba correcta de Defender **no equivale** a la aprobación de Avast, ni reemplaza un análisis de comportamiento con una cuenta real.

## Mejoras de confianza antes de distribuir ampliamente

- Firmar el ejecutable con un certificado Authenticode de una entidad reconocida o un servicio de firma de código para Windows. Un certificado autofirmado no otorga confianza pública.
- Mantener la misma identidad de publicación para futuras versiones, incluir el nombre del editor y conservar sumas SHA-256.
- Publicar el código fuente y un historial claro de versiones, sin ofuscadores ni empaquetadores innecesarios.
- Enviar los falsos positivos confirmados a cada fabricante. No existe una prueba universal que garantice cero alertas en todos los antivirus.

Las firmas y la reputación de SmartScreen son independientes de una alerta de Avast. Una aplicación recién publicada y firmada también puede mostrar advertencias mientras establece reputación.

Referencias oficiales: [Avast, revisión de falsos positivos](https://support.avast.com/avast/en/ww/home/current/solutions/submit-file-url), [Microsoft Defender Start-MpScan](https://learn.microsoft.com/en-us/powershell/module/defender/start-mpscan) y [reputación SmartScreen para desarrolladores](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).
