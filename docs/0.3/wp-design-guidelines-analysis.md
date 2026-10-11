# Análisis de «Windows Phone Design» (PDF) frente a MetroBoard

Documento analizado: `Windows phone 81. UI UX Design/Windows phone 8 Design.pdf` (52 diapositivas, 1536×897 pt, **solo imágenes**: no
tiene capa de texto, por lo que se leyó cada diapositiva renderizada y la tabla de colores a 216 ppp). **El PDF no se versiona en el
repositorio** (presentación de terceros). Aquí solo se citan datos factuales y reglas, no se reproduce el material.

## 1. Qué es y qué no es

- Presentación «Windows Phone Design» de **Maria Nasioti** (UX/UI Designer & Developer), fechada **18 de junio de 2013** en el pie de cada diapositiva.
  Estructura anunciada: *Visuals · Layout · Motion & Transitions*.
- Es una guía de **diseño de aplicaciones** de la era Windows Phone 8. **No contiene nada sobre teclados**: ni Word Flow, ni tamaños de tecla,
  ni la barra de sugerencias, ni el comportamiento de Shift, ni sonidos o vibración del teclado. Tampoco menciona Windows Phone 8.1
  (que salió en 2014): sus referencias son WP 7.5 y WP 8.
- Por tanto **no responde** ninguna de las preguntas abiertas del corpus (Q001–Q006). Sirve para la **identidad visual y la pantalla de ajustes**,
  no para el comportamiento de escritura.

## 2. Contenido diapositiva a diapositiva

| Págs. | Sección | Contenido |
|---|---|---|
| 1–3 | Portada | Título, autora, índice (Visuals / Layout / Motion & Transitions). La 2 es una diapositiva azul vacía |
| 4–7 | Visuals | Sección · **Themes** (oscuro y claro, con acento) · **Accent Brushes** (naranja, rosa, azul) · **Colors** (tabla de acentos, ver §3.2) |
| 8–9 | Visuals | **Themed Controls & Tiles** (casillas, botones de opción, barra de progreso y AppBar con tema marrón/cian/violeta) · *Theme Howto* |
| 10–13 | Visuals | **Iconography** (iconos geométricos simples dentro de un círculo; frente a iconos con volumen) · **Imagery** · *Icons & Imagery Howto* |
| 14–17 | Visuals | **Fonts** (Segoe WP Semi-Light/Regular/Semi-bold/Black) · **Custom Fonts** · *Fonts Howto* · Globalización (RTL, texto vertical, localización) |
| 18–20 | Layout | Sección · **Resolutions** (WVGA 480×800 15:9, WXGA 768×1280 15:9, 720p 720×1280 16:9) · *Graphic Resolution Howto* |
| 21–28 | Layout | **The Grid** (cita de Josef Müller-Brockmann; rejillas horizontales, verticales y de 2/3/4 columnas) · *Use Grid everywhere* · ejemplos · *Grid Howto* |
| 29 | Layout | **Pages**: barra de estado, espacio de la app, **AppBar de 72 px** |
| 30–34 | Layout | **Panorama** (anatomía, paneles −2…2) · *Panorama Howto* · ejemplos |
| 35–38 | Layout | **Pivots** (ejemplos) · *Pivots Howto* (reglas) · ejemplos · *Basic Page* |
| 39–41 | Layout | **AppBar** (botones de icono 0–4, expansor «…», etiquetas, elementos de menú) · variantes de color · **Structure** (inicio → splash → panorama → pivot → páginas) |
| 42–47 | Motion & Transitions | Sección · *Why Motion?* · *It's like a movie* · **Transitions** (entrar/salir, adelante/atrás) · *Rules* · *Motions HowTo* |
| 48–52 | Cierre | Ejemplos (pantalla de bloqueo, mosaicos, aplicación de Facebook) y agradecimiento |

## 3. Datos y reglas extraídas

