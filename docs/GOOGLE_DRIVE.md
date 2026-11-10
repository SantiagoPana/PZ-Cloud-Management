# Iniciar sesión con Google

En una edición habilitada por el autor:

1. Pulsá **Iniciar sesión con Google** en la ventana principal.
2. Se abre una ventanita de PZ Cloud y la página oficial de Google en tu navegador.
3. Elegí tu cuenta y autorizá el acceso a tus respaldos.
4. La ventanita se cierra al terminar y el gestor muestra **Google Drive conectado**. La sesión queda guardada en esta PC.

No se escribe la contraseña dentro de PZ Cloud. Mientras espera la autorización, el botón principal sigue activo como **Volver a abrir Google** y reutiliza el mismo intento. Si el navegador no abre, **Copiar enlace** permite continuar en otro navegador de esta misma PC. **Cancelar** detiene el intento; al cambiar de cuenta conserva la conexión anterior si no terminaste.

Para usar la conexión guardada, elegí **Usar esta conexión**. **Elegir otra cuenta** vuelve a mostrar el selector de Google. En otra PC iniciá sesión con la misma cuenta; no copies la sesión cifrada entre equipos.

## Si la ventana dice que el autor debe habilitar Google

Esa edición todavía no contiene la identidad registrada de PZ Cloud. No es un problema de tu cuenta. El autor debe preparar un instalable configurado siguiendo [AUTHOR_GOOGLE.md](AUTHOR_GOOGLE.md); es un paso único de la aplicación, no algo que cada usuario deba configurar.

**Iniciar sesión con Google** permanece accesible: al pulsarlo vuelve a comprobar la configuración y explica qué falta, con **Ayuda para conectar** para abrir los pasos del autor. Activar el botón no reemplaza ese registro ni crea una sesión de Google sin autorización.

Mientras tanto siguen funcionando los respaldos locales y el modo carpeta sincronizada. La aplicación no presenta una conexión como exitosa sin recibir y guardar los tokens de Google.

## Acceso y datos

Se solicita únicamente `drive.appdata`: los respaldos quedan en el espacio privado de esta aplicación en tu Drive. Se ven desde **Recuperar partida → En mi carpeta / nube**, no como una carpeta común de la web de Drive. El almacenamiento pertenece a tu cuenta.

La sesión usa DPAPI CurrentUser en Windows y permisos 0600 en POSIX. Desconectar elimina la sesión local, no tus respaldos remotos. Si Google revoca o vence la sesión, podés entrar otra vez desde la misma ventana.

La autorización usa navegador del sistema, PKCE S256, state aleatorio y retorno loopback temporal. No agrega un motor web residente al gestor. [Documentación oficial de Google para aplicaciones de escritorio](https://developers.google.com/identity/protocols/oauth2/native-app).

La ventana y el protocolo están probados con un simulador HTTP local. Registrar la aplicación y probar una cuenta real siguen siendo pasos externos necesarios.
