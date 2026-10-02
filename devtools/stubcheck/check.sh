#!/usr/bin/env bash
# Offline compile + unit-test check for environments that cannot reach the Fabric/Mojang Maven repos.
#
# Generates stub classes for every Minecraft/Fabric member referenced by the released 1.0.0 jar
# (members that are known to exist in 26.2), patches in hierarchy/generics from hints.txt, adds the
# members listed in extra.txt (used by new code but NOT verified against 26.2), then compiles
# src/main and runs src/test against those stubs. A real `./gradlew build` is still the source of truth.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${STUBCHECK_DIR:-$ROOT/build/stubcheck}"
LIB="$WORK/lib"
mkdir -p "$LIB"
fetch() { [ -f "$LIB/$(basename "$1")" ] || curl -sSfL -o "$LIB/$(basename "$1")" "https://repo1.maven.org/maven2/$1"; }
fetch org/ow2/asm/asm/9.8/asm-9.8.jar
fetch com/google/code/gson/gson/2.13.1/gson-2.13.1.jar
fetch org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar
fetch org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar
fetch org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3.jar
fetch org/junit/platform/junit-platform-console-standalone/1.13.4/junit-platform-console-standalone-1.13.4.jar
# Prefer the JDK in JAVA_HOME (CI images also ship an older default javac on PATH).
BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
"${BIN}javac" -version
CP="$LIB/gson-2.13.1.jar:$LIB/slf4j-api-2.0.17.jar:$LIB/lwjgl-3.3.3.jar:$LIB/lwjgl-glfw-3.3.3.jar"
JUNIT="$LIB/junit-platform-console-standalone-1.13.4.jar"

rm -rf "$WORK/gen" "$WORK/stubs" "$WORK/main" "$WORK/test"
"${BIN}javac" -nowarn -d "$WORK/gen" -cp "$LIB/asm-9.8.jar" "$HERE/StubGen.java"
"${BIN}java" -cp "$LIB/asm-9.8.jar:$WORK/gen" StubGen "$ROOT/skyblock-miner-1.0.0.jar" "$WORK/stubs"
rm -rf "$WORK/stubs/com/mojang/brigadier" "$WORK/stubs/org/spongepowered"
cp -r "$HERE/handwritten/." "$WORK/stubs/"
python3 "$HERE/patch.py" "$WORK/stubs" "$HERE/hints.txt"
python3 "$HERE/patch.py" "$WORK/stubs" "$HERE/extra.txt"

"${BIN}javac" -nowarn -d "$WORK/main" -cp "$CP" $(find "$WORK/stubs" "$ROOT/src/main/java" -name '*.java')
echo "main: compiled"
"${BIN}javac" -nowarn -d "$WORK/test" -cp "$WORK/main:$CP:$JUNIT" $(find "$ROOT/src/test/java" -name '*.java')
cp -r "$ROOT/src/test/resources/." "$WORK/test/" 2>/dev/null || true
"${BIN}java" -jar "$JUNIT" execute -cp "$WORK/test:$WORK/main:$CP" --select-package com.skyblockminer --disable-banner --details=summary
