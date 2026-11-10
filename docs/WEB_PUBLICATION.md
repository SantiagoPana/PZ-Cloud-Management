# Publicar la web para Google

Las páginas públicas están en `site/`. La portada enlaza a privacidad y condiciones; no se publica el resto del repositorio como sitio ni la configuración OAuth del cliente.

1. En el repositorio, abrir **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. En **Actions → Publish public website → Run workflow**, elegir `development` y ejecutar.
3. Esperar al paso **Deploy public website** correcto. Si Pages está desactivado, el workflow prepara los archivos pero omite la publicación y explica ese paso en su resumen.
4. Comprobar sin iniciar sesión estas tres direcciones: `https://santiagopana.github.io/PZ-Cloud-Management/`, `https://santiagopana.github.io/PZ-Cloud-Management/privacy.html` y `https://santiagopana.github.io/PZ-Cloud-Management/terms.html`.
5. En Search Console, con la cuenta propietaria del proyecto Google, agregar la propiedad de prefijo URL del sitio publicado y elegir etiqueta HTML. Incorporar la etiqueta exacta dentro de `<head>` en `site/index.html`, publicar y pulsar Verificar en Search Console. No usar una etiqueta de ejemplo.
6. Completar los enlaces reales en Branding. El dominio autorizado es `santiagopana.github.io`, sin protocolo ni ruta. Si Google pide acreditar una propiedad más amplia de ese dominio, publicar también una portada en el sitio de usuario raíz y verificar ese prefijo.
7. Tras verificar la propiedad, respetar el plazo que indique Google antes de volver a intentar la verificación de marca; la pantalla actual del usuario indica 24 horas.

Publicar archivos y acreditar propiedad son pasos independientes. Que un workflow termine correctamente con el despliegue omitido no significa que el sitio ya responda. La marca no se declara verificada hasta que Google la apruebe.
