#!/usr/bin/env bash
# Phase 2's exit numbers on the connected phone (task 2.5), with the debug build installed and the screen unlocked:
#   1. load and memory: cold starts through measure-device.sh
#   2. frame pacing at the 30 fps cap, then with the cap lifted to 120 (the headroom), each over a window in which
#      she gestures every 4 s and speaks the debug voice lines, the heaviest the home strip gets
#   3. 20 mood changes, each timed from the app's send to the blend's last frame (bar: 300 ms)
#   4. a renderer crash: the still must take over and the app must survive (same pid); a screenshot is saved
#
# usage: tools/character/phase-exit.sh [cold-runs=3] [out-dir=build/phase-exit]
# Bars (plan phase-2.md): load <= 2.5 s; capped avg >= 29 fps with 0 frames > 50 ms; uncapped avg >= 45 fps;
# 20/20 moods <= 300 ms; crash -> still, same pid.
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=io.github.earthkodyai.rinalarm
RECV="$PKG/.debug.DebugCharacterReceiver"
RUNS="${1:-3}"
OUT="${2:-build/phase-exit}"
WINDOW=30
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

say() { echo "== $*"; }
cmd() { "$ADB" shell am broadcast -n "$RECV" --es cmd "$@" > /dev/null; }
log() { "$ADB" logcat -d -s "$@" | tr -d '\r'; }
wait_for() { # tag pattern seconds
  for _ in $(seq 1 $(($3 * 2))); do
    log "$1" | grep -q "$2" && return 0
    sleep 0.5
  done
  return 1
}

say "load + memory ($RUNS cold starts)"
"$HERE/measure-device.sh" "$RUNS" cold

# The last cold start left the app open with Rin on screen.
frames() { # label
  "$ADB" logcat -c
  cmd stats --ei sec "$WINDOW"
  local gestures=(wave nod joy stretch yawn clap shake pout huff) i=0
  for t in $(seq 0 4 $((WINDOW - 1))); do
    cmd gesture --es name "${gestures[$((i++ % ${#gestures[@]}))]}"
    [ $((t % 8)) -eq 0 ] && cmd say --es clip "$(printf 'L%02d' $((t / 8 + 1)))"
    sleep 4
  done
  if wait_for RinChar:I ' stats ' 15; then
    echo "$1: $(log RinChar:I | grep -m1 ' stats ' | sed 's/.*stats //')"
  else
    echo "$1: no stats (screen off or app not in front?)"
  fi
}
say "frame pacing, ${WINDOW} s each, gestures every 4 s + voice"
frames "cap 30"
cmd fps --ei cap 120
frames "cap 120"
cmd fps --ei cap 30

say "20 mood changes"
"$ADB" logcat -c
moods=(pouty cheerful worried proud sulky relieved sleepy)
for i in $(seq 0 19); do
  cmd mood --es mood "${moods[$((i % 7))]}"
  sleep 1.2
done
sleep 1
totals=$(log RinChar:I | grep ' emotion ' | sed 's/.*total=//')
n=$(echo "$totals" | grep -c . || true)
ok=$(echo "$totals" | awk '$1 <= 300' | grep -c . || true)
echo "moods: $ok/$n <= 300 ms; ms: $(echo "$totals" | sort -n | tr '\n' ' ')"

say "renderer crash"
pid=$("$ADB" shell pidof "$PKG" | tr -d '\r')
"$ADB" logcat -c
cmd crash
# Crashpad writes its dump first, so the app hears of the crash 1.5 s or more later.
wait_for RinChar:W "renderer gone" 10 || true
sleep 1 # the still fades in
after=$("$ADB" shell pidof "$PKG" | tr -d '\r' || true)
gone=$(log RinChar:W | grep -c 'renderer gone' || true)
echo "crash: renderer-gone logs=$gone, pid $pid -> ${after:-dead} ($([ "$pid" = "$after" ] && echo survived || echo DIED))"
"$ADB" exec-out screencap -p > "$OUT/after-crash.png"
echo "screenshot: $OUT/after-crash.png"
# A fresh page for whoever uses the phone next (and it re-registers alarms, as every app open does).
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.MainActivity" > /dev/null
