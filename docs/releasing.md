# 正式发布与密钥管理

官方仓库： https://github.com/Hiroshi33XX/shotegy-android

1. 使用 `python scripts/setup_signing.py` 首次创建签名材料，需要 JDK 的 keytool。脚本不覆盖已有密钥、不输出密码；Windows 下收紧目录权限。
2. **立即将整个 `.signing` 目录备份到自己保管的离线介质或加密保险库。** keystore 和密码均不可公开、提交 Git、上传 Release 或添加到 Issue。密码保存在本机 `.signing/signing.properties`，仅用于维护者构建。该目录不属于公开源码包。
3. JDK 17，运行 `./gradlew assembleRelease testDebugUnitTest`；本机已配置的兼容 JDK 亦可使用。Gradle 检测本地 signing.properties 后才启用正式签名。不存在该文件时只生成未签名 release，不能发布。
4. 用 Android SDK `apksigner verify --verbose --print-certs` 验证 release APK，确认签名、包名、版本和 `debuggable=false`。与首次发布的证书 SHA-256 指纹对比。
5. 提交已审核源码，创建对应版本标签；生成且审核 `git archive` 源码包。检查不包含 SDK、iOS 参考源码、客户数据、本机配置、密钥或凭据。
6. GitHub Release 先保存为 draft，上传 `Shotegy-Android.apk`、版本对应源码压缩包、`SHA256SUMS.txt`、`SIGNING-CERTIFICATE.txt`，核对上传大小和校验值后发布。
7. 每次递增 versionCode，保持 applicationId 与发布签名。首页固定下载地址依赖每版附件名保持 `Shotegy-Android.apk`。

GitHub CI 仅运行无密钥构建/检查，不持有发布密钥。正式签名与上传在维护者本机进行。GPL 源码与 APK 同版发布，不以付费或注册为获取源码的前提。

首次正式版采用独立发布签名；此前调试版必须先导出完整备份，完成备份后才可卸载迁移。未安装正式版的用户可直接安装。

签名公钥证书可公开；私钥不可公开。密钥丢失后，GitHub 分发版本通常无法继续原包名直接更新，因此离线备份是维护者必须完成的步骤。
