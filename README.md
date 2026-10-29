# Monitor

Android 手机状态监控应用：实时采集设备指标，通过三态悬浮窗与仪表盘展示，支持历史曲线、CSV 导出与阈值告警。所有数据仅保存在本机。

## 功能

| 能力 | 说明 |
|------|------|
| 实时指标 | 电池（电量/温度/电流/电压）、CPU（整机 + 每核）、内存、存储、设备温区、前台应用与今日流量、系统帧率、应用存储占用 |
| 悬浮窗 | 悬浮球 / 迷你条 / 面板三态，拖拽吸边、位置与形态持久化、长按菜单 |
| 常驻监控 | 前台服务（`specialUse`）+ 常驻通知，支持显示/隐藏悬浮窗、暂停采样、退出 |
| 历史数据 | Room 本地库，10 秒聚合写入，1h/6h/24h/7d 曲线，保留 7 天原始样本 + 90 天小时聚合 |
| 导出 | 五列指标 CSV（UTF-8 BOM），写入系统下载目录并可分享 |
| 告警 | 电池温度/电量/内存/存储阈值，连续采样与去抖，通知 + 历史列表 |
| 特权扩展 | 安装并授权 Shizuku 后可读取系统帧率；未安装时相关功能自动隐藏 |
| 诊断 | 采集器成功率、最近耗时、错误环形缓冲（仅本次运行） |

## 多语言

支持英语（默认）、简体中文、法语、德语、葡萄牙语；切换系统语言或使用 Android 的「应用语言」即可生效。CSV 表头、通知、悬浮窗、无障碍描述均已本地化。

## 环境与构建

- JDK 21，Android SDK Platform 36（`compileSdk 36` / `targetSdk 36` / `minSdk 26`）
- Gradle Wrapper 8.13，AGP 8.13.2，Kotlin 2.2.21

```bash
./gradlew assembleDebug        # 调试包（包名 com.t2m.monitor.debug）
./gradlew testDebugUnitTest    # 单元测试
./gradlew lintDebug            # 静态检查
./gradlew assembleRelease      # R8 压缩后的 release 包（默认未签名）
```

### Release 签名

签名信息通过 Gradle 属性提供，建议写入 `~/.gradle/gradle.properties`（不要提交到版本库）：

```properties
MONITOR_KEYSTORE_PATH=/path/to/monitor.jks
MONITOR_KEYSTORE_PASSWORD=******
MONITOR_KEY_ALIAS=monitor
MONITOR_KEY_PASSWORD=******
```

未提供时 `assembleRelease` 输出 `app-release-unsigned.apk`，可用于构建校验，但无法直接安装。

### 安装与权限

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.t2m.monitor.debug SYSTEM_ALERT_WINDOW allow
```

| 权限 | 用途 | 缺失时行为 |
|------|------|----------|
| 悬浮窗（特殊授权） | 在其他应用上层显示状态 | 悬浮窗按钮引导授权，不建窗 |
| 通知（Android 13+ 运行时） | 常驻通知与告警 | 服务仍运行，通知由系统任务管理器展示 |
| 使用情况访问（特殊授权） | 前台应用、今日流量、应用存储占用 | 相关卡片显示提示或隐藏 |
| 电池优化白名单（可选） | 降低后台被清理概率 | 仅提示，不阻塞 |
| Shizuku（可选） | 系统帧率 | 功能自动隐藏 |

## 技术栈

Kotlin 2.2.21 · Jetpack Compose（Material3）· Hilt · Room · DataStore · WorkManager · Coroutines/Flow · Shizuku 13.1.5（可选）

架构：采集器（`Collector`）→ 采样调度 → `MonitorEngine` 合并快照 → 前台服务持有生命周期 → 界面/悬浮窗订阅；Room 负责历史，WorkManager 负责保留策略。

```
app/src/main/java/com/t2m/monitor/
├── core/        时钟、日志、格式化、运行态、功能开关
├── data/        采集器、模型、Room、DataStore、权限、特权层
├── domain/      采样调度、引擎、聚合、告警、导出、诊断
├── service/     前台服务与控制器
├── overlay/     悬浮窗控制器与触摸处理
├── work/        数据保留任务
└── ui/          仪表盘、历史、诊断、权限引导、主题
```

## 测试与质量门禁

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease
```

当前状态：62 个单元测试通过，lint 0 error / 0 warning，release R8 构建成功（约 1.4 MB）；已在 **Fairphone 6 / Android 17** 完成真机验收，核心流程通过（详见 `docs/plan.md` §19）。剩余未覆盖项见 `docs/release-checklist.md`。

## 隐私

- 未申请 `INTERNET` 权限，不存在任何数据上传通道。
- 采集数据（含应用包名）仅写入本机 Room 数据库，可一键清空。
- 关闭 `allowBackup`，数据不参与云备份与设备迁移。
- 详见 `docs/privacy.md`。

## 文档

- `docs/plan.md`：实现计划与 M0–M6 交付记录、偏差与待验证清单
- `docs/release-checklist.md`：发布门禁、回归矩阵、性能功耗测量
- `docs/privacy.md`：隐私说明
- `scripts/measure_perf.sh`：adb 性能/功耗采样辅助脚本

## 界面

- Material 3 + 自定义排版/配色，浅色与深色主题
- 电量环、语义色阈值（电量/温度/内存/存储）、统一卡片与状态胶囊
- 仪表盘分区：监控控制 / 实时状态；历史支持范围与指标切换；诊断页展示成功率进度

## 真机验收摘要

Fairphone 6（Android 17 / SDK 37）实测通过：安装与冷启动（release 425 ms）、权限引导、前台服务与通知、悬浮窗三态/拖拽吸边/位置与形态持久化/旋转夹取、电池与内存/存储/温区数值、历史曲线与刷新、CSV 导出、温度与低电量告警、告警落库、清空数据、进程被杀后数据持久化、权限撤销降级、诊断页。

release 空闲（服务 + 悬浮窗）PSS 41.7 MB、`top` CPU 0%；前台诊断页 PSS 97.5 MB（接近 100 MB 预算）。

## 已知限制

- **Android 17 部分机型限制 `/proc/stat`**：CPU 使用率不可用，界面降级为核数与 `/sys` 频率展示（实测 8 核 / 2.50 GHz）。
- Shizuku 系统帧率未在真机验证（设备未安装 Shizuku）；解析基于 `dumpsys SurfaceFlinger`，图层命名随 ROM 差异可能失败（失败时自动隐藏）。
- 内存 ≥90%、存储 <2 GB 告警未实测；数据库 v1→v2 迁移未实测；Android 8/9、折叠屏、国产 ROM 未覆盖。
- 小时级待机耗电与长时间泄漏测试未做。
- 分应用流量排行、自定义告警阈值、静音时段、悬浮窗点击穿透、开机自启未实现。
- 葡萄牙语使用通用 `values-pt`（覆盖 pt-PT / pt-BR），未单独提供巴西葡语。
- Android 8/9 的 CSV 导出写入应用专属目录，经分享面板获取。
