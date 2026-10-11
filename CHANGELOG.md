# Changelog — MetroBoard

Todas las notas notables de cambios para MetroBoard se documentan en este archivo.
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Aviso de Inspiración y Código Abierto**:
> MetroBoard es un proyecto independiente de **software libre y código abierto (Open Source)** licenciado bajo la **GNU Affero General Public License v3 (AGPL-3.0)**.
> Este proyecto está inspirado en múltiples proyectos y trabajos pioneros, en particular el diseño visual Metro y la ergonomía del teclado Word Flow de Windows Phone 8.1, el teclado comunitario ChiBoard (ChiApps), los diccionarios Hunspell de LibreOffice (RLA-ES de Santiago Bosio y colaboradores, [sbosio/rla-es](https://github.com/sbosio/rla-es); SCOWL, Grammalecte, igerman98), la wordlist en español de Xavier Hernández (`xavier-hernandez/spanish-wordlist`, AGPL-3.0; usada en versiones anteriores y retirada en 0.3), las frecuencias de FrequencyWords/OpenSubtitles y el Android Open Source Project (AOSP).
> Expresamos nuestro sincero agradecimiento a cada uno de sus autores y comunidades por mantener vivos recursos libres e inspiradores.
> Para más detalles, consulta [ATTRIBUTION.md](ATTRIBUTION.md) y [LICENSE](LICENSE).

---

## [0.3.0] - 2026-10-11 — Quality & Experience

Rama `feature/metroboard-0.3-quality`, apilada sobre el PR #1. Integra además el commit local no publicado `3d4de42`
(el que contenía la app probada en el video). Cifras y método en [`docs/0.3/`](docs/0.3/README.md).

### Fixed (verificado con prueba y, donde se indica, en el emulador)
- **Dobles letras seleccionaban la palabra.** Pulsar la misma tecla dos veces en < 300 ms (`ww`, `oo`, `ll`, `ss`…) disparaba
  la «selección de palabra por doble toque» y la siguiente letra la reemplazaba (`www.google.com` → `w.ogle.Com`). Eliminado.
  Alternativa legítima: si el editor tiene una palabra seleccionada, la barra ofrece reemplazos.
- **Pulsaciones perdidas y desordenadas con dos pulgares.** Una pulsación con deriva intermedia no era tap ni swipe y se
  perdía; los taps salían en orden de *liberación*; el swipe arrancaba solo por distancia. Ahora hay cola ordenada por
  orden de pulsación (ventana acotada), evidencia mínima de intención (≥ 3 teclas distintas) y el estado se vacía al cancelar.
- **Idiomas mezclados.** El motor sugería/autocorregía/predecía con es y en sin importar el idioma activo y `setLanguage`
  no hacía nada. Cada idioma usa solo su diccionario.
- **Palabras válidas «corregidas» o rodeadas de basura** (`vamos → gamos, vanos`; `demoro → decoro, memoro`). La palabra
  válida frecuente se conserva como primer candidato; la corrección difusa exige un ganador claro y una palabra frecuente.
- **Barra de sugerencias:** el candidato principal estaba en la ranura *secundaria*; ahora va al centro, en negrita.
- **Autocorrección** reversible con Retroceso (sin volver a imponerse), sin tocar nombres propios, siglas, URL ni correos;
  contracciones (`don't`) y elisiones francesas (`c'est`) bien segmentadas; un espacio tras elegir una sugerencia.
- **Mayúsculas:** ya no se capitaliza justo después de `.` (`www.Google.com`); Shift sobre texto seleccionado alterna
  Palabra → PALABRA → palabra (corpus B002).
- **Backspace** borra un carácter percibido (emoji con tono de piel, banderas, familias ZWJ, tilde descompuesta).
- **Word Flow** solo alcanzaba las 500 palabras más frecuentes: `teclado`, `mensaje`, `keyboard`, `clavier`… no se
  podían deslizar. Ahora usa todo el léxico, la geometría real de las teclas, rechaza garabatos y decodifica fuera del
  hilo de interfaz.
- **Rendimiento/memoria:** el diccionario se cargaba en el hilo principal (0,5–7 s en el emulador) y nunca se descargaba
  (hasta 165 MB con 4 idiomas). Carga asíncrona, 2 idiomas en memoria y el motor calla mientras solo existe la muestra mínima.
- **Ajustes sin efecto:** *cursor en espacio*, *pulsación larga en espacio* y *popups de teclas* ahora funcionan; se quita
  *borde a borde*, que no hacía nada.
- **Tema claro:** el icono de cambio de teclado del sistema era blanco sobre blanco.
- **Pantalla «Acerca de»** mostraba una versión fija y licencias de fuentes que nunca se usaron.
- **Menú de Ajustes: título y versión arriba y «novedades» dentro de la app.** La cabecera muestra el nombre y la versión
  (tocar la versión lleva a *About*), y *About* incluye el registro de cambios de 0.3.0, 0.2.0 y 0.1.0 en los cuatro idiomas.
- **Portapapeles en vivo.** El botón de portapapeles abre un panel con los clips recientes (más reciente primero, fijados
  arriba): tocar inserta el texto **tal cual** (un clip ya no se pasa a mayúsculas por el Shift automático; esto también
  corrige el pegado antiguo), fijar lo conserva entre reinicios y la cruz lo borra. Se actualiza solo al copiar en otra app.
  Reglas de privacidad en `docs/privacy.md`: texto ≤ 2.000 caracteres, nada marcado como sensible, sin fijar solo en memoria y
  con caducidad de 1 h, sin historial sobre campos de contraseña, desactivable en Ajustes (y al desactivarlo se borra todo).
  **Cambia lo que decía la documentación anterior** («no se lee en segundo plano»). Activado por defecto: decisión del propietario.
- **Bloqueo de pantalla.** Una revisión de seguridad automática señaló (solo con títulos, sin detalle) fuga de información con
  el dispositivo bloqueado. El panel de clips, el pegado y el acceso a Ajustes ahora no hacen nada mientras
  `KeyguardManager.isKeyguardLocked` sea verdadero. Comprobado desbloqueado; el caso bloqueado no se ejercitó.
- **Barra de herramientas** (botón `⋯` junto a las sugerencias): tamaño rápido (altura y modo a una mano, aplicados en vivo) y
  acceso directo a Ajustes.
- **Popup de acentos mal colocado.** Al mantener pulsada una tecla el cuadro de acentos salía abajo, sobre la barra espaciadora,
  en lugar de encima de la tecla: `showAtLocation` mide desde la ventana del teclado y se le pasaba una Y de pantalla.
  Ahora se coloca respecto a esa ventana (`PopupPlacement`, 5 pruebas) y solo baja bajo la tecla si la *pantalla* no tiene
  sitio arriba. Verificado en el emulador con una tecla de la fila central, una de la fila superior y una en el borde derecho.
- **Encabezado de Ajustes no circular, en negrita y en inglés.** (1) El pivote era un scroll lineal acotado: no se podía pasar de
  la última sección a la primera ni al revés. Ahora es un carrusel (`MetroPivot`, `PivotMath`): al deslizar más allá del final se
  vuelve al principio y viceversa; los letreros rotan con el contenido (el que se acaba de pasar sale por la izquierda y vuelve por
  la derecha), tocar un letrero va por el camino más corto, y se conservan sección y scroll al girar. (2) La sección activa usaba
  `Typeface.BOLD`: ahora todos los letreros son del mismo peso ligero y la activa se distingue solo por el color. (3) Los letreros
  eran literales en inglés (`SOUND & HAPTICS`…) y varios textos de Ajustes estaban fijos en español: todo sale ahora de recursos.
  `values/` pasa a inglés (idioma por defecto para cualquier idioma sin traducción) y se añaden `values-es`, `values-fr` y
  `values-de`. El idioma de escritura inicial sigue al del dispositivo (inglés si no hay diccionario). 15 pruebas del anillo
  (`PivotMathTest`) y 3 del idioma por defecto. Verificado en el emulador en en/es/fr/de, vertical y horizontal.
  **Las traducciones fr/de son mías y no las ha revisado ningún hablante nativo.**
- **Pantalla de Ajustes inconsistente y sin memoria de sección.** (1) Cada cambio de opción llamaba a `recreate()` y la pantalla
  volvía a *languages*; ahora los selectores y interruptores se actualizan en sitio, y el cambio de tema, acento, importación o
  restablecer reconstruye las páginas conservando la sección y el scroll de cada una (también al rotar o cuando Android recrea la
  actividad: se guardan en el estado de instancia). (2) Había tres estilos de control (segmentos planos, `Switch` redondeado de Material,
  filas de texto sin aspecto de botón) y dos tamaños de letra por selector: ahora un único conjunto Metro (`SettingsControls.kt`:
  segmentos, interruptor rectangular, botón con contorno, campo «pruébalo» idéntico en *typing* y *about*) con 48 dp de alto y tipografía
  uniforme. (3) El texto del segmento seleccionado era siempre negro (ilegible sobre Cobalt, Indigo…): ahora negro o blanco según contraste
  con el acento (`KeyboardColors.onColor`, probado en los 20 acentos). (4) El campo de prueba de *about* era oscuro en tema claro, el
  borde de selección del acento era blanco sobre blanco, los diálogos seguían el tema del sistema y no el del teclado, y en Android < 15 la
  barra de estado seguía oscura con iconos oscuros: todo sigue ahora el tema del teclado. Los toasts «X seleccionado» se quitaron: el
  estado visible del segmento ya es la confirmación.
- **Directrices de Windows Phone** (análisis del PDF en [`docs/0.3/wp-design-guidelines-analysis.md`](docs/0.3/wp-design-guidelines-analysis.md)):
  la paleta tenía 10 colores con valores de Windows 10; ahora son los **20 acentos de WP8 con su hex exacto** (cuadrícula 5×4) y el acento por
  defecto es *Cyan* `#1BA1E2`; el botón de portapapeles era un emoji a color y ahora es un glifo geométrico plano (con descripción en español);
  en tema claro los iconos de la barra de estado de Ajustes eran invisibles.

### Added
- **Diccionarios reales de 60.000 palabras** para es/en/fr/de generados de forma reproducible
  (`tools/build_dictionaries.py`; Hunspell + FrequencyWords). Antes en/fr/de tenían ~200 palabras escritas a mano.
- `LanguagePack` (configuración por idioma: locale y regla de elisión).
- Ajuste **«Aprender palabras nuevas»**; el aprendizaje ya solo guarda palabras que no están en el diccionario, por idioma,
  y las sugiere a partir del segundo uso.
- **Copia de seguridad v2** por el Selector de archivos de Android, validada y acotada (sin portapapeles).
- Banco de métricas reproducible (autocorrección, predicción, Word Flow) y pruebas multitáctiles sobre la vista real.

### Changed
- Versión `0.3.0` (versionCode 3). `LocalSuggestionEngine`, `TouchInputStateMachine` y `WordFlowShapeDecoder` reescritos
  en partes; `TouchGestureArbiter` y el primer `LanguagePack` (PR #1) se retiraron por duplicar piezas de la rama integrada.

### Removed
- Frecuencias derivadas del APK propietario de Chi+Flow y la lista española AGPL de terceros; `tools/fetch_spanish_wordlist.py`.
- La exportación de ajustes y palabras al portapapeles.

### Corrections to earlier notes
- La nota de 0.2.0 sobre decodificación Word Flow «en sub-milisegundo» no se sostiene: con el léxico completo se midió
  una media de 6–12 ms en la JVM (peor caso 35–95 ms), por eso ahora corre fuera del hilo de interfaz.

### Known limitations
Ver [`docs/0.3/qa-report.md`](docs/0.3/qa-report.md#limitaciones): sin validación en hardware real, sin pruebas de TalkBack,
orientación horizontal, otras versiones de Android ni navegación de tres botones.

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
