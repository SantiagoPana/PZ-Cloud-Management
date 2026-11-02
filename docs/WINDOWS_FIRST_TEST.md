# Primera prueba en Windows 11 (sin partidas reales)

## Preparacion

1. Instalar GitHub Desktop: https://desktop.github.com/
2. Iniciar sesion, clonar el repositorio `SantiagoPana/PZ-Cloud-Management`.
3. Cambiar la rama de `main` a `development` con el selector **Current branch**.
4. Instalar JDK 21 si `javac -version` no funciona. En PowerShell: `winget install Microsoft.OpenJDK.21` y abrir una terminal nueva.

## Primera prueba segura

Cerrar Project Zomboid. En GitHub Desktop: **Repository > Open in Explorer**. Abrir PowerShell en esa carpeta y ejecutar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

El script genera una partida **ficticia** en TEMP, compila las dos clases Java del repositorio, comprime el archivo ficticio en ZIP, lo descomprime, compara SHA-256 y borra los temporales. No toca tus partidas de Zomboid.

**Resultado esperado:** `[4/4] OK. ZIP creado, extraido y verificado. Archivo original intacto.`

Si falla, copiar el error completo. No instalar como mod todavía. Este es un test de motor Java aislado.

## GitHub Desktop: recordatorio

- **Fetch origin:** consultar cambios remotos.
- **Pull origin:** traerlos a la PC.
- **Commit:** registrar cambios en tu repositorio local.
- **Push origin:** publicarlos en GitHub.
- Trabajar solo en `development`; `main` es la base protegida.

## Proxima prueba

Cuando el smoke test pase, probaremos una partida **descartable y cerrada**, primero sin restauracion. Aun falta integrar Java con ZombieBuddy, detectar estado del mundo y validar el menu principal en Build 42.21.
