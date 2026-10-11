#!/usr/bin/env python3
"""
Release & Versioning Manager for MetroBoard.

Manages semantic versioning, updates build files, verifies CHANGELOG.md,
and enforces that every release explicitly acknowledges project inspirations,
gratitude to contributors/open source projects, and open source declaration.

Usage:
    python3 tools/release_manager.py --verify                 # Validates current release integrity
    python3 tools/release_manager.py --set-version 0.2.0      # Sets version and updates build.gradle.kts
    python3 tools/release_manager.py --generate-release-notes # Prints release notes with required credits
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
CHANGELOG = REPO_ROOT / "CHANGELOG.md"
ATTRIBUTION = REPO_ROOT / "ATTRIBUTION.md"

REQUIRED_ACKNOWLEDGMENT_SUBSTRINGS = [
    "Open Source",
    "AGPL-3.0",
    "Word Flow",
    "ChiBoard",
    "sbosio",
    "xavier-hernandez",
    "AOSP",
]


def log(msg: str) -> None:
    print(f"[release] {msg}", flush=True)


def get_current_gradle_version() -> tuple[int, str]:
    if not BUILD_GRADLE.exists():
        raise SystemExit(f"No se encontró {BUILD_GRADLE}")

    content = BUILD_GRADLE.read_text(encoding="utf-8")
    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)

    if not code_match or not name_match:
        raise SystemExit("No se pudieron parsear versionCode o versionName en build.gradle.kts")

    return int(code_match.group(1)), name_match.group(1)


def set_gradle_version(new_code: int, new_name: str) -> None:
    content = BUILD_GRADLE.read_text(encoding="utf-8")
    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', content)
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_name}"', content)
    BUILD_GRADLE.write_text(content, encoding="utf-8")
    log(f"Actualizado app/build.gradle.kts a versionCode={new_code}, versionName={new_name}")


def verify_attribution_in_text(text: str, source_name: str) -> list[str]:
    errors = []
    for item in REQUIRED_ACKNOWLEDGMENT_SUBSTRINGS:
        if item.lower() not in text.lower():
            errors.append(f"{source_name} no contiene la mención/agradecimiento obligatorio para: '{item}'")
    return errors


def verify_release_integrity() -> int:
    code, name = get_current_gradle_version()
    log(f"Versión actual en Gradle: {name} (código {code})")

    if not CHANGELOG.exists():
        print("ERROR: Falta CHANGELOG.md", file=sys.stderr)
        return 1

    changelog_text = CHANGELOG.read_text(encoding="utf-8")
    version_header = f"[{name}]"
    if version_header not in changelog_text:
        print(f"ADVERTENCIA: La versión {name} no tiene una sección dedicada '[{name}]' en CHANGELOG.md", file=sys.stderr)

    # Validar presencia de agradecimientos y menciones de inspiración en CHANGELOG
    changelog_errors = verify_attribution_in_text(changelog_text, "CHANGELOG.md")
    if changelog_errors:
        for err in changelog_errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    # Validar ATTRIBUTION.md
    if not ATTRIBUTION.exists():
        print("ERROR: Falta ATTRIBUTION.md", file=sys.stderr)
        return 1

    attribution_text = ATTRIBUTION.read_text(encoding="utf-8")
    attribution_errors = verify_attribution_in_text(attribution_text, "ATTRIBUTION.md")
    if attribution_errors:
        for err in attribution_errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    log("Integridad de versión y agradecimientos verificada correctamente.")
    return 0


def generate_release_notes(version: str) -> str:
    notes = f"""# MetroBoard Release v{version}

MetroBoard es un teclado Android independiente, 100% de código abierto (Open Source),
licenciado bajo GNU Affero General Public License v3 (AGPL-3.0).

## 💡 Inspiración y Agradecimientos
Este proyecto se inspira en el diseño y la ergonomía de múltiples proyectos y comunidades:
- **Microsoft Windows Phone 8.1 Word Flow Keyboard**: Ergonomía y estética visual Metro UI.
- **ChiBoard (ChiApps)**: Pionero teclado estilo Windows para Android y base de frecuencias relativas en español.
- **Santiago Bosio (sbosio/rla-es) y Xavier Hernández**: Diccionario y wordlist libre en español (AGPL-3.0).
- **SCOWL / Kevin Atkinson & Peter Norvig**: Frecuencias y vocabulario en inglés.
- **Lexique 3 & Wiktionary**: Frecuencias en francés.
- **Leipzig Wortschatz & Wiktionary**: Frecuencias en alemán.
- **Android Open Source Project (AOSP)**: Base del subsistema IME.

Agradecemos profundamente a cada uno de estos proyectos por impulsar el software libre.
Todo el código de MetroBoard ha sido implementado en Kotlin de forma limpia y transparente.
"""
    return notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Verifica la integridad de versiones y atribuciones")
    parser.add_argument("--set-version", type=str, help="Establece un nuevo versionName (ej. 0.2.0)")
    parser.add_argument("--set-code", type=int, help="Establece un nuevo versionCode opcional")
    parser.add_argument("--generate-release-notes", action="store_true", help="Imprime las notas de versión con agradecimientos obligatorios")
    args = parser.parse_args()

    if args.generate_release_notes:
        _, name = get_current_gradle_version()
        print(generate_release_notes(name))
        return 0

    if args.set_version:
        code, _ = get_current_gradle_version()
        new_code = args.set_code if args.set_code is not None else code + 1
        set_gradle_version(new_code, args.set_version)
        return verify_release_integrity()

    # Default to verify
    return verify_release_integrity()


if __name__ == "__main__":
    raise SystemExit(main())
