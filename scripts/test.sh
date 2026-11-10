#!/usr/bin/env bash
set -euo pipefail
TASK_REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$TASK_REPO/scripts/build.sh"
mkdir -p "$TASK_REPO/build/test-classes"
javac --release 17 -encoding UTF-8 -cp "$TASK_REPO/build/classes" -d "$TASK_REPO/build/test-classes" "$TASK_REPO"/src/test/java/pzcloud/*.java
java -Djava.awt.headless=true -cp "$TASK_REPO/build/classes:$TASK_REPO/build/test-classes" pzcloud.SelfTest
