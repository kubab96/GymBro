#!/bin/sh
# Mierzy płynność przełączania zakładek na podłączonym telefonie.
#
# Nie opiera się na wrażeniu z nagrania, tylko na czasach klatek, które system
# raportuje sam (dumpsys gfxinfo). Powtarza przebieg kilka razy, bo pojedynczy
# pomiar na telefonie potrafi się wahać o kilkadziesiąt procent.
ADB="/c/Users/kubab/AppData/Local/Android/Sdk/platform-tools/adb.exe"
PKG="pl.gymbro"
Y=2160
XS="98 319 540 761 980"
RUNS=${1:-5}

"$ADB" shell am force-stop $PKG
"$ADB" shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8   # czas na rozgrzanie zakładek

printf "%-5s %7s %7s %7s %7s %7s\n" bieg p50 p90 p95 p99 jank
for r in $(seq 1 "$RUNS"); do
  "$ADB" shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
  i=0
  while [ $i -lt 4 ]; do
    for X in $XS; do "$ADB" shell input tap $X $Y >/dev/null 2>&1; done
    i=$((i+1))
  done
  sleep 2
  OUT=$("$ADB" shell dumpsys gfxinfo $PKG 2>&1)
  g() { echo "$OUT" | grep "$1" | grep -oE "[0-9]+ms" | head -1 | tr -d 'ms'; }
  J=$(echo "$OUT" | grep "^Janky frames:" | grep -oE "\([0-9.]+%\)" | tr -d '()')
  printf "%-5s %7s %7s %7s %7s %7s\n" "$r" "$(g '50th percentile')" \
    "$(g '90th percentile')" "$(g '95th percentile')" "$(g '99th percentile')" "$J"
done
