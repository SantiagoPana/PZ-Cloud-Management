#!/usr/bin/env bash
set -euo pipefail
TASK_REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
rm -rf "$TASK_REPO/build/classes" "$TASK_REPO/build/resources" "$TASK_REPO/build/runtime-classes"
mkdir -p "$TASK_REPO/build/classes" "$TASK_REPO/build/resources" "$TASK_REPO/build/runtime-classes/pzcloud"
javac --release 17 -encoding UTF-8 -d "$TASK_REPO/build/classes" "$TASK_REPO"/src/main/java/pzcloud/*.java
TASK_PUBLISH=(java -cp "$TASK_REPO/build/classes" pzcloud.PublisherConfig "$TASK_REPO/build/resources" "$TASK_REPO/build/google-auth-status.json")
TASK_CLIENT_FILE="${PZCLOUD_GOOGLE_CLIENT_FILE:-$TASK_REPO/release/google-client.json}"
if [[ -f "$TASK_CLIENT_FILE" ]]; then TASK_PUBLISH+=("$TASK_CLIENT_FILE"); fi
if [[ "${PZCLOUD_REQUIRE_GOOGLE_LOGIN:-0}" == 1 ]]; then TASK_PUBLISH+=(--required); fi
"${TASK_PUBLISH[@]}"
for TASK_CLASS in "$TASK_REPO"/build/classes/pzcloud/*.class; do
    case "${TASK_CLASS##*/}" in PublisherConfig*) continue ;; esac
    cp "$TASK_CLASS" "$TASK_REPO/build/runtime-classes/pzcloud/"
done
TASK_JAR=(jar --create --file "$TASK_REPO/build/PZCloudManagement.jar" --main-class pzcloud.PzCloudApp -C "$TASK_REPO/build/runtime-classes" . -C "$TASK_REPO" mod)
if [[ -f "$TASK_REPO/build/resources/auth/google-client.json" ]]; then TASK_JAR+=(-C "$TASK_REPO/build/resources" .); fi
"${TASK_JAR[@]}"
echo "JAR listo: $TASK_REPO/build/PZCloudManagement.jar"
