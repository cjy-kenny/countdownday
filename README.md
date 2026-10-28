# CountdownDay 倒数日

一款**纯本地**的 Android 倒数日应用。记录生日、纪念日、考试、节日等重要日子，精准提醒，绝不忘记。

> **零联网、零追踪、零权限滥用**——应用没有申请 INTERNET 权限，从系统层面杜绝了任何数据外发的可能。你的所有数据 100% 保存在设备本地。

[![Release](https://img.shields.io/badge/version-10.3-blue)]() [![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)]() [![License: MIT](https://img.shields.io/badge/license-MIT-lightgrey)]()

## 功能一览

- **多事件管理**：不限数量的事件列表，按"置顶 → 未过期（临近优先）→ 已过期"智能排序
- **丰富的事件属性**：分类标签（生日 / 纪念日 / 考试 / 节日 / 其他）、文字备注、5 种主题色卡片、一键置顶
- **精准提醒**：每个事件独立设置提醒时间（默认 09:00），基于 `AlarmManager.setExactAndAllowWhileIdle`，深度休眠（Doze）下也能准时送达；Android 12+ 自动引导授权精准闹钟权限，未授权时自动降级为 WorkManager 轮询
- **重复事件**：支持"每年 / 每月"重复（生日必备）。到期提醒后自动推进到下一周期并重排闹钟，长期关机后打开也会自动补齐错过的周期
- **桌面小组件**：大字显示最近一个事件（置顶优先）的倒计时天数，卡片背景跟随事件主题色
- **滑动删除 + 撤销**：左滑删除卡片，底部 Snackbar 一键撤销
- **JSON 导出**：调用系统 SAF 保存对话框，自由选择保存位置（无需任何存储权限）
- **个性化外观**：深色模式（跟随系统 / 浅色 / 深色）、Android 12+ 动态取色开关、5 种主题色
- **空状态 / 权限引导**：优美的空状态页；通知权限、电池优化设置引导，保证提醒不被系统拦截
- **无缝升级**：Room 数据库 5 个版本全程自动迁移，历史版本（含最早的 SharedPreferences 单事件版）升级不丢数据

## 为什么是"纯本地"？

很多倒数日应用会要求账号注册、上传你的生日和纪念日到云端。本项目的选择是：

- **没有 INTERNET 权限**：这是 Android 系统级的硬限制，应用即使想联网也会被系统直接拒绝——不是"承诺不传"，而是"不可能传"
- **数据存放在应用私有目录**：其他应用无法读取，卸载应用即彻底清除
- **无账号体系**：打开即用，没有注册登录的干扰

代价是：更换设备时数据不会自动跟随（可使用 JSON 导出功能手动迁移）。这是产品定位上的有意取舍。

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（动态取色 / 自适应深色模式） |
| 架构 | MVVM + Repository，StateFlow 单向数据流 |
| 数据 | Room（当前 v5，含 4 段历史自动迁移脚本） |
| 提醒 | AlarmManager（精准闹钟）+ WorkManager（兜底轮询 + Doze 适配） |
| 构建 | Gradle 8.9 · AGP 8.5.2 · Kotlin 2.0.20 · KSP · R8 混淆 |

## 构建指南

**环境要求**：JDK 17+、Android SDK Platform 34

```bash
git clone https://github.com/cjy-kenny/countdownday.git
cd countdownday

# Debug 包 —— clone 即可构建，无需任何签名配置
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release 包（R8 混淆 + 资源压缩）
# 先在项目根目录创建 keystore.properties（该文件已被 .gitignore 排除，不会提交）：
#
#   storeFile=<你的 keystore 文件绝对路径>
#   storePassword=<keystore 密码>
#   keyAlias=<密钥别名>
#   keyPassword=<密钥密码>
#
./gradlew assembleRelease
```

## 项目结构

```
app/src/main/java/com/example/countdownday/
├── MainActivity.kt        # Compose 界面（列表/卡片/富表单弹窗/设置/个性化）
├── Data.kt                # Room 实体、DAO、数据库（v5 + 迁移链）
├── EventRepository.kt     # 数据仓库层
├── EventViewModel.kt      # ViewModel（StateFlow<UiState>）
├── AlarmScheduler.kt      # 精准闹钟调度（全量重排 + 重复推进）
├── AlarmReceiver.kt       # 闹钟触发 → 通知 → 周期推进
├── EventReminderWorker.kt # 每小时兜底检查（无精准权限时）
├── BootReceiver.kt        # 开机重排闹钟
├── NearestEventWidget.kt  # 桌面小组件
├── AppearancePrefs.kt     # 个性化偏好
├── ColorThemes.kt         # 5 色主题色板
└── ...
```

## 下载安装

前往 [Releases](https://github.com/cjy-kenny/countdownday/releases) 页面下载最新的 APK，直接安装即可（Android 7.0+）。

## License

[MIT](LICENSE)
