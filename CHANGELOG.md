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

## [0.4.1] - 2026-10-12 — Respuesta al reporte de pruebas en WhatsApp

Reporte `reporte_t_cnico_e_incidencias_teclado_android.md` (APK de la 0.4.0). Qué se encontró, qué se arregló y qué **no** se puede
afirmar todavía, en [`docs/0.4/README.md`](docs/0.4/README.md).

### Fixed
- **BUG-02, borrado mantenido descontrolado.** Repetía cada 60 ms desde la primera repetición (≈17 caracteres por segundo, fijo).
  Ahora espera (corto 250 / normal 400 / largo 600 ms), borra con suavidad los 8 primeros caracteres y luego acelera, a velocidad
  lenta, normal o rápida. Medido: mantener 1,5 s borra 11 caracteres (antes ≈18) y 2,5 s borra 23 (antes ≈35).

### Added
- **REQ-01:** el retardo de pulsación larga (corto 250 / normal 350 / largo 500 / muy largo 700 ms), el retardo inicial del
  borrado y su velocidad son ajustes (Ajustes ▸ Gestos), validados e incluidos en la copia de seguridad.
- **REQ-02:** *Gestionar portapapeles* (Ajustes ▸ Privacidad): interruptor del historial, **editar** el texto guardado, fijar, borrar
  y vaciar. En el teclado, mantener pulsado un clip abre esa pantalla sobre ese clip. (Ya existían interruptor, lista, fijar y borrar;
  faltaba editar.)
- **Diagnóstico que funciona en el APK de release** (Ajustes ▸ Acerca de, apagado por defecto): registra solo números en memoria
  (espera del toque, tiempo de cada tecla y de cada fase, bloqueos del hilo principal, tiempos de carga del diccionario, indicadores
  de los últimos campos) y «Copiar informe». Es la forma de medir en tu teléfono lo que el emulador no muestra.

### Performance
- Las mayúsculas automáticas se calculan con la copia local del texto: antes cada espacio hacía una llamada **síncrona** a la app
  (`getCursorCapsMode`), que espera al hilo de interfaz de WhatsApp. Una prueba instrumentada compara el resultado con el de la
  plataforma para 21 textos.
- El diccionario ocupa **un tercio menos de memoria** por idioma (trie con arreglos en lugar de un `HashMap` por nodo): 51 → 34 MB en
  la JVM; en el emulador el heap Java del teclado pasa de 54 a 43 MB. Menos recolección de basura y menos riesgo de que Android mate
  el proceso.

### Investigated, not changed
- **BUG-01, retraso y pulsaciones perdidas:** una prueba de estrés aleatoria (6 semillas × 120 pulsaciones, 6–14 por segundo, uno y dos
  pulgares con solapes y deriva) **no pierde ni reordena ninguna tecla** en la `KeyboardView` real, y en el emulador cada tecla cuesta
  2–4 ms. El retraso del video es, por tanto, esperas en tiempo real en el teléfono (probablemente llamadas síncronas a WhatsApp, el
  recolector de basura o carga del procesador) que solo se ven midiendo allí: usa el diagnóstico.
- **BUG-03, «no se inicia tras reiniciar»:** reproducido con un PIN en el emulador. Antes del primer desbloqueo el teclado **no está
  disponible** (solo Gboard, porque no declara arranque directo); tras desbloquear vuelve solo, sigue siendo el predeterminado y se
  muestra al instante. **No hace falta ningún `BroadcastReceiver` de `BOOT_COMPLETED`** (el sistema arranca la IME cuando hace falta)
  y añadirlo no cambiaría nada. Soportar el arranque directo exige mover preferencias y diccionario personal a almacenamiento
  protegido del dispositivo, con riesgo de perder datos si se hace mal: no se hizo a ciegas.

---

## [0.4.0] - 2026-10-12 — Más rápido, más inteligente

Desarrollo de Ameizin (Andrés Nader, info@ameizin.ec). Mediciones y método en [`docs/0.4/README.md`](docs/0.4/README.md).

### Fixed
- **Cierre inesperado al mantener la barra espaciadora** (`BadTokenException`). Un `AlertDialog` no puede mostrarse desde un
  teclado (no tiene token de ventana de actividad), así que la pulsación larga y la tecla del globo fallaban siempre. El selector
  de idioma es ahora un panel dentro del teclado. Reproducido y verificado en el emulador.
- **La fila inferior se solapaba con los botones del sistema** (flecha de ocultar y globo). Se rellenaba por la barra de
  navegación (63 px) y la franja real mide 126 px (`tappableElement`).
- **Popup de acentos:** se elegía con un toque nuevo y se quedaba abierto. Ahora se mantiene la tecla, se desliza hasta el acento
  y se suelta; soltar sin deslizar lo cierra sin escribir nada.
