# iOS 手动备份兼容层

对应 iOS commit `2101ea43cd10b98e6957ff91f8ea805d0f9fa049` 的 BackupService 与 ProjectTemplatePayload。实现位于 `IOSBackup.kt`，入口位于“我的 → 数据备份”。

## 文件与版本

- `.shotegy`：ZIP 根目录 `project.json`，format=shotegy-project，版本 2/3，图片位于 media/。
- `.shotegy-library`：ZIP 根目录 `library.json`，format=shotegy-library，版本 1；模板 content 为版本 1/2 JSON 字符串。
- `.shotegy-backup`：ZIP 根目录 `workspace.json`，format=shotegy-workspace，版本 1，含项目、资源库和 mediaMap。

导出遵循相同清单字段、英文枚举、ISO 8601 时间和媒体映射。模板提供 Swift Codable 所需 UUID 和字段。独立原图及缩略图均包含在 ZIP 中；导出媒体扩展名按文件头识别，不依赖 Android 私有文件名。

## 转换及保留信息

Project.iosMetadata 随本机数据和旧 Android JSON 备份保存。清单中的媒体路径被转换为私有媒体 ID；内部 `_androidId`/`_androidName` 关联当前脚本和场景。导出叠加 Android 当前编辑内容，再移除所有内部字段。独立缩略图、同名场景原始名称与身份、未改动的拍摄日期、方向双精度值得以保留。模板复制会重映射内部脚本关联，并排除拍摄和客户信息。

## 导入事务

ZIP 先流式读取至应用缓存的随机目录。拒绝路径穿越、反斜杠、盘符、非预期目录、重复条目、CRC 错误、未知版本、无效引用和不受支持图片。限制单条目 128 MB、清单 16 MB、累计解压 512 MB 和 10,000 条目。

全部解析通过后把图片复制到新的私有 ID，显示确认；确认前不修改项目、资源和收藏夹。确认时 Store.appendInterchange 在一次 SharedPreferences.commit 中追加全部集合，成功后发布界面状态。保存失败回滚；回滚也失败时进入保护状态并保留可能被引用的新媒体。取消或解析失败清理本次新增文件。原有媒体不重写。

导入采用追加策略，每次生成新的项目、资源和收藏夹标识；重复导入可能产生副本，确认框已明确说明。旧 Android JSON 仅保留读取迁移与替换恢复，不再提供导出，入口与确认文案明确区分。iOS 自身的 Pro 数量限制由 iOS 应用执行。

## 验证范围

IOSBackupTest 按 iOS 源码结构构造样本，验证三种文件往返、模板 Codable 结构、媒体字节、独立缩略图、同名场景、时区和方向精度、mediaMap、取消清理、CRC 错误与保存失败回滚。尚未在真实 iPhone App 上完成双设备验收。
