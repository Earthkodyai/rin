#!/usr/bin/env bash
# Cold-starts the app N times on the connected phone and prints, per run, Rin's load timings (the page's `ready`
# message, logged by CharacterView as "RinChar: ready ...") and memory 8 s after her first frame: the app's total PSS,
# its Graphics share, and the PSS of the WebView renderer process that appeared with this launch.
#
# usage: tools/character/measure-device.sh [runs=3] [label]
# Force-stop clears the app's AlarmManager registrations; the last launch leaves the app open, which reconciles them.
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=io.github.earthkodyai.rinalarm
RUNS="${1:-3}"
LABEL="${2:-run}"

renderers() { "$ADB" shell ps -A -o PID,NAME | awk '/sandboxed_process/ {print $1}' | tr -d '\r' | sort; }
pss_kb() { "$ADB" shell dumpsys meminfo "$1" | tr -d '\r' | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}'; }

for i in $(seq 1 "$RUNS"); do
  "$ADB" shell am force-stop "$PKG"
  sleep 2
  before=$(renderers)
  "$ADB" logcat -c
  "$ADB" shell am start -W -n "$PKG/.MainActivity" > /dev/null
  ready=""
  for _ in $(seq 1 60); do
    ready=$("$ADB" logcat -d -s RinChar:I | tr -d '\r' | grep -m1 ' ready ' || true)
    [ -n "$ready" ] && break
    sleep 0.5
  done
  if [ -z "$ready" ]; then
    echo "$LABEL #$i: no ready within 30 s ($("$ADB" logcat -d -s RinChar:W | tr -d '\r' | tail -1))"
    continue
  fi
  sleep 8
  app=$(pss_kb "$PKG")
  graphics=$("$ADB" shell dumpsys meminfo "$PKG" | tr -d '\r' | awk '/Graphics:/ {print $2; exit}')
  renderer=$(comm -13 <(echo "$before") <(renderers) | head -1)
  rpss=$([ -n "$renderer" ] && pss_kb "$renderer" || echo "?")
  echo "$LABEL #$i: appPSS=$((app / 1024))MB graphics=$((graphics / 1024))MB rendererPSS=$([ "$rpss" = "?" ] && echo "?" || echo "$((rpss / 1024))MB") | ${ready#*ready }"
done
