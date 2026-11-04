# 二课助手（山东科技大学「第二课堂」Android 客户端）

山东科技大学「第二课堂」平台的第三方 Android 客户端。

> 本项目为个人学习与便利性项目，与山东科技大学官方无关，未获官方授权。请遵守学校平台使用规定，仅用于本人账号。

## 功能
- 统一身份认证登录（CAS，账号密码本地加密保存）。
- 校外/手机流量：内置 webVPN 登录（WebView 自动登录 + Cookie 同步 + 代理请求），并支持 Cookie 快过期时静默续期。
- **我的活动**：按状态分组，显示名额进度、打卡阶段，并**直接显示本人签到状态（未签到 / 已签到 / 已签退）**；可「出示签到码」「打卡记录」「取消报名」。
- **活动广场**：关键词 / 种类 / 状态（可单/多选开关）/ 学院 / 时间范围 / 排序 完整筛选；卡片显示报名时间与活动时间、名额进度、学时。可「立即抢」（报名）。
- **活动详情页**：完整信息（种类/主办/学时/时间/名额/地点/联系人/简介/附件）；附件支持预览/打开与下载；未报名可「立即抢」，已报名可「出示签到码/打卡记录/取消报名」。
- **签到/签退**：出示本人二维码给工作人员扫描（每 8 秒刷新）；二维码时间戳采用**服务器时间**（避免手机时钟偏差导致扫码失败），并按 `未签到→签到码 / 已签到→签退码` 自动切换；界面实时提示**「签到成功 / 签退成功 / 尚未收到回执」**，并可「刷新状态」。
- **通知系统**：报名/取消结果通知 + 应用内消息中心（本地持久化、未读角标）。
- 深色模式、主题色、后台保活引导。

## 说明
- 仅供本人账号学习使用，请遵守学校平台规定。

## 构建

需要 **JDK 17** + **Android SDK（compileSdk 34）**，Gradle 8.x（AGP 8.4.1）。

```bash
# Debug
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk

# Release（需本地签名配置，见下）
./gradlew assembleRelease      # 产物 app/build/outputs/apk/release/app-release.apk
```

### Release 签名
仓库**不包含**签名密钥。要打 release 包，请在项目根目录创建 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=keystore/release.jks
storePassword=你的store密码
keyAlias=你的别名
keyPassword=你的key密码
```

并放置对应的 `.jks` 文件。没有该文件时 `assembleRelease` 会产出未签名包。

## 许可证

本项目基于 **GNU General Public License v3.0** 发布，详见 [LICENSE](LICENSE)。

```
Copyright (C) 2026  二课助手 contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
