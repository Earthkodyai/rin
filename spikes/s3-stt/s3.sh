#!/usr/bin/env bash
# S3 test harness. Usage:
#   ./s3.sh test                  JVM unit tests (intent matcher)
#   ./s3.sh build                 release APK (Vosk model bundled from model/)
#   ./s3.sh install               adb install -r (tap "Install" on the phone; keeps recordings)
#   ./s3.sh smoke                 make TTS test WAVs on Windows (SAPI) and push them as set "smoke"
#   ./s3.sh replay <set> [engines]  run replay headless (engines: all or csv), wait, pull results
#   ./s3.sh pull                  copy recordings (set "me") + results to the PC (git-ignored recordings/)
#   ./s3.sh push-me               push recordings back after a reinstall
export MSYS_NO_PATHCONV=1
set -o pipefail
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=dev.rinalarm.spike.s3
DIR="$(cd "$(dirname "$0")" && pwd)"
DEV=/sdcard/Android/data/$PKG/files
gradle() { (cd "$DIR" && unset MSYS_NO_PATHCONV && JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}" ./gradlew "$@" -q 2>&1 \
  | { grep -vE "restricted method|native access|NativeLibraryLoader|SDK XML versions|sun.misc.Unsafe|terminally deprecated|^\s*$" || true; }); }

case "$1" in
  test) gradle testDebugUnitTest && echo "unit tests OK" ;;
  build) gradle assembleRelease && ls -la "$DIR/app/build/outputs/apk/release/app-release.apk" | awk '{print "APK bytes:", $5}' ;;
  install) "$ADB" install -r "$(cygpath -w "$DIR/app/build/outputs/apk/release/app-release.apk")" ;;
  smoke)
    out="$DIR/recordings/smoke"; mkdir -p "$out"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$DIR/tools/make_smoke.ps1")" -OutDir "$(cygpath -w "$out")" || exit 1
    "$ADB" shell mkdir -p $DEV/sets/smoke
    for f in "$out"/*; do "$ADB" push "$(cygpath -w "$f")" $DEV/sets/smoke/ >/dev/null; done
    echo "pushed $(ls "$out" | wc -l) files" ;;
  replay)
    set="${2:-me}"; eng="${3:-all}"
    "$ADB" shell am force-stop $PKG; sleep 1; "$ADB" logcat -c
    "$ADB" shell am start -n $PKG/.MainActivity --es mode replay --es set "$set" --es engines "$eng" >/dev/null
    until "$ADB" logcat -d -s S3:I | grep -q '"phase":"done"'; do
      sleep 10; "$ADB" logcat -d -s S3:I | grep -c '"phase":"utt"' | xargs -I{} printf "\r%s utterances done" {}
    done; echo
    "$ADB" logcat -d -s S3:I | grep -E '"phase":"(engine-done|engine-error|support|vosk-model|header)"' | grep -oE '\{.*\}$'
    bash "$0" pull ;;
  pull)
    mkdir -p "$DIR/recordings" "$DIR/results"
    "$ADB" pull $DEV/sets/me "$(cygpath -w "$DIR/recordings")" >/dev/null 2>&1
    "$ADB" pull $DEV/results "$(cygpath -w "$DIR")" >/dev/null && ls "$DIR/results" | tail -3 ;;
  push-me)
    "$ADB" shell mkdir -p $DEV/sets
    "$ADB" push "$(cygpath -w "$DIR/recordings/me")" $DEV/sets/ ;;
  *) sed -n '2,10p' "$0" ;;
esac
