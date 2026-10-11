#!/usr/bin/env python3
# MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
# Copyright (C) 2026 MetroBoard Contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

"""
Generate offline open-source dictionary assets for MetroBoard / WindowsPhoneKeyboard.
Languages: Spanish (es), English (en), French (fr), German (de).
Formats:
  <word>\t<frequency>
  <typo>\t<correction>
  <word1>\t<word2>
  <word1>\t<word2>\t<word3>
Licensing:
  - English: Kevin's Word List / SCOWL & Norvig Corpus (Public Domain / MIT)
  - Spanish: Wiktionary frequency list & CREA (CC-BY-SA 3.0 / Public Domain)
  - French: Lexique 3 / Wiktionary frequency list (ODbL / CC-BY-SA compliant)
  - German: Wiktionary / Leipzig Wortschatz frequency list (CC-BY / Public Domain)
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DICT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "dictionaries"
DICT_DIR.mkdir(parents=True, exist_ok=True)

# Curated high-frequency seed vocabularies with frequency weights (1-255)
# Spanish
ES_WORDS = [
    ("de", 255), ("la", 254), ("que", 253), ("el", 252), ("en", 251), ("y", 250), ("a", 249), ("los", 248),
    ("se", 247), ("del", 246), ("las", 245), ("un", 244), ("por", 243), ("con", 242), ("no", 241), ("una", 240),
    ("su", 239), ("para", 238), ("es", 237), ("al", 236), ("lo", 235), ("como", 234), ("más", 233), ("o", 232),
    ("pero", 231), ("sus", 230), ("le", 229), ("ha", 228), ("me", 227), ("si", 226), ("sin", 225), ("sobre", 224),
    ("este", 223), ("ya", 222), ("entre", 221), ("cuando", 220), ("todo", 219), ("esta", 218), ("ser", 217),
    ("son", 216), ("dos", 215), ("también", 214), ("era", 213), ("muy", 212), ("hasta", 211), ("desde", 210),
    ("nos", 209), ("durante", 208), ("uno", 207), ("todos", 206), ("había", 205), ("bien", 204), ("tiempo", 203),
    ("hola", 202), ("gracias", 201), ("amigo", 200), ("casa", 199), ("día", 198), ("días", 197), ("hoy", 196),
    ("mañana", 195), ("tarde", 194), ("noche", 193), ("vida", 192), ("año", 191), ("años", 190), ("ahora", 189),
    ("después", 188), ("donde", 187), ("dónde", 186), ("cómo", 185), ("qué", 184), ("quién", 183), ("cuándo", 182),
    ("cuanto", 181), ("cuánto", 180), ("hacer", 179), ("puede", 178), ("puedo", 177), ("podemos", 176), ("quiero", 175),
    ("tenemos", 174), ("tienes", 173), ("tiene", 172), ("tengo", 171), ("está", 170), ("estoy", 169), ("estamos", 168),
    ("están", 167), ("vamos", 166), ("bueno", 165), ("buenos", 164), ("buenas", 163), ("favor", 162), ("mucho", 161),
    ("muchas", 160), ("hombre", 159), ("mujer", 158), ("niño", 157), ("niña", 156), ("trabajo", 155), ("mensaje", 154),
    ("teclado", 153), ("persona", 152), ("personas", 151), ("lugar", 150), ("lugares", 149), ("mundo", 148),
    ("hora", 147), ("horas", 146), ("siempre", 145), ("nunca", 144), ("nada", 143), ("algo", 142), ("alguien", 141),
    ("nadie", 140), ("claro", 139), ("seguro", 138), ("verdad", 137), ("saber", 136), ("decir", 135), ("ver", 134),
    ("dar", 133), ("llegar", 132), ("pasar", 131), ("poner", 130), ("quedar", 129), ("creer", 128), ("hablar", 127),
    ("llevar", 126), ("dejar", 125), ("seguir", 124), ("encontrar", 123), ("llamar", 122), ("venir", 121), ("pensar", 120),
    ("salir", 119), ("volver", 118), ("tomar", 117), ("conocer", 116), ("vivir", 115), ("sentir", 114), ("tratar", 113),
    ("mirar", 112), ("contar", 111), ("empezar", 110), ("esperar", 109), ("buscar", 108), ("entrar", 107), ("escribir", 106),
    ("perder", 105), ("producir", 104), ("entender", 103), ("pedir", 102), ("recordar", 101), ("terminar", 100),
    ("permitir", 99), ("aparecer", 98), ("conseguir", 97), ("comenzar", 96), ("servir", 95), ("sacar", 94),
    ("necesitar", 93), ("mantener", 92), ("resultar", 91), ("leer", 90), ("caer", 89), ("cambiar", 88),
    ("presentar", 87), ("crear", 86), ("abrir", 85), ("considerar", 84), ("oír", 83), ("acabar", 82),
    ("ganar", 81), ("formar", 80), ("partir", 79), ("morir", 78), ("aceptar", 77), ("realizar", 76),
    ("suponer", 75), ("comprender", 74), ("lograr", 73), ("explicar", 72), ("preguntar", 71), ("tocar", 70),
    ("reconocer", 69), ("alcanzar", 68), ("nacer", 67), ("dirigir", 66), ("correr", 65), ("utilizar", 64),
    ("pagar", 63), ("ayudar", 62), ("gustar", 61), ("jugar", 60), ("escuchar", 59), ("cumplir", 58),
    ("ofrecer", 57), ("descubrir", 56), ("levantar", 55), ("intentar", 54), ("usar", 53), ("amar", 52),
    ("comer", 51), ("beber", 50), ("dormir", 49), ("caminar", 48), ("viajar", 47), ("comprar", 46),
    ("vender", 45), ("aprender", 44), ("estudiar", 43), ("enseñar", 42), ("trabajar", 41), ("ayuda", 40),
    ("saludos", 39), ("pronto", 38), ("luego", 37), ("juntos", 36), ("mejor", 35), ("pequeño", 34),
    ("grande", 33), ("nuevo", 32), ("nueva", 31), ("viejo", 30), ("buena", 29), ("malo", 28),
    ("mala", 27), ("fácil", 26), ("difícil", 25), ("posible", 24), ("imposible", 23), ("feliz", 22),
    ("triste", 21), ("rápido", 20), ("lento", 19), ("cerca", 18), ("lejos", 17), ("fuerte", 16),
    ("camino", 15), ("ciudad", 14), ("familia", 14), ("amiga", 13), ("madre", 13), ("padre", 13),
    ("hijo", 12), ("hija", 12), ("hermano", 12), ("hermana", 12), ("amor", 11), ("corazón", 11),
    ("semana", 10), ("mes", 10), ("comida", 9), ("agua", 9), ("café", 9), ("música", 8),
    ("película", 8), ("libro", 8), ("foto", 8), ("viaje", 7), ("auto", 7), ("carro", 7),
    ("coche", 7), ("tren", 6), ("avión", 6), ("calle", 6), ("puerta", 6), ("ventana", 5),
    ("mesa", 5), ("silla", 5), ("cama", 5), ("dinero", 5), ("precio", 4), ("cuenta", 4),
    ("carta", 4), ("correo", 4), ("reunión", 4), ("llamada", 4), ("número", 3), ("nombre", 3)
]

# English
EN_WORDS = [
    ("the", 255), ("of", 254), ("and", 253), ("to", 252), ("a", 251), ("in", 250), ("is", 249), ("you", 248),
    ("that", 247), ("it", 246), ("he", 245), ("was", 244), ("for", 243), ("on", 242), ("are", 241), ("as", 240),
    ("with", 239), ("his", 238), ("they", 237), ("I", 236), ("at", 235), ("be", 234), ("this", 233), ("have", 232),
    ("from", 231), ("or", 230), ("one", 229), ("had", 228), ("by", 227), ("word", 226), ("but", 225), ("not", 224),
    ("what", 223), ("all", 222), ("were", 221), ("we", 220), ("when", 219), ("your", 218), ("can", 217), ("said", 216),
    ("there", 215), ("use", 214), ("an", 213), ("each", 212), ("which", 211), ("she", 210), ("do", 209), ("how", 208),
    ("their", 207), ("if", 206), ("will", 205), ("up", 204), ("other", 203), ("about", 202), ("out", 201), ("many", 200),
    ("then", 199), ("them", 198), ("these", 197), ("so", 196), ("some", 195), ("her", 194), ("would", 193), ("make", 192),
    ("like", 191), ("him", 190), ("into", 189), ("time", 188), ("has", 187), ("look", 186), ("two", 185), ("more", 184),
    ("write", 183), ("go", 182), ("see", 181), ("number", 180), ("no", 179), ("way", 178), ("could", 177), ("people", 176),
    ("my", 175), ("than", 174), ("first", 173), ("water", 172), ("been", 171), ("call", 170), ("who", 169), ("oil", 168),
    ("its", 167), ("now", 166), ("find", 165), ("long", 164), ("down", 163), ("day", 162), ("did", 161), ("get", 160),
    ("come", 159), ("made", 158), ("may", 157), ("part", 156), ("hello", 155), ("thanks", 154), ("good", 153),
    ("morning", 152), ("night", 151), ("keyboard", 150), ("message", 149), ("phone", 148), ("work", 147),
    ("today", 146), ("tomorrow", 145), ("yesterday", 144), ("great", 143), ("well", 142), ("much", 141),
    ("know", 140), ("think", 139), ("take", 138), ("come", 137), ("give", 136), ("tell", 135), ("say", 134),
    ("ask", 133), ("seem", 132), ("feel", 131), ("try", 130), ("leave", 129), ("call", 128), ("keep", 127),
    ("hold", 126), ("turn", 125), ("start", 124), ("show", 123), ("hear", 122), ("play", 121), ("run", 120),
    ("move", 119), ("like", 118), ("live", 117), ("believe", 116), ("bring", 115), ("happen", 114), ("must", 113),
    ("write", 112), ("provide", 111), ("sit", 110), ("stand", 109), ("lose", 108), ("pay", 107), ("meet", 106),
    ("include", 105), ("continue", 104), ("set", 103), ("learn", 102), ("change", 101), ("lead", 100), ("understand", 99),
    ("watch", 98), ("follow", 97), ("stop", 96), ("create", 95), ("speak", 94), ("read", 93), ("allow", 92),
    ("add", 91), ("spend", 90), ("grow", 89), ("open", 88), ("walk", 87), ("win", 86), ("offer", 85),
    ("remember", 84), ("love", 83), ("consider", 82), ("appear", 81), ("buy", 80), ("wait", 79), ("serve", 78),
    ("die", 77), ("send", 76), ("expect", 75), ("build", 74), ("stay", 73), ("fall", 72), ("cut", 71),
    ("reach", 70), ("kill", 69), ("remain", 68), ("suggest", 67), ("raise", 66), ("pass", 65), ("sell", 64),
    ("require", 63), ("report", 62), ("decide", 61), ("pull", 60), ("always", 59), ("never", 58), ("sometimes", 57),
    ("really", 56), ("again", 55), ("almost", 54), ("together", 53), ("often", 52), ("probably", 51), ("already", 50),
    ("friend", 49), ("family", 48), ("home", 47), ("house", 46), ("week", 45), ("month", 44), ("year", 43),
    ("food", 42), ("water", 41), ("coffee", 40), ("music", 39), ("book", 38), ("photo", 37), ("car", 36),
    ("road", 35), ("city", 34), ("place", 33), ("thing", 32), ("world", 31), ("life", 30), ("hand", 29)
]

# French
FR_WORDS = [
    ("de", 255), ("la", 254), ("le", 253), ("et", 252), ("les", 251), ("des", 250), ("en", 249), ("un", 248),
    ("du", 247), ("une", 246), ("que", 245), ("est", 244), ("pour", 243), ("qui", 242), ("dans", 241), ("a", 240),
    ("par", 239), ("sur", 238), ("au", 237), ("pas", 236), ("plus", 235), ("ce", 234), ("ne", 233), ("son", 232),
    ("avec", 231), ("il", 230), ("se", 229), ("ont", 228), ("ses", 227), ("mais", 226), ("ou", 225), ("como", 224),
    ("nous", 223), ("vous", 222), ("ils", 221), ("leur", 220), ("elle", 219), ("cette", 218), ("aussi", 217),
    ("tout", 216), ("faire", 215), ("bien", 214), ("peut", 213), ("sans", 212), ("deux", 211), ("été", 210),
    ("être", 209), ("avoir", 208), ("dire", 207), ("aller", 206), ("voir", 205), ("savoir", 204), ("pouvoir", 203),
    ("falloir", 202), ("vouloir", 201), ("bonjour", 200), ("merci", 199), ("oui", 198), ("non", 197), ("ami", 196),
    ("amie", 195), ("maison", 194), ("temps", 193), ("jour", 192), ("jours", 191), ("nuit", 190), ("matin", 189),
    ("soir", 188), ("aujourd'hui", 187), ("demain", 186), ("hier", 185), ("clavier", 184), ("message", 183),
    ("téléphone", 182), ("travail", 181), ("français", 180), ("très", 179), ("beaucoup", 178), ("toujours", 177),
    ("jamais", 176), ("rien", 175), ("chose", 174), ("homme", 173), ("femme", 172), ("enfant", 171), ("monde", 170),
    ("vie", 169), ("année", 168), ("années", 167), ("heure", 166), ("heures", 165), ("comment", 164), ("pourquoi", 163),
    ("quand", 162), ("où", 161), ("donc", 160), ("alors", 159), ("après", 158), ("avant", 157), ("encore", 156),
    ("autre", 155), ("autres", 154), ("même", 153), ("grand", 152), ("grande", 151), ("petit", 150), ("petite", 149),
    ("bon", 148), ("bonne", 147), ("nouveau", 146), ("nouvelle", 145), ("vieux", 144), ("premier", 143), ("première", 142),
    ("venir", 141), ("prendre", 140), ("arriver", 139), ("croire", 138), ("aimer", 137), ("passer", 136), ("mettre", 135),
    ("demander", 134), ("tenir", 133), ("sembler", 132), ("laisser", 131), ("rester", 130), ("penser", 129), ("entendre", 128),
    ("regarder", 127), ("rendre", 126), ("reconnaître", 125), ("quitter", 124), ("porter", 123), ("parler", 122),
    ("montrer", 121), ("continuer", 120), ("prier", 119), ("trouver", 118), ("donner", 117), ("comprendre", 116),
    ("connaître", 115), ("partir", 114), ("écrire", 113), ("suivre", 112), ("commencer", 111), ("vivre", 110),
    ("lire", 109), ("ouvrir", 108), ("marcher", 107), ("attendre", 106), ("perdre", 105), ("finir", 104),
    ("salut", 103), ("au revoir", 102), ("bonne nuit", 101), ("bonne journée", 100), ("s'il vous plaît", 99),
    ("famille", 98), ("ville", 97), ("amour", 96), ("eau", 95), ("café", 94), ("rue", 93), ("train", 92)
]

# German
DE_WORDS = [
    ("der", 255), ("die", 254), ("und", 253), ("in", 252), ("den", 251), ("von", 250), ("zu", 249), ("das", 248),
    ("mit", 247), ("sich", 246), ("des", 245), ("auf", 244), ("für", 243), ("ist", 242), ("im", 241), ("dem", 240),
    ("nicht", 239), ("ein", 238), ("eine", 237), ("als", 236), ("auch", 235), ("es", 234), ("an", 233), ("werden", 232),
    ("aus", 231), ("er", 230), ("hat", 229), ("dass", 228), ("sie", 227), ("nach", 226), ("wird", 225), ("bei", 224),
    ("einer", 223), ("um", 222), ("am", 221), ("sind", 220), ("noch", 219), ("wie", 218), ("über", 217), ("einen", 216),
    ("so", 215), ("war", 214), ("haben", 213), ("nur", 212), ("oder", 211), ("aber", 210), ("vor", 209), ("zur", 208),
    ("bis", 207), ("mehr", 206), ("durch", 205), ("man", 204), ("sein", 203), ("wurde", 202), ("sei", 201), ("hallo", 200),
    ("danke", 199), ("bitte", 198), ("ja", 197), ("nein", 196), ("gut", 195), ("guten", 194), ("morgen", 193),
    ("tag", 192), ("abend", 191), ("nacht", 190), ("tastatur", 189), ("nachricht", 188), ("freund", 187), ("freundin", 186),
    ("haus", 185), ("zeit", 184), ("heute", 183), ("morgen", 182), ("gestern", 181), ("arbeit", 180), ("mensch", 179),
    ("menschen", 178), ("leben", 177), ("jahr", 176), ("jahre", 175), ("stunde", 174), ("stunden", 173), ("immer", 172),
    ("nie", 171), ("etwas", 170), ("nichts", 169), ("jemand", 168), ("niemand", 167), ("alles", 166), ("sehr", 165),
    ("viel", 164), ("viele", 163), ("groß", 162), ("klein", 161), ("neu", 160), ("alt", 159), ("schön", 158),
    ("wichtig", 157), ("einfach", 156), ("schwer", 155), ("möglich", 154), ("richtig", 153), ("falsch", 152),
    ("schnell", 151), ("langsam", 150), ("können", 149), ("kann", 148), ("müssen", 147), ("muss", 146), ("wollen", 145),
    ("will", 144), ("sollen", 143), ("soll", 142), ("möchten", 141), ("möchte", 140), ("gehen", 139), ("geht", 138),
    ("kommen", 137), ("kommt", 136), ("sehen", 135), ("sieht", 134), ("wissen", 133), ("weiß", 132), ("sagen", 131),
    ("sagt", 130), ("geben", 129), ("gibt", 128), ("finden", 127), ("findet", 126), ("bleiben", 125), ("bleibt", 124),
    ("liegen", 123), ("liegt", 122), ("stehen", 121), ("steht", 120), ("denken", 119), ("denkt", 118), ("nehmen", 117),
    ("nimmt", 116), ("tun", 115), ("tut", 114), ("glauben", 113), ("glaubt", 112), ("halten", 111), ("hält", 110),
    ("nennen", 109), ("nennt", 108), ("mögen", 107), ("mag", 106), ("zeigen", 105), ("zeigt", 104), ("führen", 103),
    ("führt", 102), ("sprechen", 101), ("spricht", 100), ("bringen", 99), ("bringt", 98), ("leben", 97), ("lebt", 96),
    ("fahren", 95), ("fährt", 94), ("fragen", 93), ("fragt", 92), ("verstehen", 91), ("versteht", 90), ("bekommen", 89),
    ("bekommt", 88), ("beginnen", 87), ("beginnt", 86), ("erzählen", 85), ("erzählt", 84), ("versuchen", 83),
    ("versucht", 82), ("schreiben", 81), ("schreibt", 80), ("arbeiten", 79), ("arbeitet", 78), ("brauchen", 77),
    ("braucht", 76), ("folgen", 75), ("folgt", 74), ("lernen", 73), ("lernt", 72), ("treffen", 71), ("trifft", 70),
    ("tschüss", 69), ("auf wiedersehen", 68), ("bitte schön", 67), ("vielen dank", 66), ("bis bald", 65), ("bis später", 64),
    ("familie", 63), ("stadt", 62), ("straße", 61), ("wasser", 60), ("kaffee", 59), ("auto", 58), ("buch", 57)
]

def write_dict(lang, word_list):
    path = DICT_DIR / f"{lang}.dict"
    if path.exists() and lang == "es":
        with open(path, "r", encoding="utf-8") as f:
            line_count = sum(1 for _ in f)
        if line_count > 1000:
            print(f"Preserving existing full dictionary ({line_count} words) at {path}")
            return
    with open(path, "w", encoding="utf-8") as f:
        for word, freq in word_list:
            f.write(f"{word}\t{freq}\n")
    print(f"Wrote {len(word_list)} words to {path}")

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
    write_dict("es", ES_WORDS)
    write_dict("en", EN_WORDS)
    write_dict("fr", FR_WORDS)
    write_dict("de", DE_WORDS)

    for lang in ["es", "en", "fr", "de"]:
        write_typos(lang, TYPOS[lang])
        write_bigrams(lang, BIGRAMS[lang])
        write_trigrams(lang, TRIGRAMS[lang])

if __name__ == "__main__":
    main()
