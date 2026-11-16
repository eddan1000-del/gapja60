#!/bin/bash
# 에뮬레이터에서 앱을 실제로 조작하며 화면을 찍는다.
set -x
mkdir -p shots
PKG=kr.gapja60.app
shot() { adb exec-out screencap -p > "shots/$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/ui.xml >/dev/null 2>&1; }
# 텍스트 또는 content-desc가 정확히/앞부분이 일치하는 노드를 누른다
tap() {
  dump
  XY=$(python3 - "$1" <<'PY'
import sys,re,xml.etree.ElementTree as ET
key=sys.argv[1]
try: root=ET.parse('shots/ui.xml').getroot()
except Exception: sys.exit()
for n in root.iter('node'):
    t=n.get('text',''); d=n.get('content-desc','')
    if t==key or d==key or d.startswith(key+',') or (len(key)>1 and t.startswith(key)):
        a=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  if [ -n "$XY" ]; then adb shell input tap $XY; echo "tap $1 at $XY"; else echo "NOT FOUND: $1" | tee -a shots/report.txt; fi
}
has() { dump; grep -q "$1" shots/ui.xml; }

adb install -r gapja60.apk
adb logcat -c
adb shell am start -n $PKG/kr.gapja60.MainActivity
sleep 6; shot 1-setup
for i in $(seq 1 50); do has '순행 뒤집기' && break; sleep 5; done
has '순행 뒤집기' && echo "HOME OK" >> shots/report.txt || echo "HOME NOT REACHED" >> shots/report.txt
grep -o 'text="[^"]*"' shots/ui.xml | head -20 >> shots/report.txt
shot 2-home

tap '재생'; sleep 4; shot 3-playing
dump; grep -o 'text="[^"]*/ 60[^"]*"' shots/ui.xml >> shots/report.txt
adb shell dumpsys media_session | grep -m3 -E "state=PlaybackState|active=" >> shots/report.txt

for i in $(seq 1 18); do tap '빠르게' >/dev/null; done
sleep 4; shot 4-speed5
dump; grep -o 'text="[^"]*×"' shots/ui.xml >> shots/report.txt

tap '역행 뒤집기'; sleep 2; tap '재생'; sleep 3; shot 5-backward-flip
dump; echo "BACKWARD_FLIP:" >> shots/report.txt; grep -o 'text="[^"]*/ 60[^"]*"' shots/ui.xml >> shots/report.txt
tap '순행 뒤집기'; sleep 2; tap '재생'; sleep 3; shot 5b-forward-flip
dump; echo "FORWARD_FLIP:" >> shots/report.txt; grep -o 'text="[^"]*/ 60[^"]*"' shots/ui.xml >> shots/report.txt
# 백그라운드 재생 확인
adb shell input keyevent KEYCODE_HOME; sleep 5
echo "--- after HOME ---" >> shots/report.txt
adb shell dumpsys media_session | grep -m3 -E "state=PlaybackState" >> shots/report.txt
adb shell am start -n $PKG/kr.gapja60.MainActivity; sleep 3

tap '잇기'; sleep 2; tap '시작'; sleep 5; shot 6-next
tap '빈칸'; sleep 2; shot 7-blank
tap '기록'; sleep 2; shot 8-stats
tap '암송'; sleep 2; tap '☾'; sleep 5; shot 10-sleep
adb shell dumpsys media_session | grep -m1 -E "state=PlaybackState" | sed 's/^/SLEEP /' >> shots/report.txt
tap '수면 모드 끝내기'; sleep 2
tap '설정'; sleep 6; shot 9-settings
adb shell input swipe 500 1800 500 600 300; sleep 1; shot 9b-settings
adb logcat -d > shots/logcat.txt
echo "--- TTS log ---" >> shots/report.txt
grep -E "Gapja60|TextToSpeech|TtsService|GoogleTTS|tts" shots/logcat.txt | grep -v Phenotype | head -60 >> shots/report.txt
grep -E "FATAL EXCEPTION" -A25 shots/logcat.txt > shots/crash.txt || true
[ -s shots/crash.txt ] || echo "no crash" > shots/crash.txt
adb shell pidof $PKG >> shots/report.txt || echo "PROCESS DEAD" >> shots/report.txt
true
