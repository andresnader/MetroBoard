# Atribución, Inspiración y Agradecimientos — MetroBoard

MetroBoard es un teclado independiente para Android (IME) desarrollado desde cero en Kotlin. Este proyecto es **100% de código abierto (Open Source)** y se distribuye bajo los términos de la **GNU Affero General Public License v3 (AGPL-3.0)**.

---

## 🌟 Declaración de Inspiración y Origen

MetroBoard nace con la vocación de recuperar y honrar la ergonomía, fluidez tipográfica y minimalismo visual que caracterizaron a los teclados de la era móvil de Windows Phone, adaptándolos a estándares modernos de Android con privacidad estricta (100% offline).

Este proyecto **no es un producto oficial de Microsoft** y no contiene código fuente, binarios propietarios, tipografías protegidas, iconos ni sonidos propietarios de Microsoft. Todo el código de la aplicación ha sido implementado limpiamente (clean-room) en Kotlin.

A lo largo de su concepción, desarrollo y recopilación léxica, MetroBoard se ha inspirado en el trabajo de múltiples proyectos, comunidades y autores extraordinarios, a quienes extendemos nuestro más sincero agradecimiento:

---

## 🤝 Proyectos y Trabajos Inspiradores

### 1. Microsoft Word Flow / Windows Phone 8.1 Keyboard
- **Aporte / Inspiración**: Dirección de diseño ergonómico, disposición minimalista tipo Metro UI, comportamiento de estados de Shift (`AUTO_SHIFT`, `ONE_SHOT`, `CAPS_LOCK`), puntuación rápida (doble espacio a punto y espacio), popups lineales para caracteres alternativos y concepto de shape writing.
- **Trazabilidad**: Analizado mediante el corpus de ingeniería inversa documentado en `wordflow_8_1_reverse_engineering_corpus_v0_1.xlsx`.
- **Agradecimiento**: Nuestro reconocimiento al equipo original de diseño e ingeniería de Windows Phone y Word Flow por establecer un hito histórico de elegancia tipográfica y velocidad de escritura en dispositivos móviles.

### 2. ChiBoard (ChiApps — `chiboard.chiapps`)
- **Aporte / Inspiración**: Proyecto comunitario pionero de teclado para Android inspirado en Windows 10 Mobile. Referencia de arquitectura práctica para teclados estilo Metro sobre Android y de organización de la pantalla de ajustes.
- **Nota (0.3)**: desde MetroBoard 0.3 **no se distribuye ningún dato derivado de ChiBoard**. Las versiones anteriores usaban sus rangos de frecuencia del español, extraídos de un APK propietario; esos datos fueron sustituidos por frecuencias de OpenSubtitles (ver 5 y 6).
- **Agradecimiento**: A **ChiApps** por mantener vivo el espíritu del diseño Windows Phone en Android.

### 3. Microsoft Hub Keyboard (Microsoft Garage, 2016)
- **Aporte / Inspiración**: Referencia arquitectónica histórica de teclado Android desarrollada bajo el programa experimental Microsoft Garage, analizada con fines de estudio de integración de portapapeles y herramientas de edición de texto.
- **Agradecimiento**: Al equipo de Microsoft Garage por sus experimentos públicos en la plataforma Android.

### 4. Diccionarios Hunspell de LibreOffice (validez de las palabras)
Repositorio: [LibreOffice/dictionaries](https://github.com/LibreOffice/dictionaries). Se consultan solo en desarrollo
(`tools/build_dictionaries.py`); el APK lleva únicamente las listas de palabras resultantes.
- **Español — RLA-ES / `es_EC`** (Santiago Bosio y colaboradores, [sbosio/rla-es](https://github.com/sbosio/rla-es)). Licencia: tri-licencia GPLv3+ / LGPLv3+ / MPL-1.1+; se elige **GPL-3.0-or-later**.
- **Inglés — `en_US`** (SCOWL, Kevin Atkinson). Licencia permisiva tipo MIT/BSD (ver `README_en_US.txt`).
- **Francés — «Dictionnaires orthographiques français» 7.7** (Olivier R. y colaboradores, [Grammalecte](https://grammalecte.net/)). Licencia **MPL-2.0**.
- **Alemán — `de_DE_frami`** (igerman98 de Björn Jacke; ampliación de Franz Michael Baumann). Licencia GPL v2 o v3; se elige **GPL-3.0-or-later**.
- **Agradecimiento**: a todas las personas que mantienen estos recursos lingüísticos libres.

### 5. FrequencyWords (hermitdave) sobre OpenSubtitles 2018 (frecuencias)
- **Autor**: Hermit Dave, con datos de [OpenSubtitles 2018](https://opus.nlpl.eu/OpenSubtitles2018.php) (OPUS).
- **Repositorio**: [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords)
- **Licencia**: código MIT; **contenido CC-BY-SA-4.0**. Los pesos de frecuencia de los cuatro `*.dict` derivan de esta fuente y por tanto conservan la atribución y la obligación ShareAlike (compatible en un solo sentido con GPLv3 según Creative Commons; pendiente de revisión legal antes de una tienda).
- **Agradecimiento**: a Hermit Dave y a los voluntarios de OpenSubtitles/OPUS.

### 6. Listas de las que MetroBoard 0.2 y anteriores dependían (ya no se usan)
- `xavier-hernandez/spanish-wordlist` (AGPL-3.0) y las frecuencias extraídas de Chi+Flow. Retiradas en 0.3; la sección de licencias del `NOTICE` explica el efecto.
- Esta versión **no usa** Lexique 3, Leipzig Wortschatz, Wiktionary ni el corpus de Norvig, aunque versiones anteriores de este documento los mencionaban: nunca llegaron a incorporarse.

### 9. Android Open Source Project (AOSP)
- **Licencia**: Apache License 2.0
- **Aporte**: Arquitectura fundamental de `android.inputmethodservice.InputMethodService`, contratos de `InputConnection` y APIs del sistema operativo Android.
- **Agradecimiento**: A todos los ingenieros y contribuyentes de AOSP por proporcionar la base abierta que hace posible la existencia de teclados independientes en Android.

---

## 📜 Resumen de Licencias de Terceros

| Componente | Origen / Autor | Licencia | Ubicación en el Proyecto |
|---|---|---|---|
| **MetroBoard App** | MetroBoard Contributors | GNU AGPL-3.0-or-later | `app/src/main/` |
| **Validez ES** | RLA-ES / `es_EC` | GPL-3.0+ (de GPLv3+/LGPLv3+/MPL-1.1+) | `assets/dictionaries/es.dict` (derivado) |
| **Validez EN** | SCOWL / `en_US` | permisiva tipo MIT/BSD | `assets/dictionaries/en.dict` (derivado) |
| **Validez FR** | Grammalecte, Olivier R. | MPL-2.0 | `assets/dictionaries/fr.dict` (derivado) |
| **Validez DE** | igerman98 / `de_DE_frami` | GPL v2 o v3 → v3 | `assets/dictionaries/de.dict` (derivado) |
| **Frecuencias (4 idiomas)** | FrequencyWords / OpenSubtitles 2018 | CC-BY-SA-4.0 | pesos de los 4 `*.dict` |
| **Android Framework APIs** | AOSP (Google) | Apache 2.0 | `app/build.gradle.kts` |

---

## 🔓 Compromiso con el Software Libre

MetroBoard cree firmemente en la transparencia y en devolver a la comunidad más de lo que recibe. Toda persona tiene derecho a inspeccionar, compilar, modificar y redistribuir este software bajo las libertades que otorga la **GNU Affero General Public License v3**.
