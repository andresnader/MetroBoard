# Curated dictionary extras

`<lang>.extra` files (`word<TAB>frequency`, 1..255) are merged into the generated `<lang>.dict` by
`tools/build_dictionaries.py`. They hold entries the open sources cannot provide:

- `en.extra`: English contractions. FrequencyWords splits them into fragments ("don't" -> "don" + "t"),
  so the contraction itself never appears in the frequency list.

French elisions ("l'ami", "qu'il") are open-ended and are handled by tokenisation (LanguagePack.elidesApostrophe),
not by the dictionary.

These lists are original work of the MetroBoard contributors (AGPL-3.0-or-later).