### 3.1 Temas
Probar siempre en oscuro **y** claro; con temas del sistema, usar un esquema de color básico; usar sin miedo los pinceles de acento para que la app parezca nativa.

### 3.2 Los 20 colores de acento (diapositiva «Colors»)
Marcados en la tabla como disponibles en **Windows Phone 8** los 20; en 7.5 solo 10 (Lime, Green, Teal, Cyan, Violet, Pink, Magenta, Red, Orange, Brown).

| Color | Hex | | Color | Hex |
|---|---|---|---|---|
| Lime | `#A4C400` | | Magenta | `#D80073` |
| Green | `#60A917` | | Crimson | `#A20025` |
| Emerald | `#008A00` | | Red | `#E51400` |
| Teal | `#00ABA9` | | Orange | `#FA6800` |
| Cyan | `#1BA1E2` | | Amber | `#F0A30A` |
| Cobalt | `#0050EF` | | Yellow | `#D8C100` |
| Indigo | `#6A00FF` | | Brown | `#825A2C` |
| Violet | `#AA00FF` | | Olive | `#6D8764` |
| Pink | `#F472D0` | | Steel | `#647687` |
| | | | Mauve | `#76608A` |
| | | | Sienna | `#7A3B3F` |

**Erratas de la propia diapositiva**: la columna RGB de *Pink* dice G=141 (el hex `F472D0` implica 114) y la de *Yellow* dice G=139 (el hex `D8C100`
implica 193). Se muestreó el color de cada muestra del render: concuerda con el **hex**, así que el hex es el dato fiable. Los valores en azul de la tabla son los de WP 7.5 (p. ej. Lime `#A2C139`, Orange «Mango» `#F09609`).

### 3.3 Tipografía
Segoe WP en cuatro pesos; **evitar tamaños menores de 15 pt**; con texto coloreado, alto contraste en tamaños pequeños; fuentes alternativas con moderación (Segoe es parte integral de la experiencia).

### 3.4 Iconos
Cuando el texto o la imagen no basten, usar **iconos geométricos simples** en lugar de imágenes o texto extra. «Ir en simple y geométrico; no tener miedo a ir en grande; apilar imágenes con contenido; añadir ruido al degradado para evitar *banding*».

### 3.5 Diseño
- **Resoluciones**: soportar las tres (480×800, 768×1280, 720×1280).
- **Rejilla**: «una ayuda, no una garantía»; crear orden, ritmo y cadencia, y luego romperla para que el contenido destaque. **El PDF no da cifras** (ni unidad, ni márgenes, ni tamaño de celda).
- **AppBar**: **72 px**, **0 a 4 botones de icono**, expansor «…», etiquetas bajo los iconos, elementos de menú al expandir.
- **Panorama**: vista de conjunto, nivel superior solo con datos y navegación, AppBar mínima, solo vertical, **5 paneles o menos**, devolver al usuario al panel donde lo dejó.
- **Pivot**: solo datos de tipo similar, **AppBar completa**, **7 paneles o menos**, nunca un pivot dentro de otro pivot o de un panorama, sin elementos de scroll como mapas o barras, **encabezado de 1–2 palabras**, **nunca un control de edición dentro de un pivot**.

### 3.6 Movimiento
Deleita, añade interacción, personalidad, coherencia y elegancia; «una película de acción no es 100 % acción» (momentos altos y bajos); las transiciones las dispara el usuario y
son direccionales (entrar/salir, adelante/atrás); «si no cambió, no lo muevas»; **menos de 500 ms es instantáneo**; usar *easing*; para procesos largos, dar retroalimentación.

## 4. Aplicación a MetroBoard

