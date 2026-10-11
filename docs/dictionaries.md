# Diccionarios offline — MetroBoard 0.3

El teclado instalado es 100 % offline. Todo lo que sigue se descarga **solo en desarrollo**, en
`vendor/dict-sources/` (ignorado por git), y los resultados viajan empaquetados en `assets/dictionaries/`.

## Qué se distribuye

| Archivo | Contenido | Origen |
|---|---|---|
| `<lang>.dict` | `palabra<TAB>frecuencia (1..255)`, ordenado por frecuencia, 60.000 palabras (es/en/fr/de) | `tools/build_dictionaries.py` |
| `<lang>.typos` | `errata<TAB>corrección` (contracciones, tildes, erratas comunes) | `tools/generate_dictionaries.py` (a mano) |
| `<lang>.bigrams`, `<lang>.trigrams` | siguiente palabra, **muy pocos pares (15–30 por idioma)** | `tools/generate_dictionaries.py` (a mano) |

Cifras exactas y huellas de las fuentes: [`dictionaries-build-report.md`](dictionaries-build-report.md) (generado).

## Cómo se construye (`tools/build_dictionaries.py`)

1. **Validez de la palabra**: diccionarios Hunspell de [LibreOffice/dictionaries](https://github.com/LibreOffice/dictionaries),
   consultados con el CLI `hunspell -l` (respeta reglas de afijos, flags largos y elisiones; `unmunch` se descartó porque
   estropea los diccionarios con `FLAG long`, como el francés). Si la forma en minúsculas es inválida se prueba capitalizada
   (nombres propios, `I'm`).
2. **Frecuencia**: [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords), lista 2018 (OpenSubtitles 2018).
   Se examinan los 200.000 tokens más frecuentes; la palabra se conserva si es válida; el peso es
   `255 · (1 − ln(rango) / ln(300000))`.
3. **Limpieza**: letras sueltas solo si son palabras reales del idioma; fragmentos de contracciones/elisiones excluidos
   (`don`, `ll`, `qu`…); abreviaturas de dos letras solo-capitalizadas descartadas.
4. **Extras curados** (`tools/dictionary_extras/`): las contracciones inglesas (el corpus las parte en fragmentos) y unas
   pocas elisiones francesas fijas.

```bash
python3 tools/build_dictionaries.py --fetch   # descarga fuentes (requiere red, solo desarrollo)
python3 tools/build_dictionaries.py           # construye los 4 .dict y el informe
python3 tools/build_dictionaries.py --check   # valida formato (UTF-8, LF, orden, duplicados)
```

Requiere `hunspell` instalado (`brew install hunspell`).

## Licencias de las fuentes (no es asesoramiento legal)

| Idioma | Fuente de validez | Licencia (opción elegida) |
|---|---|---|
| es | `es/es_EC` (RLA-ES, Santiago Bosio y colaboradores) | tri-licencia GPLv3+ / LGPLv3+ / MPL-1.1+ → **GPL-3.0-or-later** |
| en | `en/en_US` (SCOWL, Kevin Atkinson) | licencia permisiva tipo MIT/BSD (`README_en_US.txt`) |
| fr | `fr_FR` «Dictionnaires orthographiques français» v7.7 (Olivier R., Grammalecte) | **MPL-2.0** |
| de | `de/de_DE_frami` (igerman98, B. Jacke; F. M. Baumann) | GPL v2 o v3 → **GPL-3.0-or-later** |
| todos | frecuencias: FrequencyWords / OpenSubtitles 2018 | **CC-BY-SA-4.0** (contenido) |

Cómo encaja con la licencia del proyecto (AGPL-3.0-or-later): GPLv3 es compatible con AGPLv3 (§13); MPL-2.0 permite
combinar con (A)GPL; Creative Commons declara CC BY-SA 4.0 compatible en un solo sentido con GPLv3. **Esto requiere una
revisión legal antes de publicar en una tienda**, sobre todo la obligación ShareAlike de los datos de frecuencia derivados.
Con esto desaparece el material derivado de un APK propietario (frecuencias de Chi+Flow) que usaban las versiones
anteriores. Mientras se distribuyan los diccionarios es/de (GPL), el conjunto sigue siendo copyleft.

## Calidad medida (sobre estos datos)

Todas las cifras salen de pruebas reproducibles (`PredictionQualityTest`, `RealMultilingualPredictionTest`); ver
[`0.3/qa-report.md`](0.3/qa-report.md). En resumen: las palabras válidas frecuentes no se cambian ni se ocultan, la
autocorrección errónea sobre erratas sintéticas es 0,0–2,3 % por idioma, y la predicción por prefijo (4 letras) acierta
en el top-3 entre el 62,6 % (es) y el 81,1 % (en).

## Límites conocidos

- Las frecuencias vienen de subtítulos: sesgo hacia el habla coloquial y los diálogos; faltan registros formales y
  escritura de mensajería.
- Sin corpus de bigramas/trigramas: la predicción de la **siguiente palabra** apenas cubre unos cuantos pares.
- Sin variantes regionales: el español usa `es_EC` para la validez; no hay selección de variante.
- Regionalismos ecuatorianos y vocabulario técnico dependen de que aparezcan en Hunspell y en los subtítulos; el
  diccionario personal cubre el resto.
- Un trie de 60.000 palabras ocupa 35–50 MB por idioma (estructura basada en `HashMap`); el registro mantiene como
  máximo 2 idiomas en memoria. Un índice compacto (array ordenado) reduciría la memoria y es trabajo pendiente.
