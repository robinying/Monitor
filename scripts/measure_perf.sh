#!/usr/bin/env bash
# T2M Monitor 性能/功耗采样辅助脚本。
# 用法: ./scripts/measure_perf.sh [包名] [采样时长秒数]
# 需要已连接设备并安装对应 APK；脚本只读采集，不修改设备状态。
set -euo pipefail

PKG="${1:-com.t2m.monitor.debug}"
DURATION="${2:-600}"

echo "== package: $PKG, duration: ${DURATION}s =="
adb shell am start -n "$PKG/.ui.MainActivity" >/dev/null
adb shell dumpsys batterystats --reset >/dev/null 2>&1 || true

echo "== baseline meminfo =="
adb shell dumpsys meminfo "$PKG" | sed -n '1,20p'

echo "== periodic top (every 60s) =="
end=$((SECONDS + DURATION))
while [ $SECONDS -lt $end ]; do
  adb shell top -b -n 1 -o PID,%CPU,RES,ARGS 2>/dev/null | grep -m1 "$PKG" || true
  sleep 60
done

echo "== final meminfo =="
adb shell dumpsys meminfo "$PKG" | sed -n '1,20p'

echo "== batterystats =="
adb shell dumpsys batterystats "$PKG" | sed -n '1,40p'

echo "== done. 手动记录：常驻 CPU、PSS、待机耗电（熄屏 1 小时对比电量） =="
