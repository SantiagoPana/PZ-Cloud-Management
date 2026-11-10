# Knox Net · PZ Cloud Management

- Nombre visible: **Knox Net**.
- App 2.3.5: selector de idioma Auto / Español / English; el idioma automático usa el idioma del sistema.
- Descripción: **PZ Cloud Management**.
- Versión preparada: **2.3.5**, aplicación e integración del juego.
- Portable de Windows: `KnoxNet.exe`, dentro de la carpeta `KnoxNet`.
- Mod: nombre `Knox Net`, ID `KNManagement`, autor `47`.
- Workshop: `Knox Net - PZ Cloud Management [B42]`.

El cambio se aplica a las ventanas, bandeja, mensajes, informe de instalación, metadatos del ejecutable, traducciones EN/ES/AR y paquete para Workshop. `Start-KnoxNet.cmd` es el lanzador desde el código; `Start-PZCloud.cmd` queda como acceso compatible para atajos existentes. El icono verde de la Z se conserva.

## Compatibilidad con instalaciones anteriores

La aplicación sigue usando `%LOCALAPPDATA%/PZCloudManagement` en Windows, `.pzcloud` en POSIX y la propiedad `pzcloud.home` cuando está configurada. Conserva el mismo bloqueo de instancia, configuración, archivos de sesión, identificadores de mundo y formato de respaldos. El JAR interno sigue llamándose `PZCloudManagement.jar`; los archivos locales del puente siguen usando el prefijo `pzcloud-`. Esos nombres técnicos no se muestran como marca del programa.

Se conserva el cliente de Google registrado y sus permisos. Así las instalaciones pueden reutilizar sesiones compatibles y acceder al mismo espacio de respaldos. El cambio de marca del binario no cambia por sí mismo el nombre de la pantalla de consentimiento en Google Cloud. El proyecto y la web de verificación conservan sus direcciones actuales.

El ID `KNManagement` conserva la elección del autor en `mod.info`. Si antes usabas el ID `PZCloudManagement`, desactivá la entrada vieja y activá **Knox Net** en el menú Mods. La carpeta interna del mod sigue llamándose `PZCloudManagement`, para actualizar la instalación local en el mismo lugar. No mantengas dos copias locales/Workshop con el mismo ID.

## Distribución preparada

GitHub Actions genera `KnoxNet-2.3.5-Windows-Google` cuando el cliente de Google del autor está incluido, y `KnoxNet-Workshop-2.3.5` para el mod. Las ediciones sin conexión incluida se identifican como `Preview-local-only`.

La release `v2.3.5` distribuye el ejecutable nuevo y un ZIP de Workshop listo para cargar desde el juego. Las releases `v2.3.1` y `v2.3.2` no se modifican. Publicar el ZIP en GitHub no publica automáticamente el artículo en Steam Workshop: ese paso requiere la sesión del autor en Steam.
