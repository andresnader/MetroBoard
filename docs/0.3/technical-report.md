# Informe técnico — MetroBoard 0.3

## 1. Estrategia de ramas

```
dc87092 ─ ab63ffd ─ bc9b8e3 (origin/main) ─┬─ 77ceda7 … 08a98de (PR #1: refactor/input-quality-0.2)
                                            └─ 3d4de42 (main LOCAL, sin publicar: la app probada en el video)
feature/metroboard-0.3-quality  =  PR #1  +  merge de 3d4de42  +  commits 0.3
backup/local-main-3d4de42       =  copia de seguridad local del commit no publicado
```

Las dos líneas implementaban lo mismo de dos maneras (máquina de estados táctil, modelo de idiomas). En los 4 conflictos
(`KeyboardView`, `WordFlowImeService`, `WordFlowShapeDecoder`, `LocalSuggestionEngine`) se tomó el lado de `main`, que ya hacía
despacho por puntero en `dispatchTouchEvent` y traía la tabla de diccionarios; se retiraron `TouchGestureArbiter` y el primer
`LanguagePack` (sin referencias) y se portó el invariante del PR («sin swipe tras un segundo dedo») como prueba. `main` y `origin/main`
no se tocaron. La rama 0.3 contiene el commit local no publicado, así que al publicarla ese commit viaja con ella.

## 2. Sistema de entrada

`KeyboardView.dispatchTouchEvent` recorre cada puntero (`pointerId`, no índice), hace *hit-testing* sobre los botones reales y alimenta
**una única** `TouchInputStateMachine` (Kotlin puro, sin Android, probable con eventos sintéticos).

```
                 DOWN                     movimiento ≥ umbral, ≥ 3 teclas       UP con trazo ≥ 3 puntos
        IDLE ───────────► TAP_CANDIDATE ──────────────────────────► SWIPE_ACTIVE ───────────────► commit Word Flow
          ▲                  │  │  │                                     │  │ 2.º dedo
          │       tecla con  │  │  └─ espacio + deslizar ─► SPACEBAR_CURSOR_ACTIVE
          │      alternativas│  └──── retroceso + izq. ───► BACKSPACE_SWIPE_DELETE_ACTIVE
          │      o espacio   ▼ mantener                         ▼
          │            LONG_PRESS_ACTIVE                  MULTITOUCH_ACTIVE ◄── 2.º dedo en cualquier estado
          │                                                       │ (el swipe queda desactivado hasta soltar TODOS los dedos)
          └──────────────── UP / CANCEL (vaciado ordenado de taps pendientes) ◄┘
```

Reglas añadidas en 0.3 (cada una con prueba que fallaba antes):

- **Un press que no fue swipe, cursor ni pulsación larga escribe su tecla**, con tolerancia de deriva de 2× la distancia de swipe.
- **Orden de pulsación**: los taps se emiten por orden de *bajada*; si un dedo anterior sigue pulsado (≤ 250 ms) el tap espera. Un temporizador de
  la vista vacía la cola para que una tecla mantenida nunca retrase al resto; `CANCEL` y un nuevo `DOWN` también la vacían.
- **Evidencia de swipe**: ≥ 3 teclas-letra distintas, o ≥ 2 con trayecto de ≥ 2× la distancia de arranque. Distancia sola ya no basta.
- Ajustes (cursor en espacio, pulsación larga en espacio, popups) gobiernan los gestos; deshabilitar una pulsación larga deja el press intacto.
- Sin «doble toque sobre palabra»: un IME no recibe esos toques; se sustituyó por reemplazo de la selección del editor.

**Decisión A (vistas por tecla + despachador único) frente a B (superficie propia en Canvas).** Se mantiene A. Las vistas por tecla dan
`contentDescription`, dibujo y estados sin reescribir; el fallo estaba en la lógica y no en la arquitectura de vistas. La prueba
`KeyboardViewTouchTest` (7 escenarios multipuntero sobre la vista real, en emulador) y 17 escenarios sobre la máquina cubren el requisito. B costaría
accesibilidad y mantenimiento sin una ganancia demostrada. **No se midió** latencia táctil real ni se descartó que B rinda mejor en hardware real.

## 3. Idiomas y predicción

- `LanguagePack` (locale, regla de elisión del apóstrofo) + diccionarios por idioma (`LanguageDictionaryRegistry`). El layout por defecto
  sigue en `KeyboardLayout.defaultLayoutFor` (una sola fuente de verdad).
