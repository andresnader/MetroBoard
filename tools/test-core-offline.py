#!/usr/bin/env python3
# MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
# Copyright (C) 2026 MetroBoard Contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Compile and run the real JUnit core tests with Android Studio's bundled tools.

Fallback for this machine without Gradle/SDK. Does NOT build or validate an APK.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--studio", default="/Applications/Android Studio.app/Contents")
parser.add_argument("--android-jar", default=None,
                    help="android.jar del SDK. Por defecto se busca en $ANDROID_HOME o sdk.dir de local.properties")
args = parser.parse_args()
studio = Path(args.studio)
root = Path(__file__).resolve().parents[1]
out = root / "build" / "offline-core-tests"
out.mkdir(parents=True, exist_ok=True)
kotlin = studio / "plugins/Kotlin/kotlinc"
java_home = studio / "jbr/Contents/Home"
junit = studio / "lib/junit4.jar"
hamcrest = []
for jar in (studio / "lib").rglob("*.jar"):
    with zipfile.ZipFile(jar) as archive:
        if "org/hamcrest/SelfDescribing.class" in archive.namelist():
            hamcrest.append(jar)
            break
if not hamcrest:
    raise SystemExit("Hamcrest is missing. Use ./gradlew :app:testDebugUnitTest with network access.")


def find_android_jar():
    """Localiza android.jar para poder compilar el código que depende de Android.

    Sin él, los módulos `ime`, `settings` y `ui` no compilan y sus pruebas se
    quedan fuera; el runner tiene que incluirlos o el resultado miente sobre
    la cobertura real.
    """
    candidates = []
    if args.android_jar:
        candidates.append(Path(args.android_jar))
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(env)
        if value:
            candidates.append(Path(value) / "platforms")
    local = root / "local.properties"
    if local.exists():
        for line in local.read_text().splitlines():
            if line.strip().startswith("sdk.dir="):
                candidates.append(Path(line.split("=", 1)[1].strip()) / "platforms")
    for platforms in candidates:
        if not platforms.is_dir():
            continue
        versions = sorted((p for p in platforms.glob("android-*") if p.is_dir()),
                          key=lambda p: p.name)
        # La API más alta disponible; el código apunta a compileSdk 36.
        for version in reversed(versions):
            jar = version / "android.jar"
            if jar.exists():
                return str(jar)
    return None


android_jar = find_android_jar()
if not android_jar:
    print("AVISO: no se encontro android.jar; se omiten los modulos que dependen de Android.\n"
          "       Usa ./gradlew :app:testDebugUnitTest para la cobertura completa.", flush=True)

source_root = root / "app/src/main/java/com/metroboard/ime"
tests = sorted((root / "app/src/test/java").rglob("*Test.kt"))

# Módulos con recursos. Sus clases referencian `R` transitivamente, así que
# cualquier archivo que importe de ellos tampoco compila aquí.
RESOURCE_MODULES = {"settings", "ui"}

# Se compila todo `main` salvo los módulos con recursos: el filtro por archivo
# de más abajo decide qué entra, así que incluir `ime` aquí es necesario para
# que sus clases puras lleguen al runner.
modules = {path.relative_to(root / "app/src/test/java").parts[1] for path in tests}
wanted = {path.parent.name for path in source_root.glob("*/*.kt")} | modules
wanted -= RESOURCE_MODULES

# Un archivo fuente que menciona `R.<tipo>` no compila fuera de Gradle: la
# clase `R` la genera AAPT durante el build y no existe en el árbol de fuentes.
# `BuildConfig` tiene el mismo origen. Se filtran archivo a archivo en vez de
# descartar el módulo entero, porque muchos archivos son lógica pura.
R_REFERENCE = re.compile(r"\bR\.(?:string|layout|id|drawable|array|xml|style|plurals|color|dimen|bool|integer)\b")


def package_of(path: Path) -> str:
    match = re.search(r"^package\s+([\w.]+)", path.read_text(encoding="utf-8", errors="replace"), re.M)
    return match.group(1).rsplit(".", 1)[-1] if match else ""


def imports_from_resource_module(path: Path) -> bool:
    text = path.read_text(encoding="utf-8", errors="replace")
    for module in RESOURCE_MODULES:
        if re.search(rf"^import\s+com\.metroboard\.ime\.{module}\b", text, re.M):
            return True
    return False


