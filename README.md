# Forge - Android 热更新框架

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Forge 是一个基于 Kotlin 开发的 Android 热更新框架，支持动态加载 DEX、资源文件和 SO 库，实现应用的热更新功能。

## 特性

- ✅ **DEX 热更新**：动态加载新版本的 DEX 文件
- ✅ **资源热更新**：动态替换资源文件
- ✅ **SO 库热更新**：动态加载 Native 库（仅支持 arm64-v8a）
- ✅ **版本管理**：支持版本回滚和清理
- ✅ **安全校验**：SHA1 文件完整性校验
- ✅ **纯 Kotlin**：全部使用 Kotlin 编写
- ✅ **简单易用**：API 设计简洁，易于集成

## 项目结构

```
Forge/
├── forge/              # 核心框架 module
│   ├── builder/       # APK 构建和版本管理
│   ├── common/        # 工具类
│   ├── loader/        # 加载器模块
│   └── logger/        # 日志系统
├── app/               # Demo 应用 module
│   ├── DemoApp.kt
│   ├── DemoApplicationLike.kt
│   ├── MainActivity.kt
│   └── HotUpdateManager.kt
└── docs/              # 文档
```

## 系统要求

- **最低 SDK**：Android 7.0 (API 24)
- **架构支持**：仅支持 arm64-v8a（64位）
- **编译 SDK**：36

## 快速开始

### 1. 集成框架

在项目的 `settings.gradle.kts` 中添加 forge module：

```kotlin
include(":forge")
```

在 app 模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation(project(":forge"))
}
```

### 2. 创建 Application

```kotlin
import com.hrm.forge.loader.ForgeApplication

class MyApp : ForgeApplication() {
    override fun getApplicationLike(): String {
        return "com.example.app.MyApplicationLike"
    }
    
    override fun onCreate() {
        super.onCreate()
        Forge.init(this)
    }
}
```

### 3. 创建 ApplicationLike

```kotlin
class MyApplicationLike(private val context: Context) {
    fun attachBaseContext(base: Context) { }
    fun onCreate() { }
    fun onTerminate() { }
    fun onLowMemory() { }
    fun onTrimMemory(level: Int) { }
}
```

### 4. 配置 AndroidManifest.xml

```xml
<application
    android:name=".MyApp"
    ...>
</application>
```

### 5. 发布新版本

```kotlin
import com.hrm.forge.Forge
import java.io.File

// 在协程中调用
val success = Forge.releaseNewApk(
    context = context,
    apkFile = File("/path/to/new.apk"),
    version = "1.0.1"
)

if (success) {
    // 提示用户重启应用
}
```

## 主要 API

### Forge 类

```kotlin
// 初始化
Forge.init(application)

// 发布新版本（挂起函数）
suspend fun releaseNewApk(context: Context, apkFile: File, version: String): Boolean

// 获取当前版本信息
Forge.getCurrentVersionInfo(context): VersionInfo?

// 获取当前版本号
Forge.getCurrentVersion(): String?

// 检查是否已加载
Forge.isLoaded(): Boolean

// 回滚到上一版本
Forge.rollbackToLastVersion(context): Boolean

// 清理上一版本
Forge.cleanLastVersion(context): Boolean

// 设置日志级别
Forge.setLogLevel(Logger.LogLevel.DEBUG)
```

## Demo 应用

项目包含一个完整的 Demo 应用，展示了 Forge 的使用方法：

- 查看当前版本信息
- 发布新版本
- 回滚到上一版本
- 清理旧版本
- 使用 Jetpack Compose 构建的现代化 UI

### 构建配置

Demo 应用的构建配置：

- **Debug 版本**：未混淆，便于开发调试和测试热更新功能
- **Release 版本**：开启混淆和优化，展示生产环境的使用方式

### 运行 Demo

```bash
# 安装 Debug 版本
./gradlew :app:installDebug

# 构建 Release 版本
./gradlew :app:assembleRelease
```

## 工作原理

### 热更新流程

```
1. 发布新版本
   └─ 复制 APK 到应用目录
   └─ 计算 SHA1 校验值
   └─ 预加载 DEX（优化启动）
   └─ 保存版本信息

2. 应用启动
   └─ Application.attachBaseContext()
   └─ ForgeAllLoader.loadNewApk()
       ├─ 验证版本
       ├─ 加载 DEX
       ├─ 加载资源
       └─ 加载 SO

3. 加载成功
   └─ 创建 ApplicationLike 实例
   └─ 调用生命周期方法
```

### 核心模块

- **ForgeClassLoader**：DEX 动态加载，支持 Android 7.0+
- **ForgeResourceLoader**：资源动态替换
- **ForgeLoadLibrary**：SO 库动态加载（arm64-v8a）
- **ForgeBuilderService**：版本管理和 APK 处理

## 版本管理

Forge 自动管理版本：

- **当前版本**：正在使用的版本
- **上一个版本**：保留用于回滚
- **自动清理**：删除更旧的版本（保留最近 2 个）

### 回滚机制

如果新版本加载失败，Forge 会自动尝试回滚到上一个可用版本。

## ProGuard 配置

如果启用混淆（如 Release 构建），请添加以下规则：

```proguard
# 保持 Forge 核心类
-keep class com.hrm.forge.** { *; }

# 保持 Instrumentation（关键）
-keep class com.hrm.forge.loader.instrumentation.InstrumentationProxy {
    public ** execStartActivity(...);
    public ** newActivity(...);
}

# 保持 ApplicationLike 生命周期方法
-keepclassmembers class * {
    public void attachBaseContext(android.content.Context);
    public void onCreate();
}
```

**重要提示**：Demo 应用的 Release 版本已开启混淆，ProGuard 规则已配置在 `app/proguard-rules.pro` 中。

## 注意事项

1. **架构限制**：仅支持 arm64-v8a（64位）
2. **最低版本**：需要 Android 7.0 (API 24) 及以上
3. **包名匹配**：新版本 APK 的包名必须与宿主应用一致
4. **重启生效**：发布新版本后需要重启应用
5. **混淆规则**：关键类不能被混淆

## 文档

- [Forge 框架文档](forge/README.md)
- [实现详解](forge/IMPLEMENTATION.md)
- [Demo 应用文档](app/README.md)

## 测试

### 单元测试

```bash
./gradlew :forge:test
```

### 集成测试

```bash
./gradlew :app:connectedAndroidTest
```

## 常见问题

**Q: 新版本为什么没有生效？**
A: 需要重启应用才能加载新版本。

**Q: 支持 32 位设备吗？**
A: 不支持，仅支持 arm64-v8a 架构。

**Q: 可以更新 Native 代码吗？**
A: 可以，支持 SO 库的动态加载。

**Q: 如何验证是否加载成功？**
A: 使用 `Forge.isLoaded()` 或查看日志。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License

## 联系方式

- Issues: [GitHub Issues](https://github.com/your-repo/forge/issues)
- Email: your-email@example.com

