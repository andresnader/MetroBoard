#!/usr/bin/env python3
# MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
# Copyright (C) 2026 MetroBoard Contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

"""
Generate the small hand-curated companions of the word lists: `<lang>.typos`, `.bigrams`, `.trigrams`.

The word lists themselves (`<lang>.dict`) are built from open sources by tools/build_dictionaries.py;
this script deliberately never writes them (running it used to replace the real lists with seeds).
Formats:
  <typo>\t<correction>
  <word1>\t<word2>
  <word1>\t<word2>\t<word3>
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DICT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "dictionaries"
DICT_DIR.mkdir(parents=True, exist_ok=True)

TYPOS = {
    "es": [
        ("tambien", "también"), ("mas", "más"), ("dia", "día"), ("dias", "días"),
        ("esta", "está"), ("estas", "estás"), ("estan", "están"), ("aqui", "aquí"),
        ("alli", "allí"), ("facil", "fácil"), ("dificil", "difícil"), ("quiza", "quizá"),
        ("quizas", "quizás"), ("adios", "adiós"), ("corazon", "corazón"), ("cancion", "canción"),
        ("accion", "acción"), ("entonses", "entonces"), ("xfa", "por favor"), ("q", "que"),
        ("tb", "también"), ("xq", "porque"), ("k", "que")
    ],
    "en": [
        ("dont", "don't"), ("cant", "can't"), ("wont", "won't"), ("im", "I'm"),
        ("ive", "I've"), ("id", "I'd"), ("ill", "I'll"), ("youre", "you're"),
        ("theyre", "they're"), ("weve", "we've"), ("youve", "you've"), ("didnt", "didn't"),
        ("isnt", "isn't"), ("arent", "aren't"), ("wasnt", "wasn't"), ("werent", "weren't"),
        ("couldnt", "couldn't"), ("wouldnt", "wouldn't"), ("shouldnt", "shouldn't"),
        ("hasnt", "hasn't"), ("havent", "haven't"), ("teh", "the"), ("adn", "and"),
        ("taht", "that"), ("waht", "what"), ("wiht", "with"), ("tou", "you"),
        ("thsi", "this"), ("peopel", "people"), ("becuase", "because")
    ],
    "fr": [
        ("cest", "c'est"), ("jai", "j'ai"), ("tas", "t'as"), ("dun", "d'un"),
        ("dune", "d'une"), ("quon", "qu'on"), ("quil", "qu'il"), ("quils", "qu'ils"),
        ("deja", "déjà"), ("tres", "très"), ("meme", "même"), ("etre", "être"),
        ("francais", "français"), ("merciii", "merci"), ("bcp", "beaucoup"),
        ("stp", "s'il te plaît"), ("svp", "s'il vous plaît"), ("ajd", "aujourd'hui")
    ],
    "de": [
        ("uber", "über"), ("konnen", "können"), ("mussen", "müssen"), ("schon", "schön"),
        ("gross", "groß"), ("fur", "für"), ("spater", "später"), ("gehts", "geht's"),
        ("wiegehts", "wie geht's"), ("dankee", "danke"), ("bittee", "bitte"),
        ("tschuss", "tschüss"), ("mochte", "möchte"), ("wurde", "würde"), ("wahre", "wäre")
    ]
}

def write_typos(lang, typos_list):
    path = DICT_DIR / f"{lang}.typos"
    with open(path, "w", encoding="utf-8") as f:
        for typo, fix in typos_list:
            f.write(f"{typo}\t{fix}\n")
    print(f"Wrote {len(typos_list)} typos to {path}")

BIGRAMS = {
    "es": [
        ("gracias", "por"), ("muchas", "gracias"), ("buenos", "días"), ("buenas", "tardes"),
        ("buenas", "noches"), ("cómo", "estás"), ("cómo", "está"), ("por", "favor"),
        ("por", "ejemplo"), ("hasta", "luego"), ("hasta", "mañana"), ("hasta", "pronto"),
        ("hola", "cómo"), ("de", "la"), ("en", "el"), ("te", "quiero"), ("vamos", "a"),
        ("qué", "tal"), ("nos", "vemos"), ("de", "nada"), ("todo", "bien")
    ],
    "en": [
        ("thank", "you"), ("thanks", "for"), ("good", "morning"), ("good", "night"),
        ("how", "are"), ("see", "you"), ("let", "me"), ("what", "is"),
        ("where", "are"), ("take", "care"), ("you", "are"), ("nice", "to"), ("have", "a"),
        ("hope", "you"), ("look", "forward"), ("let's", "go"), ("talk", "to")
    ],
    "fr": [
        ("merci", "beaucoup"), ("s'il", "vous"), ("vous", "plaît"), ("bonjour", "comment"),
        ("bonne", "journée"), ("bonne", "nuit"), ("au", "revoir"), ("à", "bientôt"),
        ("comment", "allez"), ("je", "suis"), ("il", "y"), ("y", "a"), ("je", "vous"),
        ("s'il", "te"), ("te", "plaît")
    ],
    "de": [
        ("vielen", "dank"), ("danke", "schön"), ("bitte", "schön"), ("guten", "morgen"),
        ("guten", "tag"), ("guten", "abend"), ("gute", "nacht"), ("wie", "geht's"),
        ("bis", "bald"), ("bis", "später"), ("auf", "wiedersehen"), ("ich", "habe"), ("ich", "bin"),
        ("schön", "dich"), ("alles", "gute")
    ]
}

def write_bigrams(lang, bigrams_list):
    path = DICT_DIR / f"{lang}.bigrams"
    with open(path, "w", encoding="utf-8") as f:
        for w1, w2 in bigrams_list:
            f.write(f"{w1}\t{w2}\n")
    print(f"Wrote {len(bigrams_list)} bigrams to {path}")

# Trigrams: (word1, word2, word3) => word3 is candidate next word given word1 and word2
TRIGRAMS = {
    "es": [
        ("muchas", "gracias", "por"),
        ("muchas", "gracias", "de"),
        ("cómo", "estás", "hoy"),
        ("cómo", "está", "usted"),
        ("buenos", "días", "a"),
        ("buenos", "días", "amigo"),
        ("buenas", "tardes", "a"),
        ("buenas", "noches", "a"),
        ("por", "favor", "avísame"),
        ("por", "favor", "envíame"),
        ("hasta", "luego", "amigo"),
        ("hasta", "mañana", "amigo"),
        ("nos", "vemos", "luego"),
        ("nos", "vemos", "pronto"),
        ("qué", "tal", "todo"),
        ("qué", "tal", "estás"),
        ("de", "nada", "amigo"),
        ("vamos", "a", "ver"),
        ("vamos", "a", "hacer"),
        ("te", "mando", "un"),
        ("un", "fuerte", "abrazo"),
        ("en", "el", "trabajo"),
        ("estoy", "en", "camino"),
        ("ya", "voy", "para"),
        ("todo", "va", "bien"),
        ("espero", "que", "estés"),
        ("espero", "que", "tengas"),
        ("si", "tienes", "tiempo")
    ],
    "en": [
        ("thank", "you", "very"),
        ("thank", "you", "so"),
        ("thank", "you", "for"),
        ("thanks", "for", "the"),
        ("how", "are", "you"),
        ("hope", "you", "are"),
        ("see", "you", "later"),
        ("see", "you", "soon"),
        ("see", "you", "tomorrow"),
        ("let", "me", "know"),
        ("have", "a", "great"),
        ("have", "a", "good"),
        ("have", "a", "nice"),
        ("nice", "to", "meet"),
        ("what", "do", "you"),
        ("what", "are", "you"),
        ("where", "are", "you"),
        ("I", "am", "on"),
        ("I", "will", "be"),
        ("talk", "to", "you"),
        ("take", "care", "and"),
        ("looking", "forward", "to")
    ],
    "fr": [
        ("s'il", "vous", "plaît"),
        ("s'il", "te", "plaît"),
        ("merci", "beaucoup", "pour"),
        ("merci", "beaucoup", "de"),
        ("comment", "allez", "vous"),
        ("bonne", "journée", "à"),
        ("bonne", "soirée", "à"),
        ("à", "très", "bientôt"),
        ("je", "suis", "en"),
        ("il", "y", "a"),
        ("j'espère", "que", "tu"),
        ("j'espère", "que", "vous"),
        ("au", "plaisir", "de")
    ],
    "de": [
        ("vielen", "dank", "für"),
        ("vielen", "dank", "an"),
        ("wie", "geht", "es"),
        ("ich", "hoffe", "dass"),
        ("schön", "dich", "zu"),
        ("bis", "später", "mein"),
        ("alles", "gute", "zum"),
        ("ich", "bin", "auf"),
        ("geben", "sie", "mir"),
        ("ich", "freue", "mich"),
        ("auf", "jeden", "fall")
    ]
}

def write_trigrams(lang, trigrams_list):
    path = DICT_DIR / f"{lang}.trigrams"
    with open(path, "w", encoding="utf-8") as f:
        for w1, w2, w3 in trigrams_list:
            f.write(f"{w1}\t{w2}\t{w3}\n")
    print(f"Wrote {len(trigrams_list)} trigrams to {path}")

def main():
    for lang in ["es", "en", "fr", "de"]:
        write_typos(lang, TYPOS[lang])
        write_bigrams(lang, BIGRAMS[lang])
        write_trigrams(lang, TRIGRAMS[lang])

if __name__ == "__main__":
    main()
