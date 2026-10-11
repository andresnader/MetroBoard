# Matriz de problemas — MetroBoard 0.3

Cada fila se **verificó contra el código y una prueba o el emulador** antes de corregirse. «Evidencia» dice cómo se confirmó;
«Verificación» dice qué demuestra hoy que está arreglado. Emulador = `emulator-5554`, Android 17 (API 37), arm64.
Ningún punto se ha comprobado en hardware real.

## 0. Reconciliación del estado de partida

| Hallazgo | Evidencia |
|---|---|
| El APK probado en el video **no es el del PR #1**. Es la rama `main` **local** (`3d4de42`, sin publicar; `origin/main` = `bc9b8e3`). | `app/release/app-release.apk` (v0.1.0, vc 1) contiene `es.dict` de 926 KB y `TouchInputStateMachine` y **0** coincidencias con `TouchGestureArbiter`/`LanguagePack`; la pantalla About del video dice «0.2.0». |
| Las dos líneas **divergían** desde `bc9b8e3`. El PR #1 duplicaba piezas de `main` (otra máquina de estados táctil, otro modelo de idiomas con listas fijas). | `git merge` con 4 conflictos reales; ninguna referencia a `LanguagePack` y solo su propio test a `TouchGestureArbiter` tras integrar. |
| El «video» `~/Movies/2026-10-10 16-45-01.mov` era una grabación de TV (Ecuavisa), irrelevante. El video útil es `test teclado.mp4` (raíz del repo, sin versionar). | Contact sheets de ambos. |
| Resolución: rama `feature/metroboard-0.3-quality` desde el PR #1 + merge de `3d4de42` (tomando el lado de `main` en los 4 conflictos) + retirada de las piezas duplicadas del PR. Copia de seguridad: rama local `backup/local-main-3d4de42`. `main` no se tocó. | `git log`, ver [technical-report.md](technical-report.md). |

## 1. Observaciones del video (V01–V09)

| ID | Síntoma | Causa raíz confirmada | Estado |
|---|---|---|---|
| V01 | Interfaz Metro prometedora | — | Conservada, sin rediseño |
| V02 | `decoro / memoro` tras escribir *demoro* | `demoro` no estaba en `es.dict` (lista de lemas, 76k) y se rellenaban ranuras con vecinos difusos de baja frecuencia; además faltaban `gracias`, `escribiendo`, `pingüino` | **Corregido**: diccionarios nuevos + política de sugerencias. Probado y en emulador |
| V03 | Prefijo «Cu…» razonable | — | Conservado; probado que sigue dando cuando/cuenta/cualquier |
| V04 | Word Flow se activa con dos pulgares | Tres causas: inicio del swipe por distancia sola; taps emitidos en orden de liberación; pulsaciones perdidas con deriva | **Corregido** y probado (JVM + vista real en emulador). **Sin hardware real** |
| V05 | Teclado compacto, fila numérica | No se midió la relación superficie visual / región táctil | **Pendiente** (ver §4) |
| V06 | Cambio de temas | Icono de cambio de teclado del sistema blanco sobre blanco en tema claro | **Corregido** (emulador). Resto de elementos no auditados uno a uno |
| V07 | Franja inferior | Es del sistema: botones de ocultar/cambiar teclado dibujados por Android bajo el IME; el padding por `navigationBars` es correcto y el fondo es continuo | **Sin cambio de padding**: en capturas del emulador (navegación por gestos) el fondo es continuo y los controles no tapan teclas. **No se midieron los insets** ni se probaron tres botones, otras versiones o fabricantes |
| V08 | Mayúsculas | Capitalización inmediata tras `.`; Shift sin efecto sobre selección | **Corregido** y probado |
| V09 | Ajustes | 4 interruptores sin efecto; export al portapapeles; versión y licencias falsas en About | **Corregido** (ver 2.14–2.18) |

## 2. Defectos encontrados (con evidencia y estado)

