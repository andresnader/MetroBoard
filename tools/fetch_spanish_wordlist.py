#!/usr/bin/env python3
"""Descarga y adapta el wordlist español de xavier-hernandez/spanish-wordlist.

El repo fuente publica `text/spanish_words.txt` con 76.440 palabras en
ISO-8859-1 y terminadores CRLF, una palabra por línea y sin frecuencias.
MetroBoard espera `palabra<TAB>frecuencia` en UTF-8, LF y sin BOM, con
frecuencias enteras 1..255 (mayor = más común).

Este script hace tres cosas que el volcado manual no hace:

1. **Transcodifica ISO-8859-1 -> UTF-8.** Sin `iconv` las 17.396 palabras con
   tilde quedan corruptas (`ábaco` -> `\\xbaco`), que es el fallo silencioso
   más grave: el archivo parece texto válido pero el trie indexa basura.
2. **Normaliza CRLF -> LF** y elimina el BOM, para que el diff del asset
   generado sea estable entre máquinas.
3. **Asigna frecuencias reales** cruzando con el corpus de rangos que ya está
   en el repo (`decompiled/chiboard/resources/assets/dicts/es.txt`), que
   aporta 8.777 palabras con rango 1..1000. Las palabras sin rango caen a una
   banda heurística inferior, determinista y sin red.

Uso:
    python3 tools/fetch_spanish_wordlist.py            # descarga y genera
    python3 tools/fetch_spanish_wordlist.py --check    # solo verifica el asset

El repo se clona en `vendor/spanish-wordlist/` (gitignored) y se fija al
commit `WORDLIST_COMMIT` para que la generación sea reproducible.
"""

from __future__ import annotations

import argparse
import io
import re
import subprocess
import sys
import tempfile
import unicodedata
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

WORDLIST_REPO = "https://github.com/xavier-hernandez/spanish-wordlist.git"
# Fijado para reproducibilidad. Sin esto, `master` puede cambiar el corpus
# bajo nuestros pies y el asset generado dejaría de ser determinista.
WORDLIST_COMMIT = "17df0ec"

VENDOR_DIR = REPO_ROOT / "vendor" / "spanish-wordlist"
WORDLIST_REL = Path("text") / "spanish_words.txt"
FREQUENCY_SOURCE = REPO_ROOT / "decompiled" / "chiboard" / "resources" / "assets" / "dicts" / "es.txt"
OUT_DICT = REPO_ROOT / "app" / "src" / "main" / "assets" / "dictionaries" / "es.dict"

# Banda de frecuencias. Las palabras con rango real ocupan la parte alta del
# rango 1..255; la cola larga usa la banda baja para que el ranking del trie y
# del shape-writer siga siendo monótono.
FREQ_BAND_CORPUS_LOW = 64
FREQ_BAND_CORPUS_HIGH = 255
FREQ_BAND_TAIL_LOW = 1
FREQ_BAND_TAIL_HIGH = 63

# El corpus de ChiBoard está en UTF-8 con BOM y separado por espacio simple;
# el wordlist está en ISO-8859-1 sin BOM. Se detectan y se normalizan ambos.
_BOM = "﻿"
_CORPUS_LINE = re.compile(r"^(?P<word>\S+)\s+(?P<rank>\d+)\s*$")


def log(msg: str) -> None:
    print(f"[fetch] {msg}", flush=True)


