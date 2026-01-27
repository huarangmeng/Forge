# Forge - Android 热更新框架

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.huarangmeng/forge.svg)](https://central.sonatype.com/artifact/io.github.huarangmeng/forge)

Forge 是一个基于 Kotlin 开发的 Android 热更新框架，支持动态加载 DEX、资源文件和 SO 库，实现应用的热更新功能。

## ✨ 特性

- ✅ **DEX 热更新**：动态加载新版本的 DEX 文件
- ✅ **资源热更新**：动态替换资源文件
- ✅ **SO 库热更新**：动态加载 Native 库（仅支持 arm64-v8a）
- ✅ **版本管理**：支持版本回滚和清理
- ✅ **安全校验**：SHA1 文件完整性校验
- ✅ **纯 Kotlin**：全部使用 Kotlin 编写，零 Java 依赖
- ✅ **简单易用**：API 设计简洁，易于集成
- ✅ **轻量级**：无第三方依赖，仅依赖 kotlinx-coroutines

## 🆚 与 Tinker 对比

| 核心能力 | Forge | Tinker |
|---------|-------|--------|
| **更新方式** | 🚀 整包热更新 | 🔧 补丁修复 |
| **更新文件** | 完整 APK | 差分 Patch 文件 |
| **DEX 更新** | ✅ 完整替换 | ✅ 差量修复 |
| **资源更新** | ✅ 完整替换 | ✅ 差量修复 |
| **SO 库更新** | ✅ 完整替换（arm64-v8a） | ✅ 差量修复（多架构） |
| **四大组件** | ✅ 完全支持 | ❌ 不支持新增 |
| **Activity** | ✅ 支持新增/修改 | ⚠️ 仅支持修改 |
| **Service** | ✅ 支持新增/修改 | ⚠️ 仅支持修改 |
| **BroadcastReceiver** | ✅ 支持新增/修改 (有限制*) | ⚠️ 仅支持修改 |
| **ContentProvider** | ✅ 支持新增/修改 | ⚠️ 仅支持修改 |
| **差分生成** | ❌ 不需要 | ✅ 需要 oldApk + newApk |
| **更新场景** | 版本升级 + Bug 修复 | Bug 修复 |

### Forge 的核心优势

**🚀 真正的热更新，而非热补丁**

- **整包替换**：直接使用新版本完整 APK，无需生成差分补丁
- **无需 oldApk**：不依赖基准版本，任何版本都能直接更新
- **支持大版本升级**：可以跨版本更新，不限于小修小补
- **四大组件完全自由**：可以新增/删除 Activity、Service 等，无任何限制

**🎯 Tinker 的局限性**

- **仅限热补丁**：只能修复已有代码的 bug，无法新增功能
- **四大组件限制**：不能新增组件，AndroidManifest 更改不生效
- **依赖差分**：必须基于 oldApk 生成 patch，服务端需存储所有基准版本
- **版本碎片化**：不同基准版本需要不同补丁，管理复杂

### 适用场景对比

**选择 Forge 的场景**：
- ✅ 需要快速发布新功能（如新增页面、新增模块）
- ✅ 需要进行较大的版本升级
- ✅ 希望简化发版流程（无需差分生成）
- ✅ 需要动态新增四大组件
- ✅ 纯 Kotlin 项目，追求现代化

**选择 Tinker 的场景**：
- ✅ 仅需修复小 bug，不涉及新功能
- ✅ 需要极小的更新包体积（差量更新）
- ✅ 需要支持 32 位设备
- ✅ 成熟项目，已有 Tinker 生态

## 📦 安装

### 方式一：Maven Central（推荐）

在 `gradle/libs.versions.toml` 中添加依赖：

```toml
[versions]
forge = "1.0.0"

[libraries]
forge = { module = "io.github.huarangmeng:forge", version.ref = "forge" }
```

在 app 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(libs.forge)
}
```

### 方式二：直接依赖

在 app 模块的 `build.gradle.kts` 中直接添加：

```kotlin
dependencies {
    implementation("io.github.huarangmeng:forge:1.0.0")
}
```

## 系统要求

- **最低 SDK**：Android 7.0 (API 24)
- **架构支持**：仅支持 arm64-v8a（64位）
- **编译 SDK**：36

## 🚀 快速开始

### 1. 创建 Application

```kotlin
import com.hrm.forge.ForgeApplication

