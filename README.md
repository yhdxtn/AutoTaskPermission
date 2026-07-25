# 自动任务助手 v1.0.0

一个原生 Android 权限准备页。首页只有两个权限入口：

- 悬浮窗权限：跳转到系统“显示在其他应用上层”设置。
- 无障碍权限：跳转到系统无障碍服务设置。

返回 App 后会在 `onResume()` 中重新检查权限，并更新两个开关的状态。

## v1.0.0 范围

本版本 **不执行** 自动点击、控件查找、坐标点击、手势或后台任务。`AutomationAccessibilityService` 只是空服务占位，便于用户在系统设置中看到并授权。

## 工程信息

- 包名：`com.autotask.permission`
- 最低 Android：6.0（API 23）
- 编译 SDK：35
- 目标 SDK：35
- Android Gradle Plugin：9.3.0
- Gradle：9.5.0
- 无第三方运行时依赖

## 打开和运行

1. 使用支持 AGP 9.3 的 Android Studio 打开工程根目录。
2. 首次同步时安装 Android SDK 35。
3. 工程提供 `gradlew` / `gradlew.bat` 轻量启动脚本。首次同步会从 Gradle 官方地址下载 Gradle 9.5.0，并校验 SHA-256。
4. 安装 JDK 17、Android SDK 35 与 Android SDK Build Tools 36.0.0。
5. 连接 Android 6.0 及以上设备，运行 `app`。

> 说明：当前交付环境无法直接附带标准二进制 `gradle-wrapper.jar`，因此启动脚本采用等效的“下载、校验、解压、执行”流程。也可以在本机执行 `gradle wrapper --gradle-version 9.5.0` 换成标准 Gradle Wrapper。

## 后续版本建议

后续实现自动化时，建议按以下优先级：

1. 根据控件文本、资源 ID、描述等定位 `AccessibilityNodeInfo` 并执行 `ACTION_CLICK`。
2. 控件不可点击时向上查找可点击父节点。
3. 找不到控件时，再使用按屏幕宽高比例换算后的坐标手势作为兜底。
4. 所有任务由用户明确创建和启动，并提供立即停止入口、目标应用白名单和执行日志。

## 发布提醒

无障碍服务属于高敏感能力。发布到应用商店前，需要提供清晰的应用内说明、数据使用披露，并遵守平台关于 AccessibilityService API 的政策。
