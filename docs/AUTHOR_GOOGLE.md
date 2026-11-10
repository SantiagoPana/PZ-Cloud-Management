# Habilitar Google una sola vez para el programa

Esta configuración la hace el autor de Knox Net. Los usuarios finales solo deben pulsar **Iniciar sesión con Google** y autorizar su cuenta.

## Registrar la aplicación

1. En [Google Cloud Console](https://console.cloud.google.com/), creá o elegí el proyecto de Knox Net y habilitá Google Drive API.
2. Configurá el consentimiento con el nombre real de la aplicación y el permiso `https://www.googleapis.com/auth/drive.appdata`.
3. Creá un cliente OAuth de tipo **Aplicación de escritorio** y descargá su JSON.
4. Mientras el proyecto esté en modo de prueba, agregá las cuentas autorizadas a sus usuarios de prueba. Para distribución pública, completá los requisitos de publicación/verificación que Google indique.

Google requiere una identidad OAuth registrada; un botón o una ventana no pueden reemplazarla. La app ya implementa el flujo de escritorio y no necesita pedir a cada usuario que cree su propio proyecto.

## Ediciones para usuarios

Desde la 2.3, la identidad de Google se incorpora exclusivamente al compilar. El cliente distribuido no incluye asistente de autor, importación de credenciales, reemplazos por variables de entorno o parámetros de JVM, ni el publicador de compilación. Las guías de desarrollo se conservan solo en el repositorio.

## Generar el portable configurado

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -Package -GoogleClientFile "C:\ruta\cliente-de-escritorio.json" -RequireGoogleLogin
```

También podés colocar el archivo en `release/google-client.json` antes de compilar; está excluido de Git. El publicador valida el formato, incorpora únicamente la identidad de aplicación y elimina recursos obsoletos de compilaciones anteriores. No se incluyen tokens de usuario.

`-RequireGoogleLogin` hace fallar la compilación si falta esa configuración. Sin ese parámetro se permite una vista previa; su ventana pide actualizar la aplicación. `GOOGLE_AUTH_STATUS.json` dentro del portable indica `googleClientBundled`. Ese valor confirma que hay configuración, no que se haya realizado una conexión real durante la compilación.

## Compilar desde GitHub Actions

Creá el secreto de repositorio **PZCLOUD_GOOGLE_CLIENT_JSON** con el JSON del cliente de escritorio de la app. El workflow ya lo toma al generar el portable; no lo imprime ni lo agrega al código Git. Cuando está ausente, el resultado es una edición sin Google habilitado, no una configuración de ejemplo que simula estar lista.

Como alternativas para compilaciones locales, existen `PZCLOUD_GOOGLE_CLIENT_FILE`, o `PZCLOUD_GOOGLE_CLIENT_ID` y `PZCLOUD_GOOGLE_CLIENT_SECRET`. En Bash, `PZCLOUD_REQUIRE_GOOGLE_LOGIN=1` exige un cliente configurado.

## Comprobar el resultado

Abrí el portable en una PC de prueba sin `google-client.json` anterior, pulsá Iniciar sesión con Google y completá la autorización con una cuenta permitida por el proyecto. Guardá una partida ficticia, comprobá su presencia y repetí la descarga desde otra PC con la misma cuenta. Solo después de esta prueba se puede afirmar que esa identidad de aplicación funciona con Google real.

El JSON contiene identidad de una aplicación de escritorio distribuida: no es una sesión de usuario ni un secreto de servidor que pueda mantenerse oculto dentro del ejecutable. Los access/refresh tokens sí pertenecen a cada usuario y nunca deben publicarse. El proceso de compilación descarta cualquier campo de tokens de usuario que aparezca por error en el documento.

La app conserva una sesión existente si fue creada con el mismo cliente incluido. Una sesión para otra identidad requiere una nueva autorización y no se usa para subir copias. Los archivos de cliente guardados por versiones anteriores se ignoran. Los datos de las partidas y copias no se borran.

Referencias: [OAuth de escritorio](https://developers.google.com/identity/protocols/oauth2/native-app) y [políticas OAuth](https://developers.google.com/identity/protocols/oauth2/policies).
