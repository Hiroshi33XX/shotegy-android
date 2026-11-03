# Shotegy Android · 拍序

原生 Android 人像摄影策划助手，使用 Kotlin 与 Jetpack Compose。支持项目策划、拍摄脚本、资源库、踩点机位与太阳光线规划。

**[下载最新版 APK](https://github.com/Hiroshi33XX/shotegy-android/releases/latest/download/Shotegy-Android.apk)** · [全部版本](https://github.com/Hiroshi33XX/shotegy-android/releases) · [反馈问题](https://github.com/Hiroshi33XX/shotegy-android/issues)

Android 8.0 及以上。支持简体中文、繁體中文和 English。用户无需编译，下载 APK 后允许相应来源安装即可。GitHub 下载可用性取决于所在网络。

## 功能

- 项目、拍摄信息、小场景、镜头脚本、参考图、造型与道具、拍摄进度和月历。
- 大小卡片、景别与小场景分组画廊，以及现场拍摄模式。
- 资源库、项目模板、引导词和方法收藏。
- 应用内踩点拍照，记录位置与真北镜头朝向，计算太阳光线时间窗；需要相机、定位与可靠的设备传感器。
- 策划长图、逐条脚本图片、PDF，以及 iOS 格式的项目、资源库和完整备份文件。
- 数据保存在本机，无账号或云同步；应用不声明联网权限。卸载前请导出备份。

## 安装与升级

官方版本使用维护者保管的长期发布密钥签名。每个 Release 同时提供校验值、签名证书指纹和对应源码归档。后续正式版本通常可覆盖升级，请在升级前备份。

**从此前调试版迁移：先在旧版“我的 → 完整数据备份”导出文件，并确认文件已保存到应用外。正式签名与调试签名不同，无法直接覆盖时，备份完成后再卸载旧版、安装正式版并导入备份。请勿先卸载。**

## 开源与 iOS 版

Android 原创代码按 [GPL-3.0-only](LICENSE) 发布，保留版权。允许遵守许可证的学习、修改、商业使用和再分发。品牌使用见 [品牌政策](TRADEMARKS.md)，依赖与字体见 [第三方声明](THIRD_PARTY_NOTICES.md)。

本许可证仅适用于本仓库；独立的 Shotegy iOS 应用及私有源码不在此次授权范围内。两端并非自动同步项目，通过手动备份文件交换内容。社区贡献不会自动用于闭源 iOS 版，见 [贡献说明](CONTRIBUTING.md)。

本版尚未完成所有 Android 设备与 iPhone 的并排验收；真实定位、传感器和系统分享行为因设备而异。iOS 购买权益尚未接入 Android。请勿只保留一份重要数据。

## 构建

安装 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0，在 `local.properties` 配置 `sdk.dir` 或设置 `ANDROID_HOME`：

```sh
./gradlew assembleDebug testDebugUnitTest
```

Windows 使用 `gradlew.bat`。未配置官方密钥时可正常构建调试包与未签名 release 包。签名发布、密钥备份和校验见 [发布说明](docs/releasing.md)。iOS 参考源码、本机 SDK、Gradle 缓存、测试导出文件和签名私钥均不在公开源码中。

## English

Shotegy is a native Android portrait shoot planner. It includes shot lists,
reference galleries, templates, on-location scouting and sunlight planning,
and image/PDF exports. Simplified Chinese, Traditional Chinese and English
are supported. Android 8.0+ is required.

[Download the latest APK](https://github.com/Hiroshi33XX/shotegy-android/releases/latest/download/Shotegy-Android.apk).
Back up your data before upgrading or uninstalling. When migrating from a debug
build, export a full backup **before** removing that build, then install the
official release and import the backup.

Original Android code is GPL-3.0-only; brand rights are reserved. Third-party
components retain their own licenses. The private iOS app is outside this
repository's license. There is no account or cloud sync; projects remain local.
