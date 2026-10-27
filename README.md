# CountdownDay 倒数日

一款离线优先的 Android 倒数日应用：Kotlin + Jetpack Compose（Material 3）+ Room + WorkManager + AlarmManager，支持自定义账号的加密云同步与桌面小组件。

[![Release](https://img.shields.io/badge/version-10.2-blue)]() [![Min SDK](https://img.shields.io/badge/minSdk-24-green)]() [![Target SDK](https://img.shields.io/badge/targetSdk-34-green)]() [![License: MIT](https://img.shields.io/badge/license-MIT-lightgrey)]()

## 功能特性

- **多事件倒数日**：分类（生日/纪念日/考试/节日/其他）、备注、置顶、5 色主题卡片、每年/每月重复规则
- **精准提醒**：每个事件可单独设置提醒时间（默认 09:00）；`AlarmManager.setExactAndAllowWhileIdle` 保证 Doze 下准时触发；Android 12+ 自动申请 `SCHEDULE_EXACT_ALARM`，无权限时降级 WorkManager 轮询
- **重复事件**：每年/每月到期后自动推进到下一周期并重排闹钟
- **滑动删除 + 撤销**：SwipeToDismiss + Snackbar 撤销恢复
- **桌面小组件**：显示最近事件倒计时，按事件主题色渲染
- **加密云同步（离线优先）**：零信任设计——密码不出设备，PBKDF2（40000 轮）派生 AES-256-GCM 密钥加密整包数据后同步；离线修改自动排队，联网后 WorkManager 补传；多设备按 UUID + 时间戳新者胜合并
- **JSON 导出**：通过系统 SAF 保存对话框导出全部事件
- **个性化**：深色模式（跟随系统/浅色/深色）、动态取色开关、主题色
- **空状态/通知权限/电池优化引导**：全套细节体验

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（动态取色）+ Navigation Compose |
| 架构 | MVVM + Repository（StateFlow 单向数据流） |
| 数据 | Room（v5，含 4 段自动迁移） |
| 后台 | WorkManager（同步/每日提醒兜底）+ AlarmManager（精准提醒） |
| 云端 | textdb.online 免费匿名 KV（国内可直连）+ AES-GCM 端侧加密 |
| 构建 | Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20 / KSP / R8 |

## 构建

```bash
# Debug 包（无需任何签名配置，clone 即可构建）
./gradlew assembleDebug

# Release 包：在项目根目录创建 keystore.properties（已被 .gitignore 排除）
# storeFile=<keystore 绝对路径>
# storePassword=<你的密码>
# keyAlias=<别名>
# keyPassword=<你的密码>
./gradlew assembleRelease
```

要求：JDK 17+，Android SDK Platform 34。

## 架构说明

```
UI (Compose) → EventViewModel (StateFlow<UiState>) → EventRepository → Room (SQLite)
                                                      ↘ SyncWorker (WorkManager, 网络约束)
                                                          ↘ SyncEngine → CloudStore (AES-GCM) → textdb.online
提醒链路：AlarmScheduler → AlarmManager (exact) → AlarmReceiver → 通知 + 周期推进 → BootReceiver 重排
```

## 隐私与安全

- 云端只存密文：服务器无法读取任何事件内容；密码永不离开设备
- 忘记密码 = 云端数据无法解密（本地数据不受影响）；账号无找回机制，请牢记密码
- 签名 keystore 与 `keystore.properties` 不在仓库中，请自行生成并妥善保存

## License

[MIT](LICENSE)
