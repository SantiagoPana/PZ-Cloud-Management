# Usar PZ Cloud 2.0

## Primera vez

Extraé la aplicación portable completa y abrí **PZCloudManagement.exe**. En Configuración elegí una de estas opciones:

| Opción | Qué necesitás |
| --- | --- |
| Solo en esta PC | Nada más; crea respaldos locales |
| Carpeta de Drive u OneDrive que ya uso | Elegir una carpeta que tu aplicación de nube ya sincronice |
| Google Drive directo | Tu conexión de la 1.0 o [configurar una conexión de Google](GOOGLE_DRIVE.md) |

Para el modo carpeta, el gestor prepara una subcarpeta `PZCloudManagement` en el lugar elegido. En la otra PC elegí el mismo lugar dentro de esa cuenta. No copies la carpeta de partidas activas directamente sobre la nube: el gestor guarda versiones verificadas por separado.

## Guardar

Seleccioná el mundo en **Mis partidas** y pulsá **Guardar partida**, con Zomboid cerrado. La copia local se verifica primero; si configuraste otro destino, se envía después. Si falla Internet o la carpeta no está disponible, conserva la copia pendiente.

**En Google Drive** significa que la API confirmó la subida. **En tu carpeta** significa que el archivo está listo allí; esperá a que Drive/OneDrive confirme su propia sincronización antes de cambiar de PC. El gestor no puede comprobar el estado interno de otra aplicación.

## Guardar automáticamente

Activá **Guardar automáticamente al cerrar Zomboid** para cada partida que quieras proteger. Dejá el gestor abierto o en bandeja. La copia se hace al cerrar el juego por completo, después de una espera de seguridad.

Abrir el juego desde Steam funciona para el respaldo de salida. El botón **Jugar** también respeta los respaldos previos que hubieras configurado en la 1.0. No se hacen copias de mundos mientras están abiertos.

## Recuperar en otra PC

1. Abrí el gestor y configurá la misma carpeta o cuenta.
2. **Recuperar partida → En mi carpeta / nube → Actualizar lista**.
3. Elegí la copia por nombre, fecha y equipo y pulsá **Recuperar esta partida**.
4. Por defecto se agrega “(recuperada)” al nombre para conservar ambas partidas. Si elegís un nombre existente, primero respalda su progreso actual.
5. Abrí Zomboid y buscala en **Cargar partida**. Necesitás la misma versión y los mismos mods; Workshop no se instala automáticamente.

Las fechas se muestran en la hora local de tu PC. Las versiones divergentes no se fusionan ni se elige una automáticamente.

## Cerrar y consumo

En modo manual sin envíos pendientes, cerrar la ventana termina la aplicación. Con protección o envíos activos, la ventana se libera y el gestor sigue en bandeja. Podés volver a abrirlo con el icono o iniciándolo otra vez. **Salir** en la bandeja detiene todo.

No consulta el catálogo remoto a intervalos: lo hace al entrar a Recuperar o al pulsar Actualizar. Los envíos fallidos se reintentan cada vez menos seguido. El modo carpeta depende de otra aplicación de nube y de su consumo; usalo si ya la utilizás.

## Otras opciones

**Versiones anteriores** muestra respaldos locales. En Recuperar, **Más opciones** permite exportar o eliminar una copia; la partida actual se conserva. **Importar copia** acepta los ZIP de la 1.0 y la 2.0.

En **Configuración → Más opciones** podés cambiar la carpeta Zomboid, instalar el botón opcional del juego, abrir los respaldos y desconectar Google. Si usás `-cachedir`, elegí esa carpeta real. Al cambiarla, reiniciá el gestor para actualizar la conexión con el mod.
