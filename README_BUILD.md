# S44 英雄战力工具 - 原生 Android 源码

这是 **原生 Kotlin + Jetpack Compose** 工程。

- APK 内不使用 WebView
- APK 内不打包 HTML
- 三首音乐已经拆成 Android `res/raw`
- 计算逻辑已迁移到 Kotlin
- PPTX 由 Kotlin 直接生成/读取
- 输入全部使用 App 内自定义玻璃数字/数据键盘
- 底栏由 Compose 原生固定渲染
- 官网跳转使用 Android Intent
- 设置和输入使用 SharedPreferences 持久化

## GitHub 编译

1. 新建 GitHub 仓库
2. 把本压缩包解压后的全部内容上传到仓库根目录
3. 打开 Actions
4. 运行 `Build Android APK`
5. 在 Artifacts 下载 `S44英雄战力工具_1.0.0`

工作流生成的是 Android Debug 签名 APK，可直接安装测试。

## Android Studio 编译

推荐 Android Studio + JDK 17。

打开工程后等待 Gradle Sync，然后：

`Build > Build APK(s)`

使用 Gradle 8.9 和 Android Gradle Plugin 8.7.3。
