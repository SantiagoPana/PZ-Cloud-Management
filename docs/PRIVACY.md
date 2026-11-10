# Privacidad y credenciales

En Google Drive directo, los respaldos se transfieren entre tu PC y Google mediante HTTPS y OAuth. En modo carpeta, el gestor escribe copias verificadas en el lugar que elegiste; su sincronización y permisos dependen de tu aplicación de nube. El programa no envía partidas a un servidor del desarrollador ni incluye telemetría.

El modo directo solicita únicamente `drive.appdata`, que limita el acceso al espacio privado de esta aplicación. No pide tu contraseña de Google. Ese login usa el navegador predeterminado, PKCE S256, state aleatorio y un callback loopback temporal.

En Windows los tokens se cifran con DPAPI para el usuario actual. En Linux/macOS se guardan con permisos de archivo 0600; esa variante no usa un llavero del sistema. No copies la sesión entre usuarios o PCs: conectá la cuenta en cada equipo.

Los ZIP no tienen cifrado propio con contraseña: incluyen los archivos de la partida, sus metadatos y el nombre del equipo. Se almacenan dentro de tu cuenta privada de Drive y usan cifrado de transporte; las exportaciones locales deben tratarse como cualquier otra copia de tus datos.

Desconectar elimina la sesión de esta instalación; no revoca el proyecto entero ni elimina los respaldos remotos. Podés revocar el acceso desde los ajustes de tu cuenta Google. Eliminar una versión de la aplicación borra permanentemente ese archivo de Drive, con confirmación explícita en la interfaz.

El modo local y el modo carpeta no crean un cliente HTTP. El gestor no registra líneas de comandos de otros programas ni envía información de procesos; las consultas locales se usan para reconocer el estado de Zomboid.

La ventana de inicio de sesión no recibe un nombre de usuario ni una contraseña. Abre el navegador del sistema y recibe un código de autorización mediante un listener local temporal que se cierra al terminar, cancelar o vencer. La sesión anterior se conserva si se cancela un cambio de cuenta. La configuración pública del cliente de escritorio puede venir incluida por el autor; eso no incluye la sesión personal del autor.

La información de instalación muestra versión, ruta del programa, huella y estado de conexión incluida. No incorpora secretos ni tokens. Solo se guarda un informe de diagnóstico cuando se solicita; revisá las rutas locales antes de compartirlo.
