#!/usr/bin/env bash
# S5 test harness. Usage:
#   ./s5.sh test        JVM unit tests
#   ./s5.sh build       release APK
#   ./s5.sh install     adb install -r (tap "Install" on the phone; keeps the trial log)
#   ./s5.sh pull        copy the phone's trials.jsonl to results/ (labels + timing only, no images)
#   ./s5.sh summary [session] [--config tools/frozen.json]   pull, then score
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
    "$ADB" pull $DEV/trials.jsonl "$(cygpath -w "$DIR/results/trials.jsonl")" >/dev/null && wc -l < "$DIR/results/trials.jsonl" | xargs echo "records:" ;;
  summary) shift; bash "$0" pull && python "$(cygpath -w "$DIR/tools/summarize.py")" "$(cygpath -w "$DIR/results/trials.jsonl")" "$@" ;;
  *) sed -n '2,7p' "$0" ;;
esac