- **Sin predicción en apps con `IME_FLAG_NO_PERSONALIZED_LEARNING`** (muchas apps de IA y de privacidad lo usan). Ese flag solo
  prohíbe *aprender*; las sugerencias salen del diccionario fijo. **Cambia una decisión del commit inicial.** Los campos de
  contraseña siguen sin leerse.
- **Palabras borradas que reaparecían.** Ajustes y teclado abrían cada uno su copia del diccionario personal y la desactualizada
  reescribía el archivo. Ahora comparten una sola instancia.
- Caja «pruébalo» duplicada en *Acerca de*.

### Added
- **Editor del diccionario personal** (Ajustes ▸ Escritura): añadir, editar, borrar y buscar palabras por idioma. Una palabra
  añadida a mano se sugiere y se protege de la autocorrección desde el primer momento.
- **Vista previa en vivo en Apariencia:** el teclado real, fijo bajo los controles, cambia al instante con el tema, el acento y
  el tintado de teclas.
- **Tipos de campo:** correo (`@` en lugar del emoji), URL (`/` y `.com`; al mantener: `.net .org .edu .es .ec`), nombres de
  persona (mayúscula en cada palabra, sin autocorrección) y **sin escritura por deslizamiento** en contraseñas, direcciones y
  campos numéricos.
- **Siguiente palabra real:** unas 30.000 palabras por idioma con hasta tres siguientes (antes ~20), del Leipzig Corpora
  Collection vía Helium314/aosp-dictionaries (CC BY 4.0), filtradas contra nuestro diccionario. Noticias y web, así que funciona
  más allá del chat. `tools/build_bigrams.py` lo reproduce.
- **Word Flow con contexto:** la palabra anterior desempata entre trazos que encajan igual de bien. Acierto en primera posición
  (sintético, pares reales), sin → con contexto: es 88→93 %, fr 80→87 %, de 74→81 %, en 77→79 % con ruido de 0,03 del ancho del teclado.
- **Lo que acabas de copiar aparece en la barra de sugerencias,** listo para pegar (enlaces sin el `https://www.`), durante un
  minuto y solo en un campo vacío; nunca en contraseñas ni con el dispositivo bloqueado.
- **Firma de release** desde un `keystore.properties` ignorado por git: [`docs/release.md`](docs/release.md).
- Créditos de Ameizin en *Acerca de* y en el README. Fechas en el registro de cambios de la app.

### Performance (medido en el emulador; conviene repetirlo en un teléfono)
- Cada letra hacía **dos llamadas síncronas a la app** para leer el texto alrededor del cursor (1–31 ms cada una). Ahora hay una
  copia local, válida mientras todo lo que ocurre sea del teclado y descartada ante el primer cambio externo.
- La búsqueda de sugerencias, de hasta 80 ms con una palabra mal escrita, corre en otro hilo.
- Procesar una tecla, 98 pulsaciones de español real alternando versiones: mediana 6 → 2 ms, **p95 37 → 4 ms**, máximo 60 → 10 ms.
- Herramientas solo para builds *debug*: `adb logcat -s MB_LAT MB_PHASES MB_EDITOR`.

### No incluido todavía
- Imágenes y capturas de pantalla en el portapapeles (solo texto y enlaces).
- Cinemática del trazo (velocidad y curvatura) y un grabador de trazos reales para calibrar con datos de verdad.
- Importar diccionarios `.combined` desde la app y bigramas personales.
- Ayuda de IA con API propia (diseño recomendado: variante aparte, bajo demanda, sin permiso de Internet en la normal).
- Teclado radial en arco.

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
- **Word Flow: elegir la palabra correcta tras deslizar.** Antes, el trazo escribía la primera candidata y descartaba las demás;
  la barra pasaba a sugerir la *siguiente* palabra. Ahora las alternativas del trazo (hasta 3, la elegida en negrita al centro)
  quedan en la barra y tocar una **sustituye** la palabra recién escrita, con un solo espacio; se puede volver a cambiar. Solo se
  ofrecen mientras el texto antes del cursor sea exactamente esa palabra más un espacio: escribir, borrar, espacio, mover el
  cursor o una edición de la app anfitriona las descartan. 7 pruebas nuevas (`EditorControllerTest`) y comprobado en el
  emulador con un trazo real de varios segmentos («Tiene» → «Truene» → «Tiene»).
- **Popup de acentos con el tema y el acento del teclado.** Tenía colores fijos oscuros (en tema claro era un bloque negro y
  solo el borde seguía al acento). Ahora usa fondo, teclas y texto del tema, borde de acento y ficha pulsada en acento.
  Verificado en claro + verde y oscuro + magenta.
- **Medición de latencia por pulsación** (`KeystrokeLatencyTest`): sugerencias p95 0,8–1,6 ms y autocorrección p95 1,1–1,7 ms
  en JVM de portátil. **No es una medida en teléfono**; ver `docs/0.3/qa-report.md`.
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