def source_is_compilable(path: Path) -> bool:
    text = path.read_text(encoding="utf-8", errors="replace")
    if R_REFERENCE.search(text) or re.search(r"\bBuildConfig\b", text):
        return False
    # Dependencia transitiva: si importa de un módulo con recursos, ese módulo
    # aporta `R` al paquete y la compilación falla igual.
    return not imports_from_resource_module(path)


sources: list = []
all_sources: list = []
for module in sorted(wanted):
    all_sources.extend(sorted((source_root / module).glob("*.kt")))

if android_jar is None:
    raise SystemExit(
        "No se encontro android.jar, necesario para compilar los módulos de lógica.\n"
        "Define sdk.dir en local.properties o ANDROID_HOME, o usa "
        "./gradlew :app:testDebugUnitTest."
    )


def referenced_types(path: Path) -> set:
    """Tipos del paquete propio que el archivo referencia (imports o firmas)."""
    text = path.read_text(encoding="utf-8", errors="replace")
    found = set()
    for name in re.findall(r"^import\s+com\.metroboard\.ime\.[\w.]*\.(\w+)", text, re.M):
        found.add(name)
    for name in re.findall(r"\b([A-Z]\w+)\.[a-z]\w*\s*[=(]", text):
        found.add(name)
    return found


# Punto de partida: lo que no puede compilar por usar recursos o por importar
# de un módulo con recursos. Se propaga por dependencia transitiva porque
# `InputConnectionEditor` no usa R, pero llama a `EditorPolicy`, que sí lo hace.
excluded = {p for p in all_sources if not source_is_compilable(p)}
while True:
    excluded_stems = {p.stem for p in excluded}
    grown = {
        p for p in all_sources
        if p not in excluded and referenced_types(p) & excluded_stems
    }
    if not grown:
        break
    excluded |= grown

sources = [p for p in all_sources if p not in excluded]

# Una prueba se omite si su paquete corresponde a un módulo con recursos, si
# su propia fuente referencia R/BuildConfig, o si la clase que prueba no llegó
# a compilar. `ime/EditorPolicyTest` es lógica pura pero `EditorPolicy` importa
# de `settings` (que usa R), así que sin este enlace la prueba se cuela y rompe
# la compilación de todo el lote.
excluded_classes = {path.stem for path in excluded}


def test_is_skippable(path: Path) -> bool:
    if path.relative_to(root / "app/src/test/java").parts[1] in RESOURCE_MODULES:
        return True
    if not source_is_compilable(path):
        return True
    # Si el nombre de la clase bajo prueba aparece entre las fuentes excluidas.
    subject = path.stem
    if subject.endswith("Test"):
        subject = subject[: -len("Test")]
    return subject in excluded_classes


# Pruebas que necesitan una implementación real de org.json: en `android.jar`
# las clases org.json son stubs que lanzan RuntimeException("Stub!"), así que
# aquí no hay forma de ejecutarlas. Gradle sí las cubre con la impl real.
NEEDS_RUNTIME_JSON = {"DictionaryAndPersonalDictTest"}

skipped = [p for p in tests if test_is_skippable(p) or p.stem in NEEDS_RUNTIME_JSON]
tests = [p for p in tests if p not in skipped]

classpath = os.pathsep.join(str(path) for path in [junit, *hamcrest, android_jar])
env = dict(os.environ, JAVA_HOME=str(java_home))
print(f"Compilando {len(sources)} fuentes y {len(tests)} pruebas (android.jar incluido)", flush=True)
if skipped:
    print(f"Omitidas {len(skipped)} pruebas que el runner offline no puede ejecutar: "
          f"{', '.join(sorted(p.name for p in skipped))}", flush=True)
    print("  -> usa ./gradlew :app:testDebugUnitTest para la cobertura completa", flush=True)
commands = [
    [str(kotlin / "bin/kotlinc"), "-jvm-target", "17", "-classpath", classpath,
     "-nowarn", "-d", str(out / "tests.jar"), *map(str, sources + tests)],
    [str(java_home / "bin/java"), "-cp", os.pathsep.join([
        str(out / "tests.jar"), str(kotlin / "lib/kotlin-stdlib.jar"), classpath]),
     "org.junit.runner.JUnitCore", *[
         ".".join(path.relative_to(root / "app/src/test/java").with_suffix("").parts)
         for path in tests]],
]
with (out / "result.txt").open("w") as log:
    for command in commands:
        result = subprocess.run(command, env=env, cwd=root, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT)
        print(result.stdout, end="")
        log.write(result.stdout)
        log.flush()
        if result.returncode:
            raise SystemExit(result.returncode)
