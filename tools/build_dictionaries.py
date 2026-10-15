#!/usr/bin/env python3
# MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
# Copyright (C) 2026 MetroBoard Contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

"""
Build the offline word lists (`<lang>.dict`) shipped in the APK from open sources.

    word validity   Hunspell dictionaries (LibreOffice/dictionaries), checked with the `hunspell` CLI
    word frequency  hermitdave/FrequencyWords 2018 (OpenSubtitles 2018, CC-BY-SA-4.0)

A word is kept only if it is a valid form of the language (Hunspell) AND appears in the
frequency list; its frequency weight (1..255) comes from its rank:

    freq = round(255 * (1 - ln(rank) / ln(RANK_HORIZON)))

so rank 1 -> 255 and the weight decays logarithmically. RANK_HORIZON = 300000 puts weight 64
(the engine's "common word" floor, LocalSuggestionEngine.*_MIN_FREQUENCY) at about rank 12,000.

The app never touches the network. This script is the only place that downloads anything, and
only into `vendor/dict-sources/` (git-ignored). Typos / bigrams / trigrams are curated by hand
(`<lang>.typos`, `.bigrams`, `.trigrams`) and are not touched here.

Usage:
    python3 tools/build_dictionaries.py --fetch      # download sources into vendor/dict-sources
    python3 tools/build_dictionaries.py              # build assets + docs/dictionaries-build-report.md
    python3 tools/build_dictionaries.py --check      # validate the shipped assets only
"""
import argparse
import hashlib
import math
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "vendor" / "dict-sources"
OUT = REPO / "app" / "src" / "main" / "assets" / "dictionaries"
REPORT = REPO / "docs" / "dictionaries-build-report.md"

LO = "https://raw.githubusercontent.com/LibreOffice/dictionaries/master"
FW = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"

# language -> (hunspell stem relative to LO, local stem, hunspell encoding, license chosen)
LANGS = {
    "es": ("es/es_EC", "es/es_EC", "utf-8", "GPL-3.0-or-later (of GPLv3+/LGPLv3+/MPL-1.1+)"),
    "en": ("en/en_US", "en/en_US", "utf-8", "SCOWL permissive licence (MIT/BSD-like), see README_en_US.txt"),
    "fr": ("fr_FR/dictionaries/fr", "fr_FR/dictionaries/fr", "utf-8", "MPL-2.0"),
    "de": ("de/de_DE_frami", "de/de_DE_frami", "iso-8859-1", "GPL-3.0-or-later (of GPLv2/GPLv3)"),
}
EXTRA = ["es/LICENSE.md", "es/README_hunspell_es.txt", "en/README_en_US.txt",
         "de/README_de_DE_frami.txt", "fr_FR/dictionaries/README_dict_fr.txt"]

RANK_HORIZON = 300_000
MAX_WORDS = 60_000
CANDIDATE_LIMIT = 200_000  # most frequent tokens examined per language
# FrequencyWords is built from subtitles and keeps fragments of split contractions ("don't" -> "t"),
# so one-letter tokens are only accepted when they are real words of the language.
ALLOWED_SINGLE = {"es": {"a", "y", "o", "e", "u"}, "en": {"a", "I"}, "fr": {"a", "y", "à", "ô"}, "de": {"o"}}
EXTRAS = REPO / "tools" / "dictionary_extras"
# Fragments of contractions / elisions that subtitle tokenisation leaves behind ("don't" -> "don").
EXCLUDE = {
    "en": {"don", "isn", "aren", "didn", "doesn", "wasn", "couldn", "wouldn", "shouldn", "hasn", "haven",
           "hadn", "weren", "mustn", "needn", "ll", "ve", "re"},
    "fr": {"qu", "jusqu", "lorsqu", "puisqu", "quelqu"},
}
# Two-letter tokens that are only valid Capitalised are abbreviations/names, not words, except these.
SHORT_CAPITALISED_OK = {"en": {"mr", "dr", "ms"}, "de": {"ei"}}
WORD_RE = re.compile(r"^[^\W\d_]+(?:'[^\W\d_]+)*$", re.UNICODE)  # letters, apostrophe only between letters


def fetch():
    SRC.mkdir(parents=True, exist_ok=True)
    jobs = []
    for _, (hs, local, _, _) in LANGS.items():
        jobs += [f"{LO}/{hs}.aff", f"{LO}/{hs}.dic"]
    jobs += [f"{LO}/{p}" for p in EXTRA]
    for lang in LANGS:
        jobs.append(f"{FW}/{lang}/{lang}_full.txt")
    jobs.append("https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/README.md")
    for url in jobs:
        dest = SRC / (url.split("/master/")[1].replace("content/2018/", "freq/") if "/master/" in url else url)
        dest.parent.mkdir(parents=True, exist_ok=True)
        print("fetch", url)
        urllib.request.urlretrieve(url, dest)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def hunspell_invalid(stem: str, words):
    """Words Hunspell rejects. Uses the real checker (affix rules, long flags, elision) via -l."""
    proc = subprocess.run(
        ["hunspell", "-d", str(SRC / stem), "-i", "utf-8", "-l"],
        input="\n".join(words).encode("utf-8"), capture_output=True, check=True)
    return set(proc.stdout.decode("utf-8", errors="replace").splitlines())


