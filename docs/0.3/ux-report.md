# Informe de UX — MetroBoard 0.3

Fuentes: el video del dispositivo (`test teclado.mp4`, 5 min, app = rama `main` local), capturas del emulador de esta iteración y el
corpus `wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx` (15 comportamientos, 6 preguntas abiertas). **No se hizo ninguna prueba de
usuario ni se usó un teléfono Windows Phone real.** Posteriormente se analizó por completo la presentación «Windows Phone Design» (52 diapositivas, 2013) que aportó el propietario:
ver [wp-design-guidelines-analysis.md](wp-design-guidelines-analysis.md). Es una guía de diseño de *aplicaciones*; no trata teclados.

## 1. Lo visto en el video y qué cambió

| Observación | Qué se vio | Qué se hizo |
|---|---|---|
| Identidad Metro | Tipografía ligera, teclas planas, encabezados grandes, navegación tipo pivote, 4 idiomas, distribuciones, temas | Conservada. No se rediseñó nada |
| Barra de sugerencias | El principal (negrita) era el **segundo** del ranking: `embargo \| **embajador** \| embarazo` | El mejor candidato va al centro: `Embajador \| **Embargo** \| Embarazo` (emulador) |
| Predicciones incoherentes | `decoro / memoro` para *demoro* | La palabra ahora existe y se conserva; sin vecinos de baja frecuencia |
| «Ver palabras aprendidas» | Lista de palabras comunes con contador (1) | Solo palabras nuevas, desde el 2.º uso; por idioma |
| Franja inferior | Chevrón y globo del sistema bajo las teclas | Es de Android. Se mantuvo el relleno; en tema claro el globo era blanco sobre blanco → corregido |
| Mayúsculas | Auto-shift y estados visibles correctos | Sin cambios visuales; se corrigió la capitalización tras `.` sin espacio |
| Ajustes | Pivotes languages/typing/layout/appearance/sound/gestures/privacy/about | Se quitó «Borde a borde» (no hacía nada); 3 interruptores ahora funcionan; se añadió «Aprender palabras nuevas» |
| About | «versión 0.2.0» fija y licencias de fuentes no usadas | Versión real (0.3.0) y fuentes reales |

## 2. Corpus histórico: estado de cada comportamiento

| ID | Comportamiento (estado del corpus) | MetroBoard 0.3 |
|---|---|---|
| B001 | Doble espacio → `. ` + mayúscula (Confirmado) | ✔ Implementado y probado (también tras `. `, `? `, `! `) |
| B002 | Shift sobre palabra seleccionada: Palabra → PALABRA (Confirmado) | ✔ **Nuevo en 0.3**, mantiene la selección; sobre la palabra recién escrita ya existía |
| B003 | Shape writing (Confirmado) | ✔ Rehecho (todo el léxico, geometría real). Precisión solo medida con trazos sintéticos |
| B004 | Autoespacio tras shape writing (Confirmado) | ✔ |
| B005 | Sugerencias de emoticonos por contexto (Confirmado) | ✘ No implementado |
| B006 | Mantener `.` → puntuación extendida (Confirmado) | ✔ (heredado; no auditado en 0.3) |
| B007 | Alternativas por pulsación larga (Confirmado) | ✔ Ajuste «popups» ahora real; posición exacta y selección por deslizamiento **no auditadas** |
| B008 | Caret arrastrable (Confirmado) | ◐ Aproximado con el cursor sobre la barra espaciadora; sin «círculo» propio |
| B009 | Aprendizaje de vocabulario (Confirmado) | ✔ Local, por idioma, desactivable, nunca en campos sensibles |
| B010 | Nombres de contactos (Confirmado) | ✘ No implementado (requeriría permiso; debería ser opt-in) |
| B011 | Biblioteca ampliada por idioma (Confirmado) | ✔ 60.000 palabras por idioma (antes solo es) |
| B012 | Sin fila numérica por defecto (Confirmado, media) | ✔ Opción desactivada por defecto |
| B013 | `&123` deslizar y soltar vuelve a letras (Probable) | ✘ No implementado |
| B014 | Volver a letras tras emoji (Probable) | ✘ No implementado como ajuste |
| B015 | Shift puntual y Caps Lock (Probable) | ✔ Estados explícitos, probados |

Preguntas abiertas del corpus (Q001–Q006: proporciones de teclas, layouts por tipo de campo, tiempos exactos del doble toque, reglas de
autocorrección, idiomas con shape writing, vibración/sonido): **no se respondieron** en esta iteración; los umbrales de 300 ms (Shift) y 400 ms
(doble espacio) siguen siendo estimaciones propias.

## 3. Diferencias deliberadas con Windows Phone 8.1

- **Privacidad y offline** por encima de la fidelidad: no hay nube, contactos ni telemetría.
- **Autocorrección deliberadamente conservadora**: se prefiere dejar un error a cambiar una palabra válida. No hay referencia histórica del nivel exacto (la pregunta Q004 del corpus sigue abierta).
- **Shape writing con puerta de confianza**: si el trazo no encaja con ninguna palabra, no inserta nada.
- **Reversión con Retroceso** y memoria del rechazo (práctica moderna, no documentada en el corpus).
- Geometría, color exacto, tipografía y sonidos propios; el corpus marca varios como inferencias.

## 4. Pendiente (UX)

Medir superficie táctil frente a la visual (V05) y separar `VisualKeyBounds` de `TouchTargetBounds`; probar con pulgares reales y distintas manos;
auditar popups y su posición; landscape y modo a una mano con Word Flow; TalkBack; tamaño de fuente aumentado; contraste de todos los elementos en
ambos temas (solo se corrigió el icono del sistema); sonidos propios; selección por deslizamiento en alternativas.