| # | Defecto | Evidencia | Corrección | Verificación |
|---|---|---|---|---|
| 2.1 | **Una letra doble (`ww`, `oo`, `ll`) seleccionaba la palabra** y la siguiente letra la reemplazaba | Emulador: `www.google.com` → `w.ogle.Com`; código de `onDoubleTapWordDetected` | Función eliminada; reemplazo por selección del editor | Test de máquina, test en vista real, emulador |
| 2.2 | El motor mezclaba idiomas (sugerencias, erratas, bigramas, autocorrección) y `setLanguage()` no hacía nada; los tests lo daban por bueno | Lectura del código + 7 tests rojos | Aislamiento por idioma; tests corregidos | 15+ tests, emulador |
| 2.3 | El diccionario personal usaba siempre `"es"`; aprendía solo palabras *del diccionario* al elegirlas | Código + captura «Ver palabras aprendidas» del video (`como (1)`, `mejor (1)`) | Por idioma; aprende palabras nuevas; protege desde el 2.º uso | 6 + 11 tests, emulador |
| 2.4 | Pulsación perdida con deriva entre 1,5×slop y distancia de swipe | Escenario sintético rojo | Un press que no fue swipe/cursor/long-press escribe su tecla | Tests |
| 2.5 | Orden de caracteres = orden de liberación con pulgares solapados | Escenarios rojos (2 y 3 dedos) | Cola por orden de pulsación (ventana 250 ms) | Tests + vista real |
| 2.6 | Swipe iniciado solo por `distancia > touchSlop` | Escenario rojo | Evidencia: ≥ 3 teclas distintas (o ≥ 2 con trayecto largo) | Tests |
| 2.7 | Palabra válida excluida de sus sugerencias y rellena con vecinos difusos (`vamos → gamos, vanos`) | Sonda con datos reales | Palabra válida frecuente primero; difuso solo para desconocidas y frecuentes | Tests con diccionarios reales |
| 2.8 | en/fr/de eran ~200 palabras escritas a mano; es sin `gracias`, `escribiendo`… | Recuento de líneas y búsquedas | Diccionarios de 60.000 palabras reproducibles | Informe de build + tests |
| 2.9 | Frecuencias del español derivadas de un APK propietario (riesgo ya señalado en `docs/`) | `NOTICE` previo | Sustituidas por OpenSubtitles (CC-BY-SA-4.0) | Documentación; **revisión legal pendiente** |
| 2.10 | Word Flow solo alcanzaba las 500 palabras más frecuentes; mapas estáticos; el diccionario personal nunca llegaba al decodificador; sin rechazo de garabatos | Baseline medido: top-1 32–36 %, `teclado`/`mensaje`/`keyboard`… 0/20 | Léxico completo, geometría real, puerta de confianza, hilo aparte | Benchmark + emulador |
| 2.11 | Candidato principal en la ranura secundaria de la barra | Video + código (`index == 1`) | Mejor candidato al centro | Emulador |
| 2.12 | Autocorrección irreversible; correcciones en nombres propios/siglas/URL; `don't` partido; sin espacio tras sugerencia | Lectura + tests rojos | Ver CHANGELOG | 25+ tests, emulador |
| 2.13 | Capitalización tras `.` sin espacio (`www.Google.com`); Shift sin efecto sobre selección | Tests rojos | Solo tras `. `; selección cicla casing | Tests, emulador |
| 2.14 | 4 ajustes sin efecto (cursor en espacio, pulsación larga en espacio, popups, borde a borde) | Búsqueda de usos | 3 implementados, 1 eliminado | Tests |
| 2.15 | Exportación de ajustes **y palabras** al portapapeles; importación sin límites ni validación | Código | SAF + `BackupFormat` validado | 14 tests + emulador (export e import adversarial) |
| 2.16 | Pedazos de URL/correo se aprendían (`com`) | Archivo exportado en emulador | Protegidos | Tests |
| 2.17 | Backspace por punto de código (emoji compuesto = varios toques) | Código | Borrado por grafema | 11 JVM + 6 en dispositivo |
| 2.18 | About con versión fija «0.2.0» (APK decía 0.1.0) y licencias de fuentes nunca usadas | Video + `strings.xml` | Versión real 0.3.0; texto veraz | Emulador |
| 2.19 | Carga del diccionario en el hilo de UI (0,5–7,4 s en emulador) y sin desalojo (165 MB con 4 idiomas) | Benchmark en dispositivo | Carga asíncrona, LRU de 2, silencio mientras carga | Tests + benchmark |
| 2.20 | Los assets no eran entrada de los tests JVM: resultados «verdes» obsoletos | Cambio de datos sin re-ejecución | Declarado en `build.gradle.kts` | Re-ejecución |
| 2.21 | `generate_dictionaries.py` pisaba los diccionarios reales con semillas | Lectura | Ya no escribe `.dict` | — |

## 3. Notas de calidad del propio trabajo

- Varios tests existentes **codificaban el comportamiento defectuoso** (mezcla de idiomas, palabra exacta oculta, mayúscula
  tras `.`, sin espacio tras sugerencia). Se actualizaron explicando el motivo en el commit; ninguno se borró para forzar el verde.
- Una prueba mía contenía una aserción tautológica (`écrire`); se detectó en revisión y se sustituyó por una real.

## 4. No resuelto / no verificado

| Tema | Estado |
|---|---|
| V05 superficie táctil vs. visual; hitboxes separadas (`VisualKeyBounds`/`TouchTargetBounds`) | **No hecho** |
| Hardware real (dos pulgares, latencia, ergonomía) | **No probado** |
| TalkBack, orientación horizontal, navegación de 3 botones, Android 8–16, tamaños de pantalla y de fuente | **No probado** |
| Siguiente palabra con contexto real (n-gramas) | Solo 15–30 pares curados por idioma |
| Popups de alternativas: posición exacta y selección por deslizamiento | No auditado en esta iteración |
| Sonidos propios estilo WP8.1; niveles de háptica por hardware | No hecho / no verificado |
| Catálogo emoji completo, búsqueda de emoji | No hecho (hay ~800 emoji, recientes y tonos de piel) |
| Historial de portapapeles | No implementado (solo pegar al pulsar 📋) |
| Variantes regionales, `es-EC` como paquete propio | No hecho |
| Índice compacto de diccionario (memoria) | No hecho (trie de 35–50 MB por idioma) |
| Revisión legal de CC-BY-SA-4.0 + GPL en un APK AGPL | **Pendiente** |