def build(lang: str):
    hs, stem, enc, _ = LANGS[lang]
    freq_file = SRC / "freq" / lang / f"{lang}_full.txt"
    if not freq_file.exists():
        freq_file = SRC / f"{lang}_full.txt"

    # Candidate tokens in frequency order (first spelling wins, counts are not needed any more).
    candidates, seen = [], set()
    for line in freq_file.read_text(encoding="utf-8").splitlines():
        parts = line.rsplit(" ", 1)
        if len(parts) != 2 or not parts[1].isdigit():
            continue
        tok = parts[0].replace("\u2019", "'").lower()
        if tok in seen or not WORD_RE.match(tok):
            continue
        if len(tok) == 1 and tok not in {c.lower() for c in ALLOWED_SINGLE[lang]}:
            continue
        if tok in EXCLUDE.get(lang, ()):
            continue
        seen.add(tok)
        candidates.append(tok)
        if len(candidates) >= CANDIDATE_LIMIT:
            break

    # Pass 1: as written (lowercase). Pass 2: Capitalised, for proper nouns and "I'm"-style forms.
    bad1 = hunspell_invalid(stem, candidates)
    retry = [t for t in candidates if t in bad1]
    capitalised = {t: t[0].upper() + t[1:] for t in retry}
    bad2 = hunspell_invalid(stem, list(capitalised.values()))

    kept = []
    for tok in candidates:
        if tok not in bad1:
            kept.append(tok)
        elif capitalised[tok] not in bad2:
            if len(tok) <= 2 and tok not in SHORT_CAPITALISED_OK.get(lang, ()):
                continue
            kept.append(capitalised[tok])
        if len(kept) >= MAX_WORDS:
            break

    rows = {}
    for rank, word in enumerate(kept, 1):
        freq = max(1, min(255, round(255 * (1 - math.log(rank) / math.log(RANK_HORIZON)))))
        rows[word.lower()] = (word, freq)
    extra = EXTRAS / f"{lang}.extra"
    if extra.exists():
        for line in extra.read_text(encoding="utf-8").splitlines():
            word, _, f = line.partition("\t")
            if word and f.strip().isdigit():
                old = rows.get(word.lower())
                rows[word.lower()] = (word, max(int(f), old[1] if old else 0))
    out = sorted(rows.values(), key=lambda r: (-r[1], r[0]))
    return out, len(candidates), freq_file


def write(lang, rows):
    OUT.mkdir(parents=True, exist_ok=True)
    data = "".join(f"{w}\t{f}\n" for w, f in rows).encode("utf-8")  # bytes: LF endings, no BOM
    (OUT / f"{lang}.dict").write_bytes(data)


def check():
    ok = True
    for lang in LANGS:
        p = OUT / f"{lang}.dict"
        lines = p.read_text(encoding="utf-8").split("\n")
        assert lines[-1] == "", f"{lang}: must end with LF"
        prev, words = 256, set()
        for i, line in enumerate(lines[:-1], 1):
            w, _, f = line.partition("\t")
            if not w or not f.isdigit() or not 1 <= int(f) <= 255:
                print(f"{lang}:{i}: bad row {line!r}"); ok = False; break
            if int(f) > prev:
                print(f"{lang}:{i}: not sorted by frequency"); ok = False; break
            if w.lower() in words:
                print(f"{lang}:{i}: duplicate {w!r}"); ok = False; break
            prev = int(f); words.add(w.lower())
        print(f"{lang}: {len(words)} words {'OK' if ok else 'FAILED'}")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if args.fetch:
        fetch(); return
    if args.check:
        sys.exit(0 if check() else 1)
    report = ["# Dictionary build report", "",
              "Generated by `tools/build_dictionaries.py`. Do not edit by hand.", "",
              f"- rank horizon: {RANK_HORIZON}; max words per language: {MAX_WORDS}",
              "- word validity: Hunspell dictionaries from LibreOffice/dictionaries, checked with the `hunspell` CLI",
              "- frequency: hermitdave/FrequencyWords 2018 (OpenSubtitles 2018), CC-BY-SA-4.0", "",
              "| lang | hunspell source (licence chosen) | tokens examined | words shipped | dict bytes | freq 64 at rank | source sha256 (dic / freq) |",
              "|---|---|---|---|---|---|---|"]
    for lang, (hs, stem, enc, lic) in LANGS.items():
        rows, examined, freq_file = build(lang)
        write(lang, rows)
        r64 = next((i for i, (_, f) in enumerate(rows, 1) if f < 64), len(rows))
        size = (OUT / f"{lang}.dict").stat().st_size
        print(f"{lang}: {len(rows)} words from {examined} candidate tokens, {size} bytes, freq>=64 for ~{r64} words")
        report.append(f"| {lang} | `{hs}` ({lic}) | {examined} | {len(rows)} | {size} | {r64} | "
                      f"{sha(SRC / (stem + '.dic'))} / {sha(freq_file)} |")
    REPORT.write_text("\n".join(report) + "\n", encoding="utf-8")
    print("wrote", REPORT)
    sys.exit(0 if check() else 1)


if __name__ == "__main__":
    main()
