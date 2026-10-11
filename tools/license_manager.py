#!/usr/bin/env python3
"""
License & Compliance Manager for MetroBoard.

Manages, validates, and injects license headers and verifies open source
compliance according to GNU Affero General Public License v3 (AGPL-3.0).

Usage:
    python3 tools/license_manager.py --check   # Checks compliance (CI / pre-commit)
    python3 tools/license_manager.py --fix     # Injects missing SPDX license headers
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

SPDX_TAG = "SPDX-License-Identifier: AGPL-3.0-or-later"
COPYRIGHT_NOTICE = "Copyright (C) 2026 MetroBoard Contributors"

KOTLIN_HEADER = f"""/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * {COPYRIGHT_NOTICE}
 *
 * {SPDX_TAG}
 * See LICENSE and ATTRIBUTION.md for details.
 */
"""

PYTHON_HEADER = f"""# MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
# {COPYRIGHT_NOTICE}
# {SPDX_TAG}
"""

SOURCE_EXTENSIONS = {
    ".kt": KOTLIN_HEADER,
    ".kts": KOTLIN_HEADER,
    ".py": PYTHON_HEADER,
}

IGNORE_DIRS = {
    ".git",
    ".gradle",
    ".gradle-user-home",
    ".idea",
    ".tooling",
    ".vscode",
    "build",
    "vendor",
    "decompiled",
    "apks",
    "node_modules",
}


def log(msg: str) -> None:
    print(f"[license] {msg}", flush=True)


def check_required_docs() -> list[str]:
    errors = []
    license_file = REPO_ROOT / "LICENSE"
    attribution_file = REPO_ROOT / "ATTRIBUTION.md"

    if not license_file.exists():
        errors.append("Falta el archivo LICENSE en la raíz del repositorio.")
    else:
        content = license_file.read_text(encoding="utf-8", errors="replace")
        if "GNU AFFERO GENERAL PUBLIC LICENSE" not in content:
            errors.append("El archivo LICENSE no contiene el texto de GNU AGPL v3.")

    if not attribution_file.exists():
        errors.append("Falta el archivo ATTRIBUTION.md en la raíz del repositorio.")
    else:
        content = attribution_file.read_text(encoding="utf-8", errors="replace")
        required_terms = [
            "Word Flow",
            "ChiBoard",
            "xavier-hernandez",
            "sbosio",
            "Open Source",
            "AGPL-3.0",
        ]
        for term in required_terms:
            if term not in content:
                errors.append(f"ATTRIBUTION.md no menciona el término requerido: {term}")

    return errors


def find_source_files() -> list[Path]:
    files = []
    for ext in SOURCE_EXTENSIONS:
        for path in REPO_ROOT.rglob(f"*{ext}"):
            # Check if any parent is in IGNORE_DIRS
            parts = path.relative_to(REPO_ROOT).parts
            if any(ignored in parts for ignored in IGNORE_DIRS):
                continue
            # Skip gradle wrappers or generated files
            if "gradlew" in path.name or "settings.gradle" in path.name:
                continue
            files.append(path)
    return sorted(files)


def has_license_header(path: Path) -> bool:
    try:
        content = path.read_text(encoding="utf-8")
        return "SPDX-License-Identifier" in content or "MetroBoard" in content[:300]
    except Exception:
        return False


def apply_header(path: Path) -> bool:
    content = path.read_text(encoding="utf-8")
    if has_license_header(path):
        return False

    ext = path.suffix
    header = SOURCE_EXTENSIONS.get(ext)
    if not header:
        return False

    # For Python files, preserve shebang if present
    if ext == ".py" and content.startswith("#!"):
        lines = content.splitlines(keepends=True)
        shebang = lines[0]
        rest = "".join(lines[1:])
        new_content = shebang + header + "\n" + rest
    else:
        new_content = header + "\n" + content

    path.write_text(new_content, encoding="utf-8")
    return True


def run(fix: bool = False) -> int:
    doc_errors = check_required_docs()
    if doc_errors:
        for err in doc_errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    source_files = find_source_files()
    missing = []
    fixed = []

    for file_path in source_files:
        if not has_license_header(file_path):
            if fix:
                if apply_header(file_path):
                    fixed.append(file_path)
            else:
                missing.append(file_path)

    if fixed:
        log(f"Se inyectaron encabezados de licencia en {len(fixed)} archivos:")
        for f in fixed:
            log(f"  + {f.relative_to(REPO_ROOT)}")

    if missing:
        log(f"Faltan encabezados de licencia en {len(missing)} archivos (ejecuta con --fix para reparar):")
        for f in missing:
            print(f"  ! {f.relative_to(REPO_ROOT)}", file=sys.stderr)
        return 1

    log(f"Verificación exitosa: {len(source_files)} archivos fuente cumplen con la licencia AGPL-3.0 y atribución.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fix",
        action="store_true",
        help="Inyecta automáticamente los encabezados SPDX faltantes",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verifica si todos los archivos tienen encabezados de licencia",
    )
    args = parser.parse_args()

    return run(fix=args.fix)


if __name__ == "__main__":
    raise SystemExit(main())
