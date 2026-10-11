# Changelog — MetroBoard

Todas las notas notables de cambios para MetroBoard se documentan en este archivo.
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Aviso de Inspiración y Código Abierto**:
> MetroBoard es un proyecto independiente de **software libre y código abierto (Open Source)** licenciado bajo la **GNU Affero General Public License v3 (AGPL-3.0)**.
> Este proyecto está inspirado en múltiples proyectos y trabajos pioneros, en particular el diseño visual Metro y la ergonomía del teclado Word Flow de Windows Phone 8.1, el teclado comunitario ChiBoard (ChiApps), los corpus lingüísticos de Santiago Bosio ([sbosio/rla-es](https://github.com/sbosio/rla-es)) y Xavier Hernández ([xavier-hernandez/spanish-wordlist](https://github.com/xavier-hernandez/spanish-wordlist)), las listas léxicas de SCOWL/Norvig, Lexique 3 y Leipzig Wortschatz, y el Android Open Source Project (AOSP).
> Expresamos nuestro sincero agradecimiento a cada uno de sus autores y comunidades por mantener vivos recursos libres e inspiradores.
> Para más detalles, consulta [ATTRIBUTION.md](ATTRIBUTION.md) y [LICENSE](LICENSE).

---

## [0.2.0] - 2026-10-11

### Added
- **Word Flow Shape Writing con Dynamic Time Warping (DTW)**:
  - Algoritmo de alineación elástica matricial con banda de Sakoe-Chiba (`WordFlowShapeDecoder.kt`), tolerante a variaciones de velocidad, longitud y aceleración gestual.
  - Reconocimiento de curvatura, vértices angulares y filtrado de candidatos en tiempo real en sub-milisegundo.
- **Predicciones Contextuales N-gram (Trigramas y Bigramas)**:
  - Soporte completo para predicción basada en contexto de 2 palabras (`getTrigramSuggestions(w1, w2)`).
  - Diccionarios multilingües compilados con n-gramas contextuales (`.trigrams`) para español, inglés, francés y alemán.
- **Selector de Tonos de Piel Fitzpatrick para Emojis**:
  - Popup emergente estilo Windows Phone 8.1 al mantener pulsado cualquier emoji humano o gesto manual (`🏻`, `🏼`, `🏽`, `🏾`, `🏿`).
  - Detección precisa de modificadores de piel Unicode y soporte multi-categoría.
- **Gesto Swipe-to-Delete en tecla Retroceso (Backspace)**:
  - Deslizamiento rápido hacia la izquierda desde la tecla Backspace para borrar palabras completas en un solo trazo.
  - `TouchInputStateMachine` con estado dedicado `BACKSPACE_SWIPE_DELETE_ACTIVE` y umbral dinámico.
- **Modo a Una Mano Lumia 1520**:
  - Anclaje lateral izquierdo y derecho con barra de herramientas dock integrada (`KeyboardView.kt`).
  - Botón de alternancia y expansión rápida sin salir del teclado.
- **Motor de Temas Claro/Oscuro WP8.1 Minimalista**:
  - Tema Oscuro puro OLED (`#000000`) y Tema Claro puro blanco (`#FFFFFF`) con tipografía adaptativa y paleta histórica Lumia.
  - Soporte para Tema del Sistema y tinte dinámico de teclas opcional.
- **Compatibilidad con Teclados Físicos de Hardware**:
  - Soporte para entrada directa por teclado Bluetooth / USB / emulador en `WordFlowImeService.onKeyDown()`.
- **Importador de Diccionario de Usuario de Android**:
  - Migración local de palabras aprendidas desde `android.provider.UserDictionary` a `LocalPersonalDictionary` con un solo toque y retroalimentación háptica/toast.
- **Hápticos Modernos con Amplitud Graduada**:
  - Soporte para `VibrationEffect` con intensidades calibradas (Suave, Media, Fuerte) y banderas de anulación global.

---

## [0.1.0] - 2026-10-11

### Added
- **Núcleo IME Android**: Implementación completa de `InputMethodService` con metadatos XML, ciclo de vida robusto y compatibilidad desde Android 8.0 (API 26) hasta Android 16 (API 36).
- **Layout y Tipografía Metro**:
  - Disposición QWERTY minimalista sin fila numérica superior, con estética plana de alto contraste inspirada en Windows Phone 8.1.
  - Teclas rectangulares, etiquetas dinámicas y diseño tipográfico con soporte para pantalla completa y orientación horizontal.
  - Modos de símbolos (`&123`), símbolos adicionales (`=\<`) y retorno fluido a letras (`abc`).
  - Layout numérico, telefónico y de fecha dedicados para campos con `inputType` estructurado.
- **Gestión Avanzada de Shift**:
  - Máquina de estados explícita con 4 modos: `AUTO_SHIFT`, `ONE_SHOT_SHIFT`, `CAPS_LOCK` y `LOWERCASE`.
  - Doble toque en Shift (< 300 ms) para fijar Bloqueo de Mayúsculas.
  - Indicadores visuales Metro: fondo blanco activo, punto indicador para auto-shift y barra inferior para caps lock.
  - Detección contextual de inicio de oración y puntuación final (`.`, `!`, `?`).
- **Puntuación y Atajos Ergonómicos**:
  - Doble espacio en menos de 400 ms produce automáticamente punto y espacio (`. `) activando `AUTO_SHIFT`.
  - Pulsación prolongada en punto (`.`) despliega menú emergente con `, ? ! : ; …`.
  - Pulsación prolongada en vocales y consonantes para caracteres acentuados y `ñ`.
- **Sugerencias y Diccionarios Locales**:
  - Motor de sugerencias contextual `SuggestionEngine` y `CompactWordTrie` de alto rendimiento para búsqueda en sub-milisegundo en UI thread.
  - Diccionario offline 100% privado en español, inglés, francés y alemán.
- **Ajustes y Personalización**:
  - Pantalla de configuración con selector de colores de acento Lumia (20 tonalidades históricas).
  - Configuración opcional e independiente para vibración háptica y sonido de teclas (desactivados por defecto).
  - Guía integrada para habilitación y selección del método de entrada.
- **Privacidad Estricta**:
  - Teclado 100% offline sin permiso de `INTERNET`, sin telemetría, sin analíticas y sin captura de pulsaciones.
- **Infraestructura de Código Abierto**:
  - Licencia GNU Affero General Public License v3 (`LICENSE`).
  - Documento de créditos y agradecimientos detallados (`ATTRIBUTION.md`).
  - Documentación de privacidad (`docs/privacy.md`), trazabilidad (`docs/corpus-analysis.md`) y validación (`docs/validation.md`).

[Unreleased]: https://github.com/andresnader/MetroBoard/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/andresnader/MetroBoard/releases/tag/v0.1.0
