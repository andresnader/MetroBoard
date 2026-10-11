# Análisis del corpus y trazabilidad

Archivo leído, sin modificar: `wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx`.
Fecha del corpus: 2026-10-10. Extracción directa de los XML del XLSX, incluyendo
todos los valores de todas las hojas. No se incorporaron assets externos.

## Las seis hojas

| Hoja | Resultado de la revisión |
|---|---|
| Behavioral Corpus | 15 comportamientos: 12 Confirmados y 3 Probables. Tabla detallada abajo. |
| Visual Direction | 12 elementos. Fondo oscuro, sans libre, sugerencias superiores, Shift evidente, &123 inferior izquierdo, emoji adyacente, espacio rotulado, punto, Enter contextual. Márgenes con confianza media; el trazo de shape writing se difiere. |
| Implementation Map | 10 entregas: P0 IME/Shift/layout/puntuación; P1 sugerencias/diccionario/emoticonos; P2 gestos/contactos/settings. La petición adelanta settings/feedback a v0.1 y difiere aprendizaje. |
| Sources | 6 referencias: dos primarias Microsoft, tres secundarias contemporáneas y un foro comunitario. Se conservan como procedencia de requisitos, no como licencias de assets. |
| Open Questions | 6 incógnitas: dimensiones, layouts por contexto, tiempos, autocorrección, idiomas e intensidad de feedback. Ninguna se declara resuelta por esta implementación. |
| README | Define propósito, escala de evidencia, alcance de investigación, exclusión de mediciones y assets propietarios y destino Kotlin/IME. El alcance de investigación es más amplio que la v0.1 solicitada. |

## Escala de evidencia

- **Confirmado:** así marcado en el corpus. Se puede reproducir el comportamiento
  documentado, sin inferir que se conocen todas las reglas o sus valores exactos.
- **Probable:** evidencia histórica incompleta. Se implementa cuando el usuario
  lo solicita, dejando clara la incertidumbre histórica.
- **Inferido:** decisiones de UX/ingeniería propias. El corpus define esta etiqueta,
  pero no la asigna explícitamente a ninguno de sus 15 comportamientos.
- **Desconocido:** aspectos pendientes de las preguntas abiertas. Tampoco hay
  filas Behavioral Corpus etiquetadas literalmente Desconocido; no se confunde
  ausencia de verificación con confirmación.

## Comportamientos

| ID / celda de estado | Estado del corpus | Decisión v0.1 |
|---|---|---|
| B001 / F4 | Confirmado, alta | Doble espacio → `. ` + AUTO_SHIFT. Timeout propio de 400 ms. |
| B002 / F5 | Confirmado, alta | Transformar selección con Shift queda fuera de esta fase. |
| B003 / F6 | Confirmado, alta | Shape writing diferido; interfaces preparadas. |
| B004 / F7 | Confirmado, alta | Autoespacio tras shape writing diferido junto con el decoder. |
| B005 / F8 | Confirmado, alta | Emoticonos contextuales diferidos. Botón placeholder de texto, sin contexto. |
| B006 / F9 | Confirmado, alta | Punto largo abre alternativas en un popup lineal propio. |
| B007 / F10 | Confirmado, alta | Componente reutilizable y pequeño conjunto de vocales/ñ; no cobertura completa. |
| B008 / F11 | Confirmado, alta | Se respeta el caret del host; no se replica. |
| B009 / F12 | Confirmado, alta | Contrato PersonalDictionary; aprendizaje desactivado en toda v0.1. |
| B010 / F13 | Confirmado, alta | Contrato ContactDictionaryProvider; sin permiso ni importación. |
| B011 / F14 | Confirmado, alta | Solo lista española de demostración, sin biblioteca histórica ni paquetes masivos. |
| B012 / F15 | Confirmado, media | Layout alfabético sin fila numérica; números en &123. |
| B013 / F16 | Probable, media | No se implementa mantener/deslizar &123; se alterna por toque. |
| B014 / F17 | Probable, media | No se implementa retorno automático configurable de emoji. |
| B015 / F18 | Probable, media | Los cuatro estados exigidos por el usuario se implementan. No se afirma réplica exacta histórica. |

## Decisiones propias y preguntas abiertas

| Pregunta | Sigue desconocido | Decisión propia (Inferido) |
|---|---|---|
| Q001 | Proporciones Lumia y separación exacta | Filas 54 dp retrato / 42 dp paisaje, separación 4 dp entre teclas, fila de sugerencias 40 dp. |
| Q002 | Layouts históricos de email, URL, teléfono y contraseña | Respetar inputType Android; layout numérico/teléfono/fecha y desactivar transformaciones en campos estructurados. |
| Q003 | Timeout exacto de Shift y espacio | 300 ms y 400 ms, constantes independientes y probadas. |
| Q004 | Umbrales originales de autocorrección | No autocorrección; prefijos locales y máximo 3 candidatos. |
| Q005 | Idiomas de shape writing y variaciones | Un subtipo español QWERTY; gestos diferidos. |
| Q006 | Feedback histórico exacto | HapticFeedbackConstants.KEYBOARD_TAP y AudioManager estándar; ambos opcionales y apagados inicialmente. |

Los colores #101010 / #303030 / blanco / #40B9E9, los trazos de los iconos y el
uso de fuentes Android son originales. Visual Direction tiene confianza, pero
no una clasificación Confirmado/Probable por fila: no se eleva esa confianza a
una prueba de fidelidad de píxeles.

## Procedencia histórica conservada

Las referencias siguientes son las incluidas por el corpus. Su clasificación es
la del corpus; no se reabre aquí una investigación histórica ni se distribuyen
sus imágenes, PDF, fuentes o contenidos.

1. [Windows Experience Blog, 2014](https://blogs.windows.com/windowsexperience/2014/07/01/5-tips-for-tip-top-typing-on-windows-phone/): B001–B007.
2. [Lumia 430 Reviewers Guide](https://news.microsoft.com/wp-content/uploads/prod/2016/04/Lumia_430-ReviewersGuide.pdf): B009–B011.
3. [Windows Central, caret](https://www.windowscentral.com/new-word-flow-keyboard-caret-windows-phone-81): B008.
4. [Windows Central, review](https://www.windowscentral.com/windows-phone-81-review): integración histórica de predicción y shape writing.
5. [Laptop Mag review](https://www.laptopmag.com/reviews/smartphones/windows-phone-8-1): B012.
6. [Foro Windows Central](https://forums.windowscentral.com/threads/wp8-1-tips-tricks.275061/): B013/B014, evidencia comunitaria pendiente de validación.

B015 además enlaza en su propia fila
[Windows Central sobre capitalización](https://www.windowscentral.com/capitalize-words-easily-windows-phone-81).
Esta séptima URL no tiene fila propia en Sources; se conserva sin ocultar esa diferencia.
