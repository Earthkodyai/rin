#!/usr/bin/env bash
# S1 test harness. Usage:
#   ./s1.sh sched <label> <in_sec> [ring_sec]   schedule an alarm through the debug hook
#   ./s1.sh watch <sec>                         poll audio players for USAGE_ALARM for <sec> seconds
#   ./s1.sh log [n]                             last n ring-log lines
#   ./s1.sh state                               screen / doze / saver / dnd / ringer
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=dev.rinalarm.spike.s1
LOG=/data/user_de/0/$PKG/files/ringlog.csv

case "$1" in
  sched)
    "$ADB" shell am start --activity-single-top -n $PKG/com.example.s1alarm.MainActivity \
      --ei in_sec "$3" --es label "$2" --ei ring_sec "${4:-30}" >/dev/null
    sleep 1; "$ADB" shell run-as $PKG cat $LOG | tail -1 ;;
  watch)
    end=$(( $(date +%s) + $2 ))
    while [ "$(date +%s)" -lt "$end" ]; do
      line=$("$ADB" shell dumpsys audio | grep -m1 "state:started attr:AudioAttributes: usage=USAGE_ALARM")
      if [ -n "$line" ]; then
        echo "AUDIO $(date +%T): $(echo "$line" | grep -oE 'u/pid:[0-9/]+|mutedState:[a-z_ ]+' | tr '\n' ' ')"; exit 0
      fi
      sleep 2
    done
    echo "AUDIO: no started USAGE_ALARM player within $2 s"; exit 1 ;;
  cancel)
    "$ADB" shell am start --activity-single-top -n $PKG/com.example.s1alarm.MainActivity --ez cancel_all true >/dev/null ;;
  doze)  # $2=label $3=in_sec: schedule, force deep idle, trace idle state until it fires
    bash "$0" sched "$2" "$3" 15 | cut -d, -f1-4
    until "$ADB" shell dumpsys deviceidle | grep -q "mScreenOn=false"; do sleep 3; done
    "$ADB" shell dumpsys battery unplug
    for i in $(seq 1 12); do [ "$("$ADB" shell dumpsys deviceidle step deep)" = "Stepped to deep: IDLE" ] && break; done
    "$ADB" shell dumpsys deviceidle force-idle deep >/dev/null
    until bash "$0" log 4 | grep -q ",fired,[0-9]*,$2,"; do echo "$(date +%T) deep=$("$ADB" shell dumpsys deviceidle get deep)"; sleep 20; done
    sleep 20; "$ADB" shell dumpsys deviceidle unforce >/dev/null; "$ADB" shell dumpsys battery reset
    bash "$0" log 5 | grep -vE "scheduled" | cut -d, -f1-2,4,7,8 ;;
  log)
    "$ADB" shell run-as $PKG cat $LOG | tail -"${2:-6}" ;;
  state)
    "$ADB" shell dumpsys deviceidle get deep
    "$ADB" shell settings get global low_power
    "$ADB" shell settings get global zen_mode
    "$ADB" shell dumpsys power | grep -m1 -E "mWakefulness=" ;;
  *) sed -n 2,8p "$0" ;;
esac
