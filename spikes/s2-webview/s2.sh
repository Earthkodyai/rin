#!/usr/bin/env bash
# S2 test harness. Usage:
#   ./s2.sh build                 web (vite) + release APK
#   ./s2.sh install               adb install -r (tap "Install" on the phone)
#   ./s2.sh run <label> [query]   cold start, wait for results, capture memory; appends to results/runs.log
#                                 query is passed to the page, e.g. "pr=2&dur=30" (see web/src/main.ts)
#   ./s2.sh matrix [n]            the standard run set, n cold starts each (default 3)
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=dev.rinalarm.spike.s2
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/results/runs.log"

renderers() { "$ADB" shell ps -A -o PID,NAME | grep -E "sandboxed_process|privileged_process" | awk '{print $1}' | sort; }

case "$1" in
  build)
    (cd "$DIR/web" && npm run build --silent) || exit 1
    [ -f "$DIR/model/sample.vrm" ] || echo "WARNING: model/sample.vrm missing (export it from VRoid Studio)"
    (cd "$DIR" && unset MSYS_NO_PATHCONV && JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}" ./gradlew assembleRelease -q 2>&1 \
      | grep -vE "restricted method|native access|NativeLibraryLoader|SDK XML versions|^\s*$") ;;
  install)
    "$ADB" install -r "$(cygpath -w "$DIR/app/build/outputs/apk/release/app-release.apk")" ;;
  run)
    label="$2"; query="${3:-}"
    dur=$(echo "$query" | grep -oE 'dur=[0-9]+' | cut -d= -f2); dur=${dur:-30}
    "$ADB" shell am force-stop $PKG; sleep 2
    before=$(renderers)
    "$ADB" logcat -c
    temp0=$("$ADB" shell dumpsys battery | grep -m1 temperature | awk '{print $2}')
    start=$("$ADB" shell am start -W -n $PKG/.MainActivity --es q "'run=$label&$query'" | grep -E "TotalTime" | awk '{print $2}')
    deadline=$(( $(date +%s) + dur + 40 ))
    until "$ADB" logcat -d -s S2:I | grep -qE '"phase":"fps"|"error"'; do
      [ "$(date +%s)" -gt "$deadline" ] && { echo "TIMEOUT"; break; }
      sleep 3
    done
    new=$(comm -13 <(echo "$before") <(renderers) | tr '\n' ' ')
    app=$("$ADB" shell dumpsys meminfo $PKG | grep -E "TOTAL PSS:|^ +Graphics:|^ +Native Heap:|GL mtrack|EGL mtrack" | tr -s ' ' | tr '\n' '|')
    ren=""; for p in $new; do ren="$ren $p:$("$ADB" shell dumpsys meminfo $p | grep -m1 "TOTAL PSS:" | awk '{print $3}')"; done
    temp1=$("$ADB" shell dumpsys battery | grep -m1 temperature | awk '{print $2}')
    thermal=$("$ADB" shell dumpsys thermalservice | grep -m1 "Thermal Status" | awk '{print $NF}')
    {
      echo "=== $(date '+%F %T') $label q=[$query] amStartTotalTime=${start}ms battTemp=${temp0}->${temp1} thermal=$thermal"
      "$ADB" logcat -d -s S2:I | grep -oE '\{.*\}$'
      echo "meminfo app: $app"
      echo "meminfo renderer PSS kB:$ren"
    } | tee -a "$OUT" | cut -c1-400 ;;
  matrix)
    n="${2:-3}"
    for i in $(seq 1 "$n"); do
      bash "$0" run "default-$i" "pr=2&dur=30"     # planned production setting
      bash "$0" run "native-$i"  "pr=0&dur=30"     # full device pixel ratio (worst case)
      bash "$0" run "pr15-$i"    "pr=1.5&dur=30"
    done
    bash "$0" run "long" "pr=2&dur=180"            # 3 min: thermal throttling check
    ;;
  *) sed -n '2,8p' "$0" ;;
esac
