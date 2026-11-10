#!/usr/bin/env bash
set -euo pipefail
TASK_REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$TASK_REPO/build/classes"
rm -rf "$TASK_REPO/build/classes"
mkdir -p "$TASK_REPO/build/classes"
javac --release 17 -encoding UTF-8 -d "$TASK_REPO/build/classes" "$TASK_REPO"/src/main/java/pzcloud/*.java
jar --create --file "$TASK_REPO/build/PZCloudManagement.jar" --main-class pzcloud.PzCloudApp -C "$TASK_REPO/build/classes" . -C "$TASK_REPO" mod
echo "JAR listo: $TASK_REPO/build/PZCloudManagement.jar"