def fetch_repo() -> Path:
    """Clona el wordlist en vendor/ y lo deja en WORDLIST_COMMIT."""
    if VENDOR_DIR.exists():
        log(f"reutilizando {VENDOR_DIR.relative_to(REPO_ROOT)}")
    else:
        log(f"clonando {WORDLIST_REPO}")
        VENDOR_DIR.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["git", "clone", WORDLIST_REPO, str(VENDOR_DIR)],
            check=True,
        )

    head = subprocess.run(
        ["git", "-C", str(VENDOR_DIR), "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()

    if not head.startswith(WORDLIST_COMMIT):
        log(f"checkout {WORDLIST_COMMIT} (HEAD={head})")
        subprocess.run(
            ["git", "-C", str(VENDOR_DIR), "checkout", "--detach", WORDLIST_COMMIT],
            check=True,
        )
    return VENDOR_DIR


def read_wordlist(path: Path) -> list[str]:
    """Lee el wordlist decodificando ISO-8859-1 -> UTF-8 y limpiando CRLF."""
    raw = path.read_bytes()
    if raw.startswith(b"\xef\xbb\xbf"):
        log("wordlist: se elimina BOM UTF-8 inesperado")
        raw = raw[3:]

    # Decodificar de forma estricta para no enmascarar bytes inválidos.
    try:
        text = raw.decode("iso-8859-1")
    except UnicodeDecodeError as exc:  # pragma: no cover - latin-1 nunca falla
        raise SystemExit(f"no se pudo decodificar el wordlist: {exc}") from exc

    words: list[str] = []
    seen: set[str] = set()
    for lineno, line in enumerate(text.splitlines(), start=1):
        word = line.strip()
        if not word:
            continue
        if word in seen:
            continue
        seen.add(word)
        words.append(word)
        if lineno == 0:
            pass

    log(f"wordlist: {len(words)} palabras únicas decodificadas de ISO-8859-1")
    return words


def read_corpus_ranks(path: Path) -> dict[str, int]:
    """Lee el corpus de frecuencias (word -> rank, mayor = más común).

    Devuelve un diccionario vacío si el corpus no está disponible: el
    pipeline debe poder regenerar el asset en un clon limpio, donde
    `decompiled/` está en .gitignore. En ese caso todas las palabras caen a
    la banda heurística, que es peor pero no incorrecta, y se avisa en
    pantalla.
    """
    if not path.exists():
        log(f"AVISO: no existe el corpus de frecuencias ({path}).")
        log("       Es el único insumo que no se puede regenerar solo: requiere")
        log("       descomprimir apks/chiboard.chiapps.apk en decompiled/.")
        log("       Se procede solo con la banda heurística (rankings peores).")
        return {}

    ranks: dict[str, int] = {}
    skipped = 0
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.lstrip(_BOM).strip()
        match = _CORPUS_LINE.match(line)
        if not match:
            skipped += 1
            continue
        word = match.group("word")
        rank = int(match.group("rank"))
        prev = ranks.get(word)
        if prev is None or rank > prev:
            ranks[word] = rank

    log(f"corpus: {len(ranks)} palabras con rango real ({skipped} líneas descartadas)")
    return ranks


def tail_score(word: str) -> int:
    """Puntuación heurística 0..1000 para palabras sin rango de corpus.

    No pretende medir frecuencia real, solo ordenar la cola larga de forma
    estable y sensata: las palabras cortas y de sufijos productivejos son
    mucho más frecuentes en español que las formas largas y arcaicas
    (`fijándoseos`). El resultado se escala después a la banda baja 1..63.
    """
    n = len(word)

    # Base por longitud: en español la longitud de palabra se correlaciona
    # negativamente con frecuencia. Saturación en 4 letras.
    length_score = max(10, 1000 - 120 * (n - 2)) if n <= 9 else max(5, 100 - 90 * (n - 9))

    bonus = 0
    lowered = word.lower()
    if lowered.endswith(("ar", "er", "ir")) and n >= 5:
        bonus += 180  # infinitivos verbales,Altamente frecuentes
    if lowered.endswith(("ción", "dad", "mente", "ista", "ero")):
        bonus += 90   # sufijos nominales productivos
    if n <= 4:
        bonus += 120
    # Penaliza formas enclíticas/arcasmas muy largas que casi no se teclean.
    if n > 14:
        bonus -= 150

    return max(0, min(1000, length_score + bonus))


def assign_frequencies(words: list[str], ranks: dict[str, int]) -> list[tuple[str, int]]:
    """Devuelve (palabra, frecuencia) ordenada por frecuencia descendente.

    El archivo ya viene ordenado de más a menos frecuente porque
    `getTopWords()` lo consume truncado, así que el orden del archivo es parte
    del contrato, no un detalle cosmético.
    """
    if ranks:
        rmin = min(ranks.values())
        rmax = max(ranks.values())
        rspan = max(1, rmax - rmin)
    else:
        rmin = rmax = rspan = 0

    with_freq: list[tuple[str, int]] = []
    tail: list[str] = []

    for word in words:
        rank = ranks.get(word)
        if rank is not None:
            # Invierte el rango: en el corpus mayor rank = más común, y en
            # MetroBoard mayor freq = más común. Ninguno de los dos usa cero
            # como máximo, así que la inversión es lineal dentro de la banda.
            ratio = (rank - rmin) / rspan
            freq = round(FREQ_BAND_CORPUS_LOW + ratio * (FREQ_BAND_CORPUS_HIGH - FREQ_BAND_CORPUS_LOW))
            with_freq.append((word, freq))
        else:
            tail.append(word)

    scored_tail = sorted(
        ((w, tail_score(w)) for w in tail),
        key=lambda pair: (-pair[1], pair[0]),
    )

    tail_span = max(1, FREQ_BAND_TAIL_HIGH - FREQ_BAND_TAIL_LOW)
    top_tail = max((s for _, s in scored_tail), default=1)
    result = list(with_freq)
    for word, score in scored_tail:
        freq = round(FREQ_BAND_TAIL_LOW + (score / top_tail) * tail_span)
        result.append((word, max(FREQ_BAND_TAIL_LOW, min(FREQ_BAND_TAIL_HIGH, freq))))

    # Orden final: frecuencia descendente, y a igual frecuencia el orden
    # alfabético para que el asset sea byte-idéntico entre ejecuciones.
    result.sort(key=lambda pair: (-pair[1], pair[0]))
    return result


def write_dict(entries: list[tuple[str, int]], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    buf = io.StringIO()
    for word, freq in entries:
        buf.write(f"{word}\t{freq}\n")
    payload = buf.getvalue().encode("utf-8")
    out_path.write_bytes(payload)
    log(f"escrito {out_path.relative_to(REPO_ROOT)}: {len(entries)} palabras, {len(payload) / 1024:.0f} KiB")


def verify(out_path: Path) -> int:
    """Valida el asset generado contra el contrato del parser en Kotlin."""
    problems: list[str] = []
    data = out_path.read_bytes()

    if data.startswith(b"\xef\xbb\xbf"):
        problems.append("el archivo empieza con BOM UTF-8")
    if b"\r" in data:
        problems.append("el archivo contiene CR (se esperaba LF puro)")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        problems.append(f"no es UTF-8 válido: {exc}")
        text = data.decode("utf-8", errors="replace")

    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    freqs: list[int] = []
    for i, line in enumerate(lines, start=1):
        parts = line.split("\t")
        if len(parts) != 2:
            problems.append(f"línea {i}: se esperaban 2 campos tabulados, hay {len(parts)}")
            continue
        word, raw_freq = parts
        if not word:
            problems.append(f"línea {i}: palabra vacía")
        try:
            freqs.append(int(raw_freq))
        except ValueError:
            problems.append(f"línea {i}: frecuencia no entera: {raw_freq!r}")

    if freqs:
        lo, hi = min(freqs), max(freqs)
        if lo < 1 or hi > 255:
            problems.append(f"frecuencias fuera de 1..255 (min={lo}, max={hi})")
        log(f"verificación: {len(lines)} líneas, freq {lo}..{hi}")

    # Coherencia con lo que el trie espera: el orden del archivo es el orden
    # de `getTopWords()`, así que debe ser no creciente en frecuencia.
    for i in range(1, len(freqs)):
        if freqs[i] > freqs[i - 1]:
            problems.append(f"línea {i + 1}: frecuencia {freqs[i]} mayor que la anterior {freqs[i - 1]}")
            break

    if problems:
        for problem in problems[:20]:
            print(f"  FALLO: {problem}", file=sys.stderr)
        return 1

    log("verificación OK")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="no descarga ni genera; solo valida el asset existente",
    )
    args = parser.parse_args()

    if args.check:
        return verify(OUT_DICT)

    repo = fetch_repo()
    words = read_wordlist(repo / WORDLIST_REL)
    ranks = read_corpus_ranks(FREQUENCY_SOURCE)

    overlap = sum(1 for w in words if w in ranks)
    log(f"cobertura de frecuencias: {overlap}/{len(words)} palabras ({overlap / len(words):.1%})")

    entries = assign_frequencies(words, ranks)
    write_dict(entries, OUT_DICT)
    return verify(OUT_DICT)


if __name__ == "__main__":
    raise SystemExit(main())
