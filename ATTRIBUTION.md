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
- **Aporte / Inspiración**: Proyecto comunitario pionero de teclado para Android inspirado en Windows 10 Mobile. Proporcionó una valiosa referencia de arquitectura práctica para teclados estilo Metro sobre Android, y aportó el corpus base de frecuencias relativas para el idioma español (`es.txt`, 20.780 palabras con rangos 1..1000).
- **Agradecimiento**: Agradecemos profundamente a **ChiApps** por su dedicación al mantener vivo el espíritu del diseño Windows Phone en Android y por compartir sus datos y recursos con la comunidad de software libre.

### 3. Microsoft Hub Keyboard (Microsoft Garage, 2016)
- **Aporte / Inspiración**: Referencia arquitectónica histórica de teclado Android desarrollada bajo el programa experimental Microsoft Garage, analizada con fines de estudio de integración de portapapeles y herramientas de edición de texto.
- **Agradecimiento**: Al equipo de Microsoft Garage por sus experimentos públicos en la plataforma Android.

### 4. Spanish Wordlist (`xavier-hernandez/spanish-wordlist`)
- **Autor**: Xavier Hernández
- **Repositorio**: [https://github.com/xavier-hernandez/spanish-wordlist](https://github.com/xavier-hernandez/spanish-wordlist)
- **Licencia**: GNU Affero General Public License v3 (AGPL-3.0)
- **Aporte**: Lista léxica de 76.440 palabras en español, que se usa como vocabulario base del diccionario offline `app/src/main/assets/dictionaries/es.dict`. El archivo original viene en ISO-8859-1 con CRLF y sin frecuencias; el pipeline `tools/fetch_spanish_wordlist.py` lo transcodifica a UTF-8, normaliza los saltos de línea, filtra duplicados y le asigna frecuencias.
- **Agradecimiento**: Gracias a Xavier Hernández por compilar y publicar este listado bajo una licencia libre que garantiza el acceso abierto al conocimiento.

### 5. RLA-ES — Recursos Lingüísticos Abiertos para el Español (`sbosio/rla-es`)
- **Autor / Mantenedor**: Santiago Bosio y colaboradores
- **Repositorio**: [https://github.com/sbosio/rla-es](https://github.com/sbosio/rla-es)
- **Licencia**: GNU GPL / LGPL / MPL
- **Aporte**: El proyecto madre del que se derivaron los diccionarios ortográficos abiertos del español para LibreOffice, OpenOffice y Hunspell, y fuente original del catálogo de palabras del idioma español.
- **Agradecimiento**: Expresamos nuestra más profunda gratitud a **Santiago Bosio** y a todos los colaboradores de RLA-ES por décadas de trabajo voluntario defendiendo y construyendo herramientas lingüísticas libres para el idioma español.

### 6. SCOWL & Kevin's Word List / Peter Norvig Corpus
- **Autores**: Kevin Atkinson y Peter Norvig
- **Licencia**: Dominio Público / MIT
- **Aporte**: Listados de frecuencia y vocabulario en idioma inglés utilizados para el diccionario `en.dict`.
- **Agradecimiento**: A Kevin Atkinson por SCOWL y a Peter Norvig por su investigación pedagógica y abierta sobre frecuencias del lenguaje natural.

### 7. Lexique 3 & Wiktionary French
- **Autores**: Boris New, Christophe Pallier, Ludovic Ferrand, Rafael Matos
- **Sitio web**: [http://www.lexique.org/](http://www.lexique.org/)
- **Licencia**: Open Database License (ODbL) / CC BY-SA
- **Aporte**: Base de frecuencias y lemas para el diccionario en francés `fr.dict`.
- **Agradecimiento**: A los investigadores de Lexique 3 por poner a disposición de la comunidad científica datos psicocientíficos abiertos.

### 8. Leipzig Wortschatz Corpora & Wiktionary German
- **Institución**: Universität Leipzig (Abteilung Automatische Sprachverarbeitung)
- **Licencia**: Creative Commons Attribution 4.0 International (CC BY 4.0) / Public Domain
- **Aporte**: Frecuencias léxicas para el diccionario en alemán `de.dict`.
- **Agradecimiento**: A la Universidad de Leipzig por su labor en corpus multilingües de acceso abierto.

### 9. Android Open Source Project (AOSP)
- **Licencia**: Apache License 2.0
- **Aporte**: Arquitectura fundamental de `android.inputmethodservice.InputMethodService`, contratos de `InputConnection` y APIs del sistema operativo Android.
- **Agradecimiento**: A todos los ingenieros y contribuyentes de AOSP por proporcionar la base abierta que hace posible la existencia de teclados independientes en Android.

---

## 📜 Resumen de Licencias de Terceros

| Componente | Origen / Autor | Licencia | Ubicación en el Proyecto |
|---|---|---|---|
| **MetroBoard App** | MetroBoard Contributors | GNU AGPL-3.0 | `app/src/main/` |
| **Spanish Wordlist** | Xavier Hernández / Santiago Bosio | GNU AGPL-3.0 | `vendor/spanish-wordlist/`, `assets/dictionaries/es.dict` |
| **Frecuencias ChiBoard** | ChiApps | **Sin verificar** — ver `NOTICE` | `decompiled/chiboard/.../es.txt` |
| **SCOWL / Norvig (EN)** | Kevin Atkinson / Peter Norvig | Public Domain / MIT | `assets/dictionaries/en.dict` |
| **Lexique 3 (FR)** | B. New, C. Pallier et al. | ODbL / CC BY-SA | `assets/dictionaries/fr.dict` |
| **Leipzig Wortschatz (DE)**| Uni Leipzig | CC BY 4.0 | `assets/dictionaries/de.dict` |
| **Android Framework APIs**| AOSP (Google) | Apache 2.0 | `app/build.gradle.kts` |

---

## 🔓 Compromiso con el Software Libre

MetroBoard cree firmemente en la transparencia y en devolver a la comunidad más de lo que recibe. Toda persona tiene derecho a inspeccionar, compilar, modificar y redistribuir este software bajo las libertades que otorga la **GNU Affero General Public License v3**.
