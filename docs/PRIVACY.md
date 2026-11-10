# Privacidad y credenciales

Los respaldos se transfieren directamente entre tu PC y Google Drive mediante HTTPS y autorización OAuth. El programa no envía partidas a un servidor del desarrollador ni incluye telemetría.

Solicita únicamente `drive.appdata`, que limita el acceso al espacio privado de esta aplicación. No pide tu contraseña de Google. El login usa el navegador predeterminado, PKCE S256, state aleatorio y un callback loopback temporal.

En Windows los tokens se cifran con DPAPI para el usuario actual. En Linux/macOS se guardan con permisos de archivo 0600; esa variante no usa un llavero del sistema. No copies la sesión entre usuarios o PCs: conectá la cuenta en cada equipo.

Los ZIP no tienen cifrado propio con contraseña: incluyen los archivos de la partida, sus metadatos y el nombre del equipo. Se almacenan dentro de tu cuenta privada de Drive y usan cifrado de transporte; las exportaciones locales deben tratarse como cualquier otra copia de tus datos.

Desconectar elimina la sesión de esta instalación; no revoca el proyecto entero ni elimina los respaldos remotos. Podés revocar el acceso desde los ajustes de tu cuenta Google. Eliminar una versión de la aplicación borra permanentemente ese archivo de Drive, con confirmación explícita en la interfaz.
