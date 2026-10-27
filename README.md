# CountdownDay 倒数日

一款纯本地的 Android 倒数日应用：Kotlin + Jetpack Compose（Material 3）+ Room + WorkManager + AlarmManager，支持桌面小组件。**无任何网络权限，数据 100% 保存在本机。**

[![Release](https://img.shields.io/badge/version-10.3-blue)]() [![Min SDK](https://img.shields.io/badge/minSdk-24-green)]() [![Target SDK](https://img.shields.io/badge/targetSdk-34-green)]() [![License: MIT](https://img.shields.io/badge/license-MIT-lightgrey)]()

## 功能特性

- **多事件倒数日**：分类（生日/纪念日/考试/节日/其他）、备注、置顶、5 色主题卡片、每年/每月重复规则
- **精准提醒**：每个事件可单独设置提醒时间（默认 09:00）；`AlarmManager.setExactAndAllowWhileIdle` 保证 Doze 下准时触发；Android 12+ 自动申请 `SCHEDULE_EXACT_ALARM`，无权限时降级 WorkManager 轮询
- **重复事件**：每年/每月到期后自动推进到下一周期并重排闹钟
- **滑动删除 + 撤销**：SwipeToDismiss + Snackbar 撤销恢复
- **桌面小组件**：显示最近事件倒计时，按事件主题色渲染
- **JSON 导出**：通过系统 SAF 保存对话框导出全部事件
- **个性化**：深色模式（跟随系统/浅色/深色）、动态取色开关、主题色
- **数据迁移**：旧版 SharedPreferences 数据自动导入；Room 结构升级全自动迁移

## 隐私

- 应用**没有 INTERNET 权限**，不可能发送任何数据到网络
- 全部数据存储在应用私有目录的 Room 数据库中，卸载即清除

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（动态取色）|
| 架构 | MVVM + Repository（StateFlow 单向数据流）|
| 数据 | Room（v5，含 4 段自动迁移）|
| 后台 | WorkManager（每日提醒兜底）+ AlarmManager（精准提醒）|
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

## License

[MIT](LICENSE)
