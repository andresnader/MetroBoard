# QA — MetroBoard 0.3

Todo lo de aquí sale de ejecuciones reales de esta rama. Lo que no se ejecutó está en [Limitaciones](#limitaciones).

## Entorno

| | |
|---|---|
| Build | `./gradlew clean test assembleDebug lintDebug` → **BUILD SUCCESSFUL** (1 min 26 s, JDK 25 de Android Studio) |
| Pruebas unitarias (JVM) | **265 en total, 0 fallos, 1 omitida** (la omitida es la de calibración de Word Flow, que exige `METROBOARD_CALIBRATE=1`) |
| Pruebas instrumentadas | **39, 0 fallos** (ejecutadas antes de los cambios de Ajustes e idioma; **no se volvieron a ejecutar**: la prueba de preferencias cambió y no se ha corrido), `connectedDebugAndroidTest` en AVD `Medium_Phone_API_37.0` (Android 17, API 37, arm64) |
| Lint (`lintDebug`) | **0 errores, 14 avisos** (sin comparación con una línea base) |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 2.419.236 bytes, **debug** (no firmado para publicación) |
| Dispositivo | Solo el emulador. **Ningún dispositivo físico.** |

## Qué cubre cada grupo del prompt

| Grupo | Cobertura real |
|---|---|
| **A** Escritura básica | Parcial. Se escribieron en el emulador frases como «es mas comodo sin embargo me demoro», «mira www.google.com y listo. otra cosa llegaron ellos» y palabras en francés; **no** se ejecutaron literalmente A01–A05 (acentos, `¿?`, comas). |
| **B** Multitouch | B01–B05, B07 con eventos sintéticos sobre la máquina de estados (17 escenarios) y sobre el `KeyboardView` real en el emulador (7 escenarios). B06 parcial. **B08 (60 s repetidos) no ejecutado.** Sin pulgares reales. |
| **C** Word Flow | C01–C08 con swipes sintéticos (24 palabras, 20 repeticiones cada una: Top-3 20/20 en todas; Top-1 ≥ 18/20); C09 y C10 en la máquina de estados. Dos palabras desplazadas con trazos reales de varios segmentos en el emulador. |
| **D** Idiomas | D01–D07 en JVM con diccionarios reales y las frases exactas del prompt; cambio sucesivo de idioma repetido 3 veces; francés y español comprobados en el emulador. |
| **E** Autocorrección | E01–E05, E08, E09 en JVM; E06 (nombres propios) y E07 (URLs/correos) con motor simulado; E10 solo por la política del editor. |
| **F** Shift | F01–F08 en JVM contra el controlador real; F09/F10 por la política (`automaticCapitalization` falso en correo y contraseña). |
| **G** Unicode | G07 (Backspace sobre emoji compuesto) en JVM y en el dispositivo; G08 prueba previa. **G01–G06 (escribir acentos y emoji) no ejecutados.** |
| **H** Android | Solo a nivel de política (`EditorPolicy`: contraseña, correo, URL, número, teléfono). **H10–H12 (horizontal, gestos, tres botones) no ejecutados.** |

## Métricas (reproducibles)

Todas con diccionarios reales y semillas fijas. Los swipes y erratas son **sintéticos**: modelan a una persona cuidadosa, no
un pulgar real; son un índice de regresión, no una cifra de usuario.

### Autocorrección — `PredictionQualityTest` (1.500 palabras por idioma, semilla 42)

Erratas generadas desde las 8.000 palabras más frecuentes (≥ 5 letras): transposición, letra omitida, tecla vecina, letra duplicada.

| Idioma | Arregladas | **Erróneas** | Sin tocar |
|---|---|---|---|
| es | 55,9 % | **1,3 %** | 42,7 % |
| en | 54,7 % | **0,0 %** | 45,3 % |
| fr | 49,7 % | **2,3 %** | 48,0 % |
| de | 53,7 % | **0,7 %** | 45,6 % |

Antes del control de ambigüedad, en español: arregladas 68,4 %, **erróneas 8,9 %** (la omisión de letra era 25,5 % erróneas).
Se eligió el compromiso conservador a propósito. Palabras válidas cambiadas por la autocorrección: **0 de ~3.000 por idioma**; palabras
frecuentes válidas que no salen primero en las sugerencias: **0**.

### Predicción por prefijo (5.000 palabras más frecuentes, ≥ 5 letras)

| Idioma | Top-1 con 4 letras | **Top-3 con 4 letras** | Top-3 con 3 letras |
|---|---|---|---|
| es | 23,8 % | **62,6 %** | 32,8 % |
| en | 27,7 % | **81,1 %** | 41,1 % |
| fr | 24,8 % | **69,6 %** | 34,7 % |
| de | 23,5 % | **64,2 %** | 33,3 % |

### Word Flow — `WordFlowQualityTest` (swipes sintéticos, semilla 11)

Trazo = polilínea por los centros de tecla, corte de esquinas y ruido gaussiano del 20 % de una tecla.

| | Baseline (decodificador del APK probado, N = 1.200/idioma) | 0.3 (N = 400/idioma) |
|---|---|---|
| Top-1 | 35,9 / 33,3 / 34,4 / 31,8 % (es/en/fr/de) | **94,8 / 94,0 / 90,8 / 91,0 %** |
| Top-3 | 38,2 / 34,8 / 38,1 / 34,5 % | **98,8 / 97,5 / 99,0 / 98,5 %** |
| 24 palabras de referencia (Top-3 en 20 intentos) | 12 de 24 con 0/20 | **24 de 24 con 20/20** |
| Latencia JVM (media / peor) | 0,4–0,6 / 9–30 ms (solo top-500) | **5,6–9,6 / 33–38 ms** |

Robustez (N = 200, Top-3): ruido del 35 % de tecla → 82 / 71 / 77 / 76 %; ruido del 50 % → 59 / 47,5 / 55,5 / 50 %.
Garabatos aleatorios que se convierten en palabra: **1,0 / 2,0 / 1,0 / 1,5 %** (antes del filtro de confianza: 51–69 %).
(Una ejecución anterior con N = 1.200 dio 87,8–94,0 % Top-1 y 98,6–99,3 % Top-3; la diferencia es de muestreo.)

### Rendimiento en dispositivo — `DictionaryLoadBenchmarkTest` (emulador)

| Idioma | Carga en frío | Sugerir (por consulta) | Autocorregir (por consulta) |
|---|---|---|---|
| es | 7.444 ms (primera carga del proceso; incluye arranque en frío del intérprete) | 3,4 ms | 2,8 ms |
| en | 554 ms | 1,7 ms | 4,0 ms |
| fr | 498 ms | 1,6 ms | 3,5 ms |
| de | 725 ms | 1,7 ms | 3,4 ms |

Memoria medida antes de limitar a 2 idiomas: 35–50 MB por idioma (165 MB con los cuatro). Con el desalojo LRU, 87 MB en el mismo
recorrido. Esto es **un emulador**: no se extrapola a un teléfono. No se midieron fotogramas perdidos, GC ni memoria con escritura prolongada.

## Comprobaciones manuales en el emulador

- `tecldao␣` → `Teclado␣`; Retroceso → `Tecldao`; el siguiente espacio no lo reimpone.
- «Es mas comodo sin embargo me demoro » → `Es mas cómodo sin embargo me demoro ` (palabra válida intacta, `mas` ambigua intacta, tilde restaurada).
- Barra con «emba»: `Embajador | **Embargo** | Embarazo` (principal al centro).
- Francés (AZERTY): `bonjuor je voudrais ecrire` → `Bonjour je voudrais écrire`; swipes `bonjour` y `clavier` correctos.
- «mira www.google.com y listo. otra cosa llegaron ellos» → `Mira www.google.com y listo. Otra cosa llegaron ellos`.
- Tema claro: icono de cambio de teclado del sistema visible tras el arreglo.
- Exportar a `Download/metroboard-backup.json` (JSON v2) e importar un archivo adverso: tema aplicado, volumen 9 recortado a 1,0, ajustes
  no válidos ignorados, palabra con URL y de 60 caracteres rechazadas, idioma `zz` ignorado.

## Limitaciones

No se ejecutó ni se afirma: hardware real; TalkBack; orientación horizontal; navegación de tres botones; Android 8, 10, 13, 15, 16;
pantallas pequeñas/grandes; fuente aumentada; escritura continua de 60 s; fotogramas, GC y memoria con uso prolongado; niveles de
háptica y calidad de sonido; selección por deslizamiento en los popups; campos H05–H09 en apps reales; emoji G04–G06; revisión legal de licencias.
