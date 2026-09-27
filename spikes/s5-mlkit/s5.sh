#!/usr/bin/env bash
# S5 test harness. Usage:
#   ./s5.sh test        JVM unit tests
#   ./s5.sh build       release APK
#   ./s5.sh install     adb install -r (tap "Install" on the phone; keeps the trial log)
#   ./s5.sh models      download the MediaPipe embedder models into model/ (git-ignored)
#   ./s5.sh pull        copy trials.jsonl to results/ (git-ignored: has embeddings of the home) and write
#                       results/trials.jsonl.gz WITHOUT embeddings (labels, luma, timing): the committed copy
#   ./s5.sh summary [session] [--config tools/frozen-A.json]    part A: pull, then score labels
#   ./s5.sh score-b <session> [--config tools/frozen-b.json]    part B: pull, then score embeddings
export MSYS_NO_PATHCONV=1
set -o pipefail
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=dev.rinalarm.spike.s5
DIR="$(cd "$(dirname "$0")" && pwd)"
DEV=/sdcard/Android/data/$PKG/files/s5
gradle() { (cd "$DIR" && unset MSYS_NO_PATHCONV && JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}" ./gradlew "$@" -q 2>&1 \
  | { grep -vE "restricted method|native access|NativeLibraryLoader|SDK XML versions|sun.misc.Unsafe|terminally deprecated|^\s*$" || true; }); }

case "$1" in
  test) gradle testDebugUnitTest && echo "unit tests OK" ;;
  build) gradle assembleRelease && ls -la "$DIR/app/build/outputs/apk/release/app-release.apk" | awk '{print "APK bytes:", $5}' ;;
  install) "$ADB" install -r "$(cygpath -w "$DIR/app/build/outputs/apk/release/app-release.apk")" ;;
  pull)
    mkdir -p "$DIR/results"
    "$ADB" pull $DEV/trials.jsonl "$(cygpath -w "$DIR/results/trials.jsonl")" >/dev/null || exit 1
    python "$(cygpath -w "$DIR/tools/public_log.py")" "$(cygpath -w "$DIR/results/trials.jsonl")" || exit 1
    echo "records: $(wc -l < "$DIR/results/trials.jsonl"), public gz bytes: $(wc -c < "$DIR/results/trials.jsonl.gz")" ;;
  models)
    mkdir -p "$DIR/model"
    for m in small large; do curl -sfL -o "$DIR/model/mobilenet_v3_$m.tflite" \
      https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_$m/float32/latest/mobilenet_v3_$m.tflite; done
    sha256sum "$DIR"/model/*.tflite ;;
  summary) shift; bash "$0" pull && python "$(cygpath -w "$DIR/tools/summarize.py")" "$(cygpath -w "$DIR/results/trials.jsonl")" "$@" ;;
  score-b) shift; bash "$0" pull && python "$(cygpath -w "$DIR/tools/score_b.py")" "$(cygpath -w "$DIR/results/trials.jsonl")" "$@" ;;
  *) sed -n '2,11p' "$0" ;;
esac
