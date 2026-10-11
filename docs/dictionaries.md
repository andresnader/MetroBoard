# Documentación de Diccionarios Open Source — MetroBoard

Este documento detalla las fuentes, licencias, formatos y reglas lingüísticas de los diccionarios offline incluidos en el proyecto MetroBoard.

**Requisito de Privacidad**: El teclado es 100% offline. No se conecta a internet ni descarga datos en segundo plano. Todos los diccionarios están empaquetados localmente en los assets de la aplicación.

---

## 1. Español (`es`)

- **Vocabulario**: [xavier-hernandez/spanish-wordlist](https://github.com/xavier-hernandez/spanish-wordlist) — 76.440 palabras.
  - Upstream: [sbosio/rla-es](https://github.com/sbosio/rla-es) (Recursos Lingüísticos Abiertos del Español).
  - Commit fijado: `17df0ec`.
  - El archivo fuente viene en **ISO-8859-1 con CRLF**, no en UTF-8: sin transcodificar, las 17.396 palabras acentuadas quedan corruptas de forma silenciosa.
- **Licencia del vocabulario**: **GNU Affero General Public License v3** (AGPL-3.0). Es copyleft viral, y es el motivo de que MetroBoard se distribuya bajo AGPL. Ver [`NOTICE`](../NOTICE).
- **Frecuencias**: corpus de rangos de `decompiled/chiboard/resources/assets/dicts/es.txt` (20.780 palabras, rank 1..1000 donde mayor = más común). Cubre 8.777 de las 76.440 palabras (11,5 %) y el 90 % de las 200 más frecuentes.
  - **Riesgo abierto**: esos datos provienen de un APK propietario y su licencia no está verificada. Antes de publicar en una tienda de apps hay que sustituirlos por un corpus de licencia clara (p. ej. Leipzig, CC BY 4.0). El pipeline no cambia: solo `read_corpus_ranks()`.
  - Si el corpus no está disponible, el pipeline sigue funcionando con la banda heurística y avisa por pantalla.
- **Formato**: TSV UTF-8 sin BOM, terminador LF, dos campos: `word\tfrequency`, con pesos 1..255.
  - `word` nunca vacío; `frequency` entero en 1..255; orden no creciente en frecuencia.
  - El orden del archivo **es parte del contrato**: `getTopWords()` se consume truncado (el shape-writer pide 500) y esa lista es su pool de candidatos.
- **Bandas de frecuencia**: las palabras con rango real ocupan 64..255; la cola larga sin rango usa 1..63, ordenada por longitud y sufijos productivos.
- **Layout Asociado**: QWERTY con tecla dedicada **`ñ`** en la fila central (`asdfghjklñ`).
- **Etiqueta Espacio**: `espacio`.
- **Caracteres Específicos**: `ñ`, `á`, `é`, `í`, `ó`, `ú`, `ü`, `¡`, `¿`.
- **Reglas de Capitalización**: Oraciones inician con mayúscula; tras punto (`.`), signo de interrogación (`?`), exclamación (`!`) y nueva línea (`\n`).

### Regeneración

```bash
python3 tools/fetch_spanish_wordlist.py            # descarga, transcodifica y genera
python3 tools/fetch_spanish_wordlist.py --check    # solo valida el asset existente
```

El script clona el wordlist en `vendor/` (ignorado por git, fijado por commit), decodifica ISO-8859-1 → UTF-8, normaliza CRLF → LF, cruza las frecuencias y escribe el asset. La salida es byte-idéntica entre ejecuciones.

---

## 2. Inglés (`en`)

- **Fuente**: Kevin's Word List / SCOWL (Spell Checking Oriented Word Lists) y Peter Norvig Corpus of High Frequency English Words.
- **Licencia**: Public Domain / MIT License (Kevin Atkinson).
- **Versión**: SCOWL 2020.12.07 / Norvig v2.
- **Idioma**: English (en-US / en-GB).
- **Formato**: TSV compacto UTF-8 (`word\tfrequency`).
- **Layout Asociado**: QWERTY estándar de 9 teclas en fila media (`asdfghjkl`), con sangría simétrica de 0.5f.
- **Etiqueta Espacio**: `space`.
- **Contracciones Comunes**: `dont` → `don't`, `cant` → `can't`, `im` → `I'm`, `wont` → `won't`, `youre` → `you're`, etc.
- **Reglas de Capitalización**: Pronombres `I`, `I'm`, `I've`, `I'll`, `I'd` siempre en mayúscula.

> Nota: la lista actual de inglés es una lista pequeña generada a mano por `tools/generate_dictionaries.py`; la atribución de arriba describe el origen previsto, no el contenido real del asset. Conviene alinearla con una fuente real igual que se hizo con el español.

---

## 3. Francés (`fr`)

- **Fuente**: Lexique 3 (New, Pallier, Ferrand, Matos) & Wiktionary French Frequency Lists.
- **Licencia**: Open Database License (ODbL) / GNU GPL / CC BY-SA compatible open data.
- **Versión**: Lexique 3.83 / Wiktionary Fr 2023.
- **Idioma**: Français (fr-FR).
- **Formato**: TSV compacto UTF-8 (`word\tfrequency`).
- **Layout Asociado**: AZERTY estándar (`azertyuiop`, `qsdfghjklm`, `wxcvbn`).
- **Etiqueta Espacio**: `espace`.
- **Caracteres Específicos**: `é`, `è`, `ê`, `ë`, `à`, `â`, `î`, `ï`, `ô`, `ù`, `û`, `ü`, `ç`, `œ`, `æ`.
- **Contracciones Comunes**: `cest` → `c'est`, `jai` → `j'ai`, `dun` → `d'un`, `quil` → `qu'il`.

> Nota: el mismo desfase que en inglés — la atribución describe el origen previsto, no el asset actual.

---

## 4. Alemán (`de`)

- **Fuente**: Leipzig Wortschatz Corpora / Wiktionary German Frequency Lists.
- **Licencia**: Creative Commons Attribution 4.0 International (CC BY 4.0) / Public Domain.
- **Versión**: Leipzig 2023 / Wiktionary De v1.
- **Idioma**: Deutsch (de-DE).
- **Formato**: TSV compacto UTF-8 (`word\tfrequency`).
- **Layout Asociado**: QWERTZ estándar (`qwertzuiop`, `asdfghjkl`, `yxcvbnm`). 'Z' e 'Y' intercambiados según ergonomía alemana.
- **Etiqueta Espacio**: `Leerzeichen`.
- **Caracteres Específicos**: `ä`, `ö`, `ü`, `ß`.
- **Contracciones y Variantes**: `uber` → `über`, `konnen` → `können`, `mussen` → `müssen`, `gross` → `groß`.

> Nota: el mismo desfase que en inglés.

---

## 5. Arquitectura del Proveedor de Diccionario

- Interfaz: `DictionaryProvider` (`dictionary/DictionaryProvider.kt`).
- Implementación de Búsqueda: `CompactWordTrie` (árbol de prefijos con top-15 entradas cacheadas por nodo).
- Carga: `LanguageDictionaryRegistry` lee `assets/dictionaries/<lang>.{dict,typos,bigrams}` y hace *fallback* a una lista de respaldo si no hay `Context`.

### 5.1 Restricciones de escala

El diseño del trie está optimizado para diccionarios grandes, y hay tres cosas que lo condicionan:

- **Top-K por nodo, ordenado en todo momento.** `addTopEntry` localiza la posición por búsqueda binaria y solo desplaza memoria; no reordena la lista. La versión anterior llamaba a `sortByDescending` en cada nodo por cada palabra, lo que con 76.440 palabras sonaba a segundos de CPU en el hilo de UI. Medido: **154 ms** de construcción.
- **Búsqueda difusa acotada por trie.** `searchFuzzy` recorre el trie con una sola fila de programación dinámica por profundidad y poda cualquier subárbol que supere el umbral, en vez de recorrer las 76.440 palabras construyendo una matriz DP por candidata. Medido: **~15x más rápido**, y el resultado es equivalente salvo por las colisiones de normalización (ver abajo).
- **Anotación de recursos**: los diccionarios van en `assets/` sin `noCompress`, así que se descomprimen en cada carga. Con un diccionario español de 904 KiB es aceptable; si se multiplican los idiomas habría que evaluar `androidResources.noCompress`.

### 5.2 Colisiones de normalización

`normalize()` pliega tildes (`mexico` → `mexico`, `que` → `que`), así que formas acentuadas y no acentuadas comparten nodo de trie: **5.210 nodos colapsan 5.270 formas distintas**. El trie conserva la de mayor frecuencia, que en español tiende a ser la acentuada (`médico` sobre `medico`, `qué` no sobre `que` porque `que` es más frecuente). El ranking por prefijo nunca podrá devolver las dos a la vez.

### 5.3 Normalización de typos y bigrams

`.typos` y `.bigrams` se indexan **normalizados**, porque `autoCorrect()` y `getBigramSuggestions()` buscan con `normalize()`. Indexarlos en crudo dejaba inalcanzables todas las claves acentuadas (`cómo`, `estás`, `uber` → `über`).

### 5.4 Aislamiento entre idiomas

`LocalSuggestionEngine` consulta deliberadamente los diccionarios `es` y `en` para autocorrección y siguiente palabra, con independencia del idioma activo. La versión anterior de este documento afirmaba aislamiento estricto; la implementación actual hace mezcla cruzada, así que se documenta el comportamiento real.

---

## 6. Verificación

- `python3 tools/test-core-offline.py` — runner offline con `kotlinc` + `android.jar`. Cubre los módulos de lógica pura; las pruebas que dependen de recursos (`R.*`) o de `org.json` real las omite y avisa.
- `./gradlew :app:testDebugUnitTest` — cobertura completa, incluida instrumentación.
- `SpanishDictionaryTest` valida el contrato de formato del asset, el orden por frecuencia, el rendimiento del trie y la búsqueda difusa a escala real.