- `LocalSuggestionEngine` solo consulta el diccionario del idioma activo, también para erratas, bigramas y trigramas. El diccionario personal es por idioma.
- Política de candidatos (mejor primero; la UI pone el primero al centro):
  1. palabra válida **frecuente** (≥ 100) primero y literal; una válida rara compite por frecuencia con las continuaciones;
  2. corrección de erratas curada del idioma activo;
  3. palabras aprendidas (≥ 2 usos);
  4. continuaciones por frecuencia (tras una palabra completa solo las comunes);
  5. vecinos difusos **solo** si la palabra no es válida y son frecuentes (≥ 48 ≈ top 32.000 de 60.000).
- **Autocorrección** (conservadora): nunca sobre palabra válida o aprendida; solo cambios por transposición/tecla vecina (coste ≤ 0,8), o una letra
  omitida/sobrante en palabras de ≥ 6 letras (≤ 1,0) con destino frecuente; en ambos casos con **ganador inequívoco** (el segundo candidato a ≥ 0,5 más
  lejos o el ganador 3× más frecuente). Se omite en nombres propios (mayúscula fuera de inicio de oración), siglas/camelCase, URL/correos, y palabras rechazadas.
  Retroceso inmediato revierte, recuerda el rechazo y cuenta como confirmación del usuario.
- **Aprendizaje**: al pasar a la siguiente palabra, si es nueva (no está en el diccionario), no se autocorrigió, no es URL/correo y el campo lo permite.
- Mientras el diccionario real carga, el motor calla (ni sugiere, ni corrige, ni aprende) para no actuar sobre la muestra mínima de 35 palabras.

## 4. Diccionarios

`<lang>.dict` (60.000 palabras, `palabra\tfrecuencia`) se carga en un trie de nodos con *top-K* por prefijo. Carga: `preload()` en un hilo de baja prioridad,
`getNonBlocking()` devuelve la muestra mientras tanto, y el registro conserva como máximo 2 idiomas (LRU por acceso). El índice por primera/última letra
(`wordsByEndpoints`) alimenta a Word Flow. Memoria medida 35–50 MB por idioma; **alternativas evaluadas y no implementadas**: array ordenado + búsqueda binaria
por prefijo (menos memoria, escaneo del rango en consultas), DAWG/MARISA (compacto, complejidad). Proceso de construcción y licencias: [dictionaries.md](../dictionaries.md).

## 5. Word Flow

`captura → simplificación y normalización al cuadro de las teclas-letra → remuestreo a 32 puntos → candidatos → ranking → puerta de confianza`

- **Geometría real**: los centros se leen de los botones renderizados y trazo y centros se normalizan a la caja de las teclas-letra (orientación, altura, fila
  numérica y modo a una mano salen solos). Los mapas estáticos quedan como respaldo.
- **Candidatos**: todo el léxico filtrado por las 4 teclas más cercanas al inicio y al final (radio 0,18), unidas por frecuencia hasta 6.000, más las palabras aprendidas.
- **Rechazos baratos primero**: razón de longitud de trayecto 0,55–1,9; letras visitadas en orden ≥ 60 %; después DTW (ventana 8) + distancia euclídea.
- **Puntuación**: `forma = completitud·2,2 / (1 + dist·3,8)`; `+ 0,8·frecuencia/255`; `+ 1,2` si es palabra aprendida. Los pesos son los del decodificador previo y se
  **conservaron sin barrido ni optimización**: solo se calibró la puerta (`MIN_CONFIDENCE` = 1,55) con swipes vs. garabatos (swipes correctos p5 ≥ 1,72; garabatos máx ≈ 1,63).
- Decodificación en un hilo aparte; el resultado vuelve al hilo principal solo si la vista sigue adjunta y el idioma no cambió. Latencia JVM: media 5,6–9,6 ms.

No se implementó un modelo de lenguaje de contexto en el ranking del swipe.

## 6. Privacidad y datos

Resumen en [privacy.md](../privacy.md) y comprobaciones en [security-audit.md](security-audit.md). Formato de copia: [`BackupFormat`](../../app/src/main/java/com/metroboard/ime/dictionary/BackupFormat.kt).

## 7. Compatibilidad Android

- `minSdk 26`, `targetSdk/compileSdk 36`. Probado solo en API 37 (emulador).
- La ventana del IME desactiva `setDecorFitsSystemWindows` (API ≥ 30) y el teclado añade relleno inferior según `navigationBars`: el fondo continúa bajo los botones del sistema.
  La apariencia clara/oscura de los iconos de navegación sigue ahora al tema del teclado (API 30+ por `WindowInsetsController`, por bandera heredada en < 30; **la rama < 30 no se probó**).
- Los botones de ocultar y de cambiar de teclado los dibuja Android; MetroBoard no los oculta ni los sustituye.
