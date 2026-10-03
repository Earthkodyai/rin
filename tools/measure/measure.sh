#!/usr/bin/env bash
# 6.9 measurements on a connected phone (the 14T): cold start, memory, idle background work.
# Usage: tools/measure/measure.sh <label> [runs]   e.g. tools/measure/measure.sh before 10
# Install the build first (release: ./gradlew assembleRelease, adb install -r ...). The screen must be unlocked.
# Prints one block; paste it into docs/spikes/6.9-optimise.md.
set -euo pipefail
LABEL=${1:?label}
RUNS=${2:-10}
PKG=io.github.earthkodyai.rinalarm
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
a() { "$ADB" "$@" | tr -d '\r'; }

median() { sort -n | awk '{v[NR]=$1} END {if (NR%2) print v[(NR+1)/2]; else print int((v[NR/2]+v[NR/2+1])/2)}'; }

# Our WebView renderers: sandboxed processes running under this app's isolated uid (u0a<appId>i<n>).
renderers() {
  local app
  app=$(a shell dumpsys package $PKG | grep -m1 -o "appId=[0-9]*" | cut -d= -f2)
  a shell dumpsys activity processes | grep -o "[0-9]*:com.google.android.webview:sandboxed_process0[^ ]*/u0a$((app - 10000))i[0-9]*" | cut -d: -f1 | sort -u
}

echo "## $LABEL ($(date '+%Y-%m-%d %H:%M'), $(a shell getprop ro.product.model), versionName $(a shell dumpsys package $PKG | grep -m1 versionName | cut -d= -f2))"

# Cold start: force-stop, then am start -W; TotalTime is launch to first frame of MainActivity.
times=()
for i in $(seq "$RUNS"); do
  a shell am force-stop $PKG
  sleep 1
  t=$(a shell am start -W -n $PKG/.MainActivity | awk -F': ' '/TotalTime/ {print $2}')
  times+=("$t")
  sleep 4
done
echo "- cold start TotalTime ms (n=$RUNS): median $(printf '%s\n' "${times[@]}" | median), all: ${times[*]}"

# Memory on the home screen after Rin's panel settles (WebView included: its renderer is a separate process).
sleep 8
app=$(a shell dumpsys meminfo $PKG | awk '/TOTAL PSS:/ {print $3; exit}')
renderer=0
for pid in $(renderers); do
  owner=$(a shell cat /proc/$pid/cmdline 2>/dev/null | tr '\0' ' ')
  r=$(a shell dumpsys meminfo $pid 2>/dev/null | awk '/TOTAL PSS:/ {print $3; exit}')
  [ -n "$r" ] && renderer=$((renderer + r)) && echo "  - renderer pid $pid ($owner): ${r} KB"
done
echo "- home PSS: app ${app} KB, WebView renderer(s) ${renderer} KB, sum $(( (app + renderer) / 1024 )) MB"

# Idle: home, then Home key; after 30 s nothing of ours should run or hold the CPU.
# (am start, not `input keyevent`: HyperOS refuses injected input unless a security setting is on.)
a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
sleep 30
echo "- after 30 s in the background:"
echo "  - running services: $(a shell dumpsys activity services $PKG | grep -c 'ServiceRecord' || true)"
echo "  - wake locks held: $(a shell dumpsys power | grep -i "$PKG" | grep -ci wake || true)"
echo "  - scheduled jobs: $(a shell dumpsys jobscheduler | grep -c "$PKG/" || true)"
echo "  - alarms registered: $(a shell dumpsys alarm | grep -c "$PKG" || true) lines (exact alarms are expected only for enabled rings)"
echo "  - PSS cached: $(a shell dumpsys meminfo $PKG | awk '/TOTAL PSS:/ {print $3; exit}') KB"
