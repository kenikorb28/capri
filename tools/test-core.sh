#!/usr/bin/env bash
# Compile Capri Android's pure-Kotlin engine (core/) with the Kotlin compiler bundled in Gradle, then run its JUnit tests.
# Usage: test-core.sh <path-to-capri-android> <build-dir>
set -euo pipefail
SRC="$1"
OUT="$2"
LIB=/opt/gradle/lib
CP_KOTLINC="$LIB/kotlin-compiler-embeddable-2.0.21.jar:$LIB/kotlin-stdlib-2.0.21.jar:$LIB/kotlin-reflect-2.0.21.jar:$LIB/kotlin-script-runtime-2.0.21.jar:$LIB/kotlin-daemon-embeddable-2.0.21.jar:$LIB/trove4j-1.0.20200330.jar:$LIB/annotations-24.0.1.jar:$LIB/kotlinx-coroutines-core-jvm-1.6.4.jar"
STD="$LIB/kotlin-stdlib-2.0.21.jar"
JUNIT="$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar"
rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"
echo "== compiling core/src/main"
java -cp "$CP_KOTLINC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD" -jvm-target 17 -d "$OUT/main" $(find "$SRC/core/src/main/kotlin" -name '*.kt') 2>&1 | grep -v '^warning' || true
echo "== compiling core/src/test"
java -cp "$CP_KOTLINC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$STD:$OUT/main:$JUNIT" -jvm-target 17 -d "$OUT/test" $(find "$SRC/core/src/test/kotlin" -name '*.kt') 2>&1 | grep -v '^warning' || true
echo "== running tests"
classes=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | tr '\n' ' ')
java -Dcapri.contract="${CONTRACT:-$SRC/../contract}" -cp "$STD:$OUT/main:$OUT/test:$JUNIT" org.junit.runner.JUnitCore $classes
