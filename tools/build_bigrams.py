#!/usr/bin/env python3
"""Builds the next-word lists (`assets/dictionaries/<lang>.nextwords`) from the experimental AOSP word lists
published by Helium314 (https://codeberg.org/Helium314/aosp-dictionaries), which are made from the Leipzig
Corpora Collection (news and web text; "source lists under CC BY 4.0" according to that repository).

  python3 tools/build_bigrams.py --fetch     # download the four .combined lists into vendor/dict-sources/helium
  python3 tools/build_bigrams.py             # build the four .nextwords files and docs/nextwords-build-report.md
  python3 tools/build_bigrams.py --check     # validate the generated files

The source lists give at most three next words per word, ranked 1..3 (no counts). Only pairs whose two words
are in OUR dictionary (`<lang>.dict`, spell-checked) are kept, so web noise, numbers and foreign tokens never
reach the keyboard. A word typed in the middle of a sentence ("el") is a better predictor than the same word
at the start of one ("El"), so the lower-case entry is used first and the capitalised one only fills the gaps.

Output, one line per word: `word<TAB>next1<TAB>next2<TAB>next3` (1 to 3 next words, best first).
Run with `python3 -I` (the downloaded files are untrusted data).
"""
import argparse
import datetime
import hashlib
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets/dictionaries"
SOURCES = ROOT / "vendor/dict-sources/helium"
REPORT = ROOT / "docs/nextwords-build-report.md"
BASE = "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists_experimental/"
FILES = {"es": "main_es.combined", "en": "main_en_US.combined", "fr": "main_fr.combined", "de": "main_de.combined"}
MAX_NEXT = 3

WORD = re.compile(r"^ word=(.+?),f=(\d+)")
BIGRAM = re.compile(r"^  bigram=(.+?),f=(\d+)")
CLEAN = re.compile(r"^[^\W\d_]+(?:['’\-][^\W\d_]+)*$", re.UNICODE)  # letters, with inner apostrophes/hyphens


def fetch() -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    for name in FILES.values():
        target = SOURCES / name
        if target.exists():
            print(f"have {name}")
            continue
        print(f"downloading {name} ...")
        with urllib.request.urlopen(BASE + name, timeout=300) as response:
            target.write_bytes(response.read())


def load_dictionary(lang: str) -> dict:
    """lower-case word -> (display form, frequency) for the best spelling in `<lang>.dict`."""
    best = {}
    for line in (ASSETS / f"{lang}.dict").read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        word, freq = parts[0], int(parts[1])
        key = word.lower()
        if key not in best or freq > best[key][1]:
            best[key] = (word, freq)
    return best


def read_source(path: Path):
    """Yields (word, its f, [(next word, rank)...]) for every entry of the .combined list."""
    word, freq, nexts = None, 0, []
    with path.open(encoding="utf-8", errors="replace") as handle:
        for line in handle:
            m = WORD.match(line)
            if m:
                if word is not None and nexts:
                    yield word, freq, nexts
                word, freq, nexts = m.group(1), int(m.group(2)), []
                continue
            m = BIGRAM.match(line)
            if m and word is not None:
                nexts.append((m.group(1), int(m.group(2))))
    if word is not None and nexts:
        yield word, freq, nexts


def build(lang: str) -> tuple:
    dictionary = load_dictionary(lang)
    # word (lower) -> {"lower": [...], "capital": [...]} candidate next words in rank order
    pools = {}
    for word, _freq, nexts in read_source(SOURCES / FILES[lang]):
        key = word.lower()
        if key not in dictionary or not CLEAN.match(word):
            continue
        slot = "lower" if word == key else "capital"
        ranked = [n for n, _r in sorted(nexts, key=lambda pair: pair[1])]
        pools.setdefault(key, {"lower": [], "capital": []})[slot].extend(ranked)

    lines = []
    for key, pool in pools.items():
        chosen, seen = [], {key}
        for candidate in pool["lower"] + pool["capital"]:
            low = candidate.lower()
            if low in seen or low not in dictionary or not CLEAN.match(candidate):
                continue
            seen.add(low)
            chosen.append(dictionary[low][0])
            if len(chosen) == MAX_NEXT:
                break
        if chosen:
            lines.append((dictionary[key][1], dictionary[key][0], chosen))
    # Most frequent words first, ties alphabetical: deterministic output.
    lines.sort(key=lambda row: (-row[0], row[1].lower()))
    text = "".join(f"{word}\t" + "\t".join(chosen) + "\n" for _f, word, chosen in lines)
    return text, len(lines), sum(len(c) for _f, _w, c in lines)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.fetch:
        fetch()
        return 0
    if args.check:
        bad = 0
        for lang in FILES:
            path = ASSETS / f"{lang}.nextwords"
            dictionary = load_dictionary(lang)
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                parts = line.split("\t")
                if not 2 <= len(parts) <= 1 + MAX_NEXT or parts[0].lower() not in dictionary or any(
                        p.lower() not in dictionary for p in parts[1:]):
                    print(f"{path.name}:{number}: bad line {line!r}")
                    bad += 1
        print("ok" if not bad else f"{bad} bad lines")
        return 1 if bad else 0

    rows = []
    for lang in FILES:
        source = SOURCES / FILES[lang]
        if not source.exists():
            print(f"missing {source}; run with --fetch first", file=sys.stderr)
            return 1
        text, words, pairs = build(lang)
        (ASSETS / f"{lang}.nextwords").write_bytes(text.encode("utf-8"))
        rows.append((lang, FILES[lang], sha256(source), words, pairs, len(text.encode("utf-8"))))
        print(f"{lang}: {words} words with next words, {pairs} pairs, {len(text.encode('utf-8')) // 1024} KiB")

    lines = [
        "# Next-word lists: build report",
        "",
        f"Generated by `tools/build_bigrams.py` on {datetime.date.today().isoformat()}. Do not edit by hand.",
        "",
        "Source: the experimental word lists of https://codeberg.org/Helium314/aosp-dictionaries, built from the Leipzig "
        "Corpora Collection (news and web text). The repository states: \"source lists under CC BY 4.0\". "
        "Attribution is in ATTRIBUTION.md.",
        "",
        "| Language | Source list | SHA-256 of the source | Words with next words | Pairs | Size |",
        "|---|---|---|---|---|---|",
    ]
    for lang, name, digest, words, pairs, size in rows:
        lines.append(f"| {lang} | `{name}` | `{digest[:16]}…` | {words:,} | {pairs:,} | {size // 1024} KiB |")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
