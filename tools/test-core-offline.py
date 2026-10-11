#!/usr/bin/env python3
"""Compile and run the real JUnit core tests with Android Studio's bundled tools.

Fallback for this machine without Gradle/SDK. Does NOT build or validate an APK.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--studio", default="/Applications/Android Studio.app/Contents")
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
source_root = root / "app/src/main/java/com/windowsphonekeyboard/ime"
sources = [path for module in ("keyboard", "prediction", "dictionary", "gestures")
           for path in (source_root / module).glob("*.kt")]
tests = sorted((root / "app/src/test/java").rglob("*Test.kt"))
classpath = os.pathsep.join(str(path) for path in [junit, *hamcrest])
env = dict(os.environ, JAVA_HOME=str(java_home))
commands = [
    [str(kotlin / "bin/kotlinc"), "-jvm-target", "17", "-classpath", classpath,
     "-d", str(out / "tests.jar"), *map(str, sources + tests)],
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