| Regla del PDF | Estado en MetroBoard 0.3 | Evidencia / acción |
|---|---|---|
| Probar en oscuro y claro | **Aplicada tras encontrar dos fallos**: iconos de navegación del IME blancos sobre blanco (ya corregido antes) y **iconos de la barra de estado de Ajustes invisibles** en tema claro | Capturas del emulador antes/después; `syncSystemBarAppearance` en el servicio y en `SettingsActivity` |
| Acentos oficiales | **Corregido**: la paleta tenía 10 colores con valores de Windows 10 (p. ej. Cyan `#00A4EF`, Lime `#BAD80A`); ahora son **los 20 de WP8 con su hex exacto** en cuadrícula 5×4. El acento por defecto es *Cyan* `#1BA1E2` (el comentario del código lo decía, pero el valor real era `#00A49F`) | `AccentPaletteTest` (3), captura de la cuadrícula |
| Iconos geométricos simples | **Corregido**: el botón de portapapeles era el emoji 📋 a color; ahora un glifo plano monocromo tintado como las teclas, con descripción accesible en español (antes «Clipboard» fijo en inglés). Shift/Backspace/Enter ya eran geométricos | Captura en tema claro |
| Fuentes ≥ 15 pt | **No cumplido** en Ajustes: textos de ayuda de 12–14 sp (p. ej. la nota de Shift, la nota de privacidad). Teclas 16–23 sp, sugerencias 18 sp | **Sin cambiar**, decisión pendiente (ver §5) |
| Segoe WP | No se distribuye (propietaria); se usa la fuente ligera del sistema | `ATTRIBUTION.md` ya lo declara |
| Pivot ≤ 7 paneles | **No cumplido**: 8 (languages, typing, layout, appearance, sound & haptics, gestures, privacy, about). Es la estructura que pidió el propio prompt de 0.3 | **Sin cambiar**; decisión pendiente |
| Encabezados de 1–2 palabras | `sound & haptics` son 3 elementos | Sin cambiar |
| Nunca un control de edición en un pivot | **No cumplido**: el campo «pruébalo» está dentro del pivot *typing* | Sin cambiar; propuesta en §5 |
| AppBar de 72 px con 0–4 iconos | Ajustes no usa AppBar | No aplica ahora; opcional |
| Resoluciones WVGA/WXGA/720p | Android usa dp/sp y cualquier densidad | No aplica |
| Rejilla / márgenes | **No se pueden derivar**: el PDF no da números | La pregunta Q001 del corpus sigue abierta |
| Movimiento < 500 ms; direccional; «si no cambió, no lo muevas» | El pivote se desliza con ajuste a página; las teclas dan respuesta inmediata; no hay otras animaciones | Sin cambios; no se auditó la duración del scroll del pivote |

## 5. Decisiones que dejo al propietario

1. **¿7 o 8 paneles?** Cumplir la guía implica fusionar dos secciones (por ejemplo *about* dentro de *privacy*, o *gestures* dentro de *layout*). El prompt pidió 8; no lo cambié.
2. **El campo «pruébalo» dentro del pivote** incumple «nunca un control de edición en un pivot». Opciones: moverlo a una página propia fuera del pivote o a una franja fija inferior. Hoy es lo que permite probar el teclado sin salir de Ajustes.
3. **Tamaño mínimo de texto**: subir los textos de ayuda de 12–14 sp a 15 sp puede desbordar etiquetas largas; habría que revisarlo con TalkBack y fuente aumentada.
4. **Fuente abierta parecida a Segoe**: Microsoft publica *Selawik* como alternativa libre métricamente compatible con Segoe UI; **no verifiqué su licencia ni la apariencia** frente a Segoe WP, así que no la he incorporado.

## 6. Lo que este PDF no aporta (sigue pendiente)

Dimensiones y proporciones de teclas y su separación (Q001), disposición por tipo de campo (Q002), tiempos del doble toque (Q003), reglas de autocorrección (Q004),
idiomas con *shape writing* (Q005) y vibración/sonido (Q006). Para ellas hacen falta capturas o vídeos del teclado real de Windows Phone 8.1.
