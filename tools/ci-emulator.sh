#!/usr/bin/env bash
# GitHub Actions の Android エミュレータジョブ内で実行する。
#  1. instrumented テスト (debug ビルド): Prune の実際の pm/appops/settings コマンドを shell ユーザーとして
#     実行するエンジンのテストと、全画面を巡回してスクリーンショットを撮る UI テスト。
#  2. 難読化・署名済みのリリース APK のスモークテスト: インストール、起動、クラッシュの確認。
set -u
APKS="${1:-apks}"
OUT="emulator-results"
mkdir -p "$OUT/screens"
STATUS=0

adb wait-for-device
adb shell getprop ro.build.version.release > "$OUT/android-version.txt"
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1

echo "== Install debug + test APKs"
adb install -r -t "$APKS/app/build/outputs/apk/debug/app-debug.apk" || STATUS=1
adb install -r -t "$APKS/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" || STATUS=1
adb shell rm -rf /data/local/tmp/prune-shots
adb shell mkdir -p /data/local/tmp/prune-shots
adb shell chmod 777 /data/local/tmp/prune-shots

echo "== Instrumented tests"
adb logcat -c
adb shell am instrument -w -r \
  io.github.cyb3rl00m.prune.debug.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$OUT/instrumentation.txt"
if grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$OUT/instrumentation.txt"; then STATUS=1; fi
if ! grep -qE "^OK \(" "$OUT/instrumentation.txt"; then STATUS=1; fi
adb logcat -d > "$OUT/logcat-instrumentation.txt"
adb pull /data/local/tmp/prune-shots/. "$OUT/screens/" || true

echo "== Release APK smoke test"
REL=$(ls "$APKS"/dist/Prune-*.apk | head -1)
adb install -r "$REL" || STATUS=1
adb logcat -c
adb shell am start -W -n io.github.cyb3rl00m.prune/.MainActivity | tee "$OUT/release-start.txt"
sleep 8
adb shell screencap -p /data/local/tmp/release-launch.png
adb pull /data/local/tmp/release-launch.png "$OUT/screens/release-launch.png" || true
adb logcat -d > "$OUT/logcat-release.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: io.github.cyb3rl00m.prune" "$OUT/logcat-release.txt"; then
  echo "Release build crashed"; STATUS=1
fi
if ! adb shell pidof io.github.cyb3rl00m.prune >/dev/null; then
  echo "Release build is not running after launch"; STATUS=1
fi

echo "== Exit status $STATUS"
exit $STATUS
