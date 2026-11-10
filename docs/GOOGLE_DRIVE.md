# Conectar Google Drive

La aplicación implementa la conexión directa con Google Drive. Necesita un cliente OAuth de escritorio de tu proyecto Google: no se incluyen credenciales ajenas ni un servidor central. El inicio de sesión y el consentimiento se hacen en el navegador del usuario.

## Configuración inicial (una vez por proyecto)

1. Abrí [Google Cloud Console](https://console.cloud.google.com/) y creá o elegí un proyecto para PZ Cloud Management.
2. Habilitá [Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com).
3. En Google Auth Platform configurá el nombre de la aplicación y los datos de contacto. Para una cuenta personal, usá audiencia externa. Si está en modo de prueba, agregá tu cuenta como usuario de prueba.
4. El único permiso que solicita el programa es `https://www.googleapis.com/auth/drive.appdata`. Si la consola pide configurar acceso a datos, agregá ese permiso.
5. En **Clients / Clientes**, creá un cliente de tipo **Desktop app / Aplicación de escritorio**. Descargá el JSON.
6. En el gestor: **Configuración → 1. Importar JSON OAuth… → 2. Conectar cuenta**. Elegí tu cuenta en el navegador y autorizá el permiso.
7. Al volver al gestor debe verse **Google Drive conectado**. Creá un respaldo con destino Drive o ambos y actualizá la nube.

Importá el mismo JSON y conectá la misma cuenta en la segunda PC. La identidad de aplicación/proyecto y la cuenta deben coincidir para acceder a los mismos respaldos.

## Dónde aparecen las partidas

Se guardan en `appDataFolder`, el espacio privado de la aplicación en tu cuenta. Las versiones se ven en la pestaña Google Drive del gestor; no aparecen como una carpeta común en la web de Drive. Consumen almacenamiento de la cuenta. Revocar acceso o borrar los datos de la aplicación desde Google puede impedir el acceso o eliminar esos datos; conservá exportaciones locales de las copias que necesites aparte.

## Sesión y errores habituales

- **No configurado:** importá el JSON descargado de un cliente de escritorio, no una cuenta de servicio ni un cliente web.
- **access_denied / usuario no autorizado:** agregá la cuenta a los usuarios de prueba del proyecto o revisá la audiencia y el consentimiento.
- **API deshabilitada:** habilitá Google Drive API en el mismo proyecto del cliente.
- **Sesión vencida/revocada:** volvé a conectar. Los respaldos pendientes permanecen en la PC. Los clientes externos en modo de prueba pueden tener refresh tokens con vencimiento de siete días.
- **HTTP 403 / cuota:** revisá autorización y almacenamiento de la cuenta. La copia local y la cola no se marcan como sincronizadas.
- **Sin navegador registrado:** el gestor muestra la dirección para abrirla manualmente. El callback usa una dirección loopback `127.0.0.1` con puerto aleatorio, no un código para pegar en el programa.

El JSON y la sesión se guardan solo en la carpeta de datos del gestor y están excluidos del repositorio. No pegues tokens ni el contenido de tus credenciales en issues.

## Referencias oficiales usadas

- [OAuth 2.0 para aplicaciones de escritorio, PKCE y loopback](https://developers.google.com/identity/protocols/oauth2/native-app).
- [Espacio privado appDataFolder](https://developers.google.com/workspace/drive/api/guides/appdata).
- [Transferencias reanudables](https://developers.google.com/workspace/drive/api/guides/manage-uploads).
- [IDs preasignados](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/generateIds).

El cliente HTTP se verifica con un simulador local; conectar una cuenta real y probar el recorrido entre PCs es la validación externa pendiente.
