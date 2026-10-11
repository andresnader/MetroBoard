# MetroBoard 0.3 — Quality & Experience

Principio rector de la iteración: **el teclado debe escribir correctamente antes de intentar escribir de forma inteligente.**

| Documento | Contenido |
|---|---|
| [issue-matrix.md](issue-matrix.md) | Problemas verificados, causa raíz, corrección y verificación; y lo que **no** se resolvió |
| [technical-report.md](technical-report.md) | Ramas, sistema de entrada y su máquina de estados, predicción, diccionarios, Word Flow, compatibilidad |
| [ux-report.md](ux-report.md) | Observaciones del video, estado de cada comportamiento del corpus, diferencias con Windows Phone 8.1 |
| [qa-report.md](qa-report.md) | Ejecuciones, métricas, rendimiento, comprobaciones manuales y limitaciones |
| [security-audit.md](security-audit.md) | Comprobaciones reproducibles, hallazgos corregidos y riesgos residuales |
| [../dictionaries.md](../dictionaries.md), [../privacy.md](../privacy.md) | Datos y privacidad |

## Resumen ejecutivo

**Punto de partida.** El APK del video no era el del PR #1 sino una rama `main` local sin publicar; las dos líneas divergían y duplicaban piezas.
Se integraron en `feature/metroboard-0.3-quality` (sin tocar `main`).

**Lo más grave que se encontró** no estaba en la lista de síntomas: una letra doble rápida (`ww`, `oo`, `ll`) seleccionaba la palabra entera y la
letra siguiente la reemplazaba. Además, el motor mezclaba idiomas, en/fr/de no tenían diccionario real, las pulsaciones con dos pulgares se perdían o
salían en orden inverso, y Word Flow solo podía escribir las 500 palabras más frecuentes.

**Qué cambió.** Entrada multitáctil ordenada y sin pérdidas; un diccionario real de 60.000 palabras por idioma (reproducible y con licencias
documentadas); autocorrección conservadora (0,0–2,3 % de correcciones erróneas en el banco sintético) y reversible; Word Flow sobre todo el léxico
(Top-3 98–99 % en trazos sintéticos frente a 35–38 %); Backspace por carácter percibido; copia de seguridad validada sin portapapeles; ajustes que
ahora hacen lo que dicen.

**Qué no se puede afirmar.** Nada se ha probado en un teléfono real; las medidas de swipe son sintéticas; no se probaron TalkBack, horizontal, otras
versiones de Android ni navegación de tres botones. Quedan sin hacer los hitboxes separados, la predicción contextual real y la revisión legal de licencias.

## Decisiones que necesitan al propietario

1. **Licencias.** Las frecuencias son CC-BY-SA-4.0 y los diccionarios es/de GPL-3.0+. Según sus autores encajan con la AGPL del proyecto, pero conviene
   una revisión legal antes de publicar en una tienda. La licencia del proyecto **no** se cambió.
2. **Aprendizaje de palabras.** Está activado por defecto (como ya ocurría) y tiene interruptor. Si se prefiere opt-in, es un cambio de una línea.
3. **Publicación del commit local `3d4de42`**: viaja dentro de esta rama (el PR está apilado sobre el PR #1).
4. **El PDF «Windows phone 8 Design»** apareció en la raíz sin versionar; no se leyó ni se añadió al repositorio.
