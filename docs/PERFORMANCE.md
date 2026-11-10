# Rendimiento de la 2.0

## Qué se redujo

- En manual no hay sondeo periódico de procesos, de partidas ni del catálogo remoto.
- Los eventos del botón Lua y de apertura de una segunda instancia usan WatchService del sistema.
- Protección: de cada 2 segundos a cada 15, con reutilización del proceso ya identificado.
- Pendientes: reintento únicamente si existen y el destino está configurado; espera creciente hasta 10 minutos.
- El historial usa JSON pequeños en lugar de descomprimir los manifiestos de todos los ZIP.
- La interfaz conserva resúmenes, no miles de entradas de archivos por copia.
- Se reutilizan buffers; SHA-256 y MD5 se calculan juntos y se comparte el cliente HTTP directo.
- En local/carpeta no se crea cliente HTTP. Un único trabajador hace las tareas con baja prioridad.
- El portable usa runtime comprimido y Serial GC con heap inicial de 8 MiB; puede crecer según el trabajo.

## Método

Comparación en el mismo entorno Linux, OpenJDK 21.0.13, con la 1.0 publicada en `95e70d8` y la 2.0 de este cambio. `PerformanceProbe` compila contra ambas APIs.

Historial: una partida ficticia con 2.000 archivos de 1.024 bytes, tres versiones, 50 consultas del historial. Se hizo una consulta de calentamiento; en la 2.0 esta completa la migración del índice 1.x y queda fuera del tiempo de las 50 consultas. Se midió tiempo, CPU del hilo, lecturas `/proc/self/io` y RSS máxima muestreada externamente cada 10 ms.

Reposo: ventanas reales en un escritorio Xvfb, sin cuenta, archivos pendientes ni protección. Tras 3 segundos de calentamiento, se observaron otros 15 segundos con psutil. El benchmark usa el lanzamiento normal de la 1.0; la 2.0 usa los parámetros de su portable: `-Xms8m -Xmx768m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20`.

## Resultados de esta ejecución

| Métrica | 1.0 | 2.0 |
| --- | ---: | ---: |
| 50 consultas de historial | 846,49 ms | 22,92 ms |
| CPU del hilo en esas consultas | 809,92 ms | 23,06 ms |
| Bytes leídos por las consultas | 19.976.957 | 92.661 |
| Llamadas de lectura del historial | 3.452 | 303 |
| RSS pico durante ese proceso de prueba | 404,7 MiB | 67,8 MiB |
| Heap usado al finalizar esas consultas | 26,3 MiB | 2,9 MiB |
| RSS al final del reposo con ventana abierta | 111,8 MiB | 82,1 MiB |
| Lecturas en 15 s de reposo | 240 | 0 |
| Bytes leídos en 15 s de reposo | 117.888 | 0 |
| Tiempo de CPU en esos 15 s | 0,02 s | 0,01 s |

La diferencia pequeña de CPU en reposo está cerca de la resolución de la medición: no demuestra un porcentaje estable de mejora. El cambio comprobable es que no hay tareas de consulta programadas ni lecturas en manual durante ese intervalo.

## Límites prácticos

Es una muestra reproducible, no una garantía para cualquier equipo, build de Java o partida. El pico del benchmark de historial no es el pico máximo de un respaldo o una restauración. Esas operaciones conservan verificaciones completas y pueden necesitar más CPU, memoria y disco temporalmente.

El heap máximo de 768 MiB no limita toda la RAM del proceso: también existen bibliotecas nativas, JVM y memoria del sistema. Un manifiesto excepcionalmente grande puede necesitar ejecutar el JAR con otro presupuesto; nunca se recomienda confiar en un mundo no probado por su tamaño.

El modo carpeta no incluye en estas cifras la RAM/CPU de Drive, OneDrive u otra aplicación externa. Aprovechar una aplicación que ya usás simplifica el acceso; instalar otra solo para esto puede aumentar el consumo total frente a la conexión directa.

No se redujeron hashes, controles de mutación, respaldos previos ni las verificaciones de restauración para obtener estas cifras. La revisión del historial es deliberadamente liviana; la verificación completa se realiza antes de usar el contenido.

## Reproducir

Compilá `src/test/java/pzcloud/PerformanceProbe.java` con las clases de cada versión y usá un directorio temporal distinto por versión:

```text
java -cp <clases>:<tests> pzcloud.PerformanceProbe prepare <directorio>
java -cp <clases>:<tests> pzcloud.PerformanceProbe measure <directorio>
```

En Windows el separador de classpath es `;`. Para comparar el mismo historial, copiá el directorio preparado por la 1.0 a la carpeta de prueba de la 2.0. El modo `idle` abre la ventana, imprime READY y espera una entrada por stdin para terminar. Todas estas pruebas deben usar datos ficticios separados de tus partidas.
