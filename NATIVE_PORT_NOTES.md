# 原生迁移说明

基线文件 SHA256 见 `SOURCE_BASELINE.sha256`。

本工程不是网页壳：
- 没有 WebView
- 没有 `.html` 文件进入 `app/src`
- 没有通过 assets 运行网页代码

UI 由 Jetpack Compose 原生组件重建。
主要计算公式、巅峰参考映射、45 场逻辑、PPTX 字段、水印、作者验证、三首音乐、公告、主题、记忆、官网入口和自定义键盘均在 Kotlin 层实现。

PPTX 采用 OOXML Zip 直接生成，不依赖浏览器 JavaScript 库。