class MyApp : ForgeApplication() {
    override fun getApplicationLike(): String {
        return "com.example.app.MyApplicationLike"
    }
}
```

### 2. 创建 ApplicationLike

```kotlin
class MyApplicationLike(private val context: Context) {
    fun attachBaseContext(base: Context) {
        // 初始化代码
    }
    
    fun onCreate() {
        // 业务初始化
    }
    
    fun onTerminate() { }
    fun onLowMemory() { }
    fun onTrimMemory(level: Int) { }
}
```

### 3. 配置 AndroidManifest.xml

```xml
<application
    android:name=".MyApp"
    ...>
</application>
```

### 4. 发布新版本

```kotlin
import com.hrm.forge.Forge
import java.io.File

// 在协程中调用
lifecycleScope.launch {
    val result = Forge.releaseNewApk(
        context = context,
        apkFile = File("/path/to/new.apk")
    )
    
    if (result.isSuccess) {
        // 提示用户重启应用
    }
}
```

就这么简单！🎉

## 📚 主要 API

### Forge 类

```kotlin
// 手动安装（如果不继承 ForgeApplication）
Forge.install(application, "com.example.MyApplicationLike")

// 发布新版本
suspend fun releaseNewApk(context: Context, apkFile: File): ReleaseResult

// 获取当前版本信息
Forge.getCurrentVersionInfo(context): VersionInfo

// 回滚到上一版本
Forge.rollbackToLastVersion(context): Boolean

// 清理上一版本
Forge.cleanLastVersion(context): Boolean

// 检查是否已加载热更新
Forge.isHotUpdateLoaded(): Boolean

// 获取热更新版本号
Forge.getHotUpdateVersion(): String?

// 设置日志级别
Forge.setLogLevel(LogLevel.DEBUG)
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

### BroadcastReceiver 限制说明

BroadcastReceiver 的热更新支持有以下限制：

#### ✅ 完全支持的场景

| 场景 | 支持程度 | 说明 |
|------|---------|------|
| **修改已有 Receiver** | ✅ 完全支持 | DEX 热更新会自动覆盖旧代码 |
| **动态注册 Receiver** | ✅ 完全支持 | 与普通 Receiver 无任何区别 |
| **显式广播** | ✅ 完全支持 | 可以发送到热更新 APK 中新增的 Receiver |
| **隐式广播（应用运行时）** | ✅ 完全支持 | Forge 自动解析 Manifest 并拦截匹配的广播 |

#### ⚠️ 不支持的场景

| 场景 | 支持程度 | 说明 |
|------|---------|------|
| **应用未运行时接收广播** | ❌ 不支持 | 需要应用进程存活（与真正的静态注册不同） |

#### 详细说明

**1. 动态注册（推荐方式）**
```kotlin
// ✅ 完全支持，无任何限制
class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 处理广播
    }
}

// 在代码中动态注册
val receiver = MyReceiver()
context.registerReceiver(receiver, IntentFilter("MY_ACTION"))
```

**2. 静态注册（有限制）**
```kotlin
// ✅ 支持：在热更新 APK 的 Manifest 中声明
// upgrade-test/src/main/AndroidManifest.xml
<receiver android:name=".MyReceiver" android:exported="false">
    <intent-filter android:priority="100">
        <action android:name="com.example.MY_ACTION" />
    </intent-filter>
</receiver>

// 发送广播，应用运行时会收到
context.sendBroadcast(Intent("com.example.MY_ACTION"))

// ❌ 应用未运行时无法接收
// 原因：热更新 Receiver 依赖进程存活，无法被系统唤醒
```

**工作原理：**
- Forge 自动解析热更新 APK 的 AndroidManifest.xml
- 提取 Receiver 的 IntentFilter 配置（action、priority 等）
- Hook AMS 拦截隐式广播，匹配并手动分发到热更新 Receiver
- 按优先级排序，避免重复分发

**核心限制：**
- 静态注册的 Receiver 只能在**应用进程运行时**接收广播
- 应用未运行时，系统无法唤醒热更新 Receiver（需要真正的静态注册）

**建议：**
- 需要在应用未运行时接收广播的功能，必须在**主 APK** 中提前声明 Receiver
- 其他场景优先使用**动态注册**（更灵活）


## 文档

- [Forge 框架文档](forge/README.md)
- [Demo 应用文档](app/README.md)

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

- Issues: [GitHub Issues](https://github.com/huarangmeng/Forge/issues)
