# MetroBoard 0.5.0 — mediciones y revisión de la propuesta

Fecha: 2026-10-14. Las cifras de la JVM son de un portátil (Apple Silicon, JDK 25 de Android Studio) y las del emulador de
`Medium_Phone_API_37.0` (ART): sirven para comparar versiones entre sí, no para prometer una cifra en un teléfono.

## Punto de partida: una propuesta escrita sin ver el código

Se recibió una propuesta de optimización (generada por otro asistente sin acceso al repositorio). Se revisó punto por punto:

| Propuesta | Veredicto | Motivo |
|---|---|---|
| *Shadow buffer* del texto + `beginBatchEdit` | ya existía (0.4.0) | `InputConnectionEditor` ya guarda el texto alrededor del cursor y reconoce sus propios acuses (`expectedSelections`). La versión propuesta no compilaría (`inline` pública que usa un miembro privado) y no invalidaba la copia al mover el cursor sin seleccionar. |
| Sugerencias asíncronas con cancelación | en parte | Ya eran asíncronas (0.4.0); faltaba no ejecutar las búsquedas ya viejas: hecho. |
| `GestureResampler` sin asignaciones, filtro pasa-bajos, esquinas | no | El remuestreo solo corre una vez por trazo, al levantar el dedo. El filtro añade retardo y deforma la forma, y nada usaría las esquinas. Lo caro estaba en otro sitio (abajo). |
| Poda «branch & bound» en el decodificador | idea válida, código incorrecto | Su cota no dependía del mejor candidato y sustituía la DTW por una comparación rígida. Se hizo una poda **exacta**: mismo resultado. |
| Muestras históricas de `MotionEvent` | sí, acotado | Solo una vez reconocido el trazo, para no cambiar cómo se clasifican los toques (arreglo de 0.3). |
| Háptica y sonido en `ACTION_DOWN` | sí | Ver abajo. |
| Inicio del trazo a 14 dp + velocidad | no | Reabriría los trazos falsos al teclear rápido con dos pulgares. |
| `versionCode = 5` para 0.5.0 | error | 5 es la 0.4.1. La 0.5.0 es 6 (`tools/release_manager.py --set-version 0.5.0`). |
| Cifras («45 ms → <3 ms», «~45 ms háptica», «8–16 ms») | sin respaldo | No salían de ninguna medición. |

## Word Flow: descodificar un trazo

Método: `WordFlowShapeDecoder.decode` sobre 400 trazos sintéticos por idioma (generador de `WordFlowQualityTest`, semilla 11, ruido
0,02), con los diccionarios reales, tras una pasada de calentamiento; memoria con `ThreadMXBean.getThreadAllocatedBytes`. Mismo
benchmark antes y después (tres repeticiones; se muestra la típica).

| Idioma | media antes → después | p95 | memoria asignada por trazo | GC cada 400 trazos |
|---|---|---|---|---|
| es | 9,2 → 0,6 ms | 23,7 → 1,3 ms | 11.007 → 213 KB | 14 → 0–1 |
| en | 5,4 → 0,4–0,5 ms | 14,0 → 0,9–1,2 ms | 6.860 → 137 KB | 8–9 → 0–1 |
| fr | 9,2 → 0,6 ms | 24,2 → 1,3 ms | 11.767 → 212 KB | 15 → 0–1 |
| de | 9,2 → 0,7 ms | 24,6 → 1,4 ms | 11.136 → 209 KB | 14–15 → 0–1 |

En el emulador (ART, `WordFlowDecodeBenchmarkTest`, 200 trazos por idioma) la versión nueva tarda de media 4,8–6,4 ms (p95 10,7–13,6).
No se midió la 0.4.1 en ART.

Dónde se iba el tiempo (desglose de la 0.4.1, español): DTW 52 %, «letras en orden» 15 %, `wordsByEndpoints` 9 %, normalizar cada
candidata 6 %. Unas 571.000 llamadas a `hypot` por trazo a ≈ 14 ns cada una eran casi todo el coste; la DTW reservaba 9,4 KB por candidata.

Qué se cambió, sin cambiar el resultado:
- `fastHypot`: `sqrt` de la suma de cuadrados en `double` redondea al mismo `Float` que `kotlin.math.hypot` salvo junto al punto medio
  entre dos `Float`, donde se usa `hypot`. Probado bit a bit con 2 millones de pares.
- «Letras en orden»: qué muestras del trazo caen cerca de cada tecla se calcula una vez por trazo, como máscara de 32 bits.
- DTW con dos filas reutilizadas; se abandona en cuanto el mínimo de una fila (cota inferior: los costes no son negativos) impide
  pasar `MAX_DISTANCE` o entrar entre las tres mejores (con empate gana la anterior, como en la ordenación estable).
- El índice por primera/última letra guarda la forma normalizada y la frecuencia (antes, ≈ 18.600 `getFrequency` por trazo).

`WordFlowDecoderEquivalenceTest` guarda la implementación 0.4.1 como oráculo y compara 2.095 trazos (4 idiomas, ruido 0,02 y 0,05,
con y sin palabra anterior, palabras aprendidas, garabatos): listas, puntuaciones y `decode()` idénticos.

## Cada tecla (emulador)

Método de la 0.4: build *debug*, `adb logcat -s MB_LAT MB_PHASES`, la misma frase de 110 pulsaciones (`adb shell input tap`) en el
campo «try it» de Ajustes, datos de la app borrados antes de cada tanda, versiones alternadas (nueva, 0.4.1, nueva, 0.4.1).

| | 0.4.1 | 0.5.0 |
|---|---|---|
| dibujar tras la tecla (`draw`), mediana | 1,95 / 1,80 ms | 0,40 / 0,40 ms |
| `draw`, total de 110 teclas | 344 / 288 ms | 108 / 128 ms |
| procesar la tecla (`handled`), mediana | 3 / 3 ms | 2 / 2 ms |
| espera del toque (`queued`), mediana | 13 / 13 ms | 10 / 11 ms |
| `commitText` | ≈ 1,6 ms | ≈ 1,6 ms |

Causas: `TextView.setCompoundDrawablesRelative` llama siempre a `requestLayout()` (comprobado en las fuentes de API 37) y se llamaba
en los tres huecos de la barra en cada tecla; `render()` corría también con el acuse de nuestro propio commit; y cada letra reescribía
todas las etiquetas. En el emulador la háptica cuesta ≈ 0 (no hay vibrador): lo que gana un teléfono al dar el clic en el toque no se ve
aquí.

## Pruebas

- JVM: 367 pruebas (antes 362), 0 fallos, 1 omitida.
- Instrumentadas (emulador): 48 + `WordFlowDecodeBenchmarkTest`, 0 fallos. Nuevas: clic al tocar y tecla al soltar, dos pulgares
  iluminados, tecla tras un trazo después de la palabra (y como mucho 250 ms de espera), muestras históricas durante el trazo,
  `render()` repetido sin `requestLayout`, misma petición de `setLayout` sin reconstruir.
- Extremo a extremo en el emulador: la frase completa y un trazo real «the» (con «there» y «thee» de alternativas).
- Lint sin errores.

## Pendiente

Medir en un teléfono con el diagnóstico de Ajustes ▸ Acerca de; construir el índice de Word Flow al cargar el diccionario (el primer
trazo de cada idioma lo construye, ≈ 10 ms en la JVM).
