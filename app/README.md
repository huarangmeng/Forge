# Forge Demo App

这是 Forge 热更新框架的演示应用，展示了两种集成方式和热更新功能的完整使用。

## 📦 项目结构

```
app/src/main/java/com/hrm/forge/demo/
├── DemoApp.kt                      # 方案一：继承 ForgeApplication
├── DemoApplicationLike.kt          # 方案一的 ApplicationLike
├── manual/
│   ├── ManualDemoApp.kt           # 方案二：手动安装
│   └── ManualApplicationLike.kt   # 方案二的 ApplicationLike
├── MainActivity.kt                 # 主界面（Compose UI）
└── HotUpdateManager.kt            # 热更新管理器
```

## 🎯 两种集成方式

Forge 提供两种集成方式，你可以根据项目需求选择：

### 方案一：继承 ForgeApplication（推荐 ⭐）

**适用场景**：新项目或可以自由继承的项目

**实现代码**：见 `DemoApp.kt`

```kotlin
class DemoApp : ForgeApplication() {
    override fun getApplicationLike(): String {
        return "com.hrm.forge.demo.DemoApplicationLike"
    }
}
```

**特点**：
- ✅ **最简单**：只需继承并返回 ApplicationLike 类名
- ✅ **自动管理**：SDK 自动处理所有生命周期转发
- ✅ **零配置**：无需手动调用任何方法
- ✅ **代码最少**：3 行代码完成集成

**AndroidManifest.xml 配置**：

```xml
<application
    android:name=".DemoApp"
    ...>
</application>
```

---

### 方案二：手动安装（灵活 🔧）

**适用场景**：已有 Application 基类的项目

**实现代码**：见 `manual/ManualDemoApp.kt`

```kotlin
class ManualDemoApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        // 安装 Forge
        Forge.install(
            application = this,
            applicationLikeClassName = "com.hrm.forge.demo.manual.ManualApplicationLike"
        )
    }

    override fun onCreate() {
        super.onCreate()
        Forge.dispatchOnCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
        Forge.dispatchOnTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Forge.dispatchOnLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Forge.dispatchOnTrimMemory(level)
    }
}
```

**特点**：
- ✅ **更灵活**：可以继承任意 Application 基类
- ✅ **完全控制**：手动控制生命周期转发
- ⚙️ **需配置**：需要在每个生命周期方法中调用 dispatch
- 📝 **代码略多**：需要转发 5 个生命周期方法

**AndroidManifest.xml 配置**：

```xml
<application
    android:name=".manual.ManualDemoApp"
    ...>
</application>
```

---

### 两种方案对比

| 特性 | 方案一（继承） | 方案二（手动） |
|------|--------------|--------------|
| **继承要求** | 必须继承 ForgeApplication | 可继承任意 Application |
| **代码量** | ⭐ 最少（3 行） | ⭐⭐ 略多（需转发生命周期） |
| **生命周期转发** | 自动 | 手动 |
| **ApplicationLike 管理** | 自动 | 自动 |
| **灵活性** | 低 | 高 |
| **推荐场景** | 新项目 | 已有 Application 基类 |

**核心原则**：无论哪种方案，ApplicationLike 的生命周期都由 SDK 自动管理，用户只需：
- 方案一：继承即可
- 方案二：手动转发 Application 生命周期

---

## 🔧 核心组件

### ApplicationLike

实际的应用逻辑类，所有热更新业务代码都应该写在这里：

```kotlin
class DemoApplicationLike(private val context: Context) {
    fun attachBaseContext(base: Context) {
        // 初始化框架
    }
    
    fun onCreate() {
        // 业务初始化（这里的代码会被热更新）
    }
    
    fun onTerminate() { }
    fun onLowMemory() { }
    fun onTrimMemory(level: Int) { }
}
```

**重要规则**：
- ✅ **ApplicationLike 中的代码会被热更新**
- ❌ **Application 类本身不会被热更新**
- ✅ 所有业务逻辑应写在 ApplicationLike 中
- ❌ 不要在 Application 中初始化业务代码

### HotUpdateManager

封装了热更新相关的操作：

- `releaseNewVersion()` - 发布新版本
- `releaseFromAssets()` - 从 Assets 发布
- `rollbackToLastVersion()` - 回滚到上一版本
- `cleanLastVersion()` - 清理上一版本
- `getVersionInfo()` - 获取版本信息

### MainActivity

使用 Jetpack Compose 构建的主界面，展示：

- 当前版本信息
- 热更新操作按钮
- 版本管理功能

---

## 🚀 功能演示

### 1. 查看版本信息

启动应用后，会显示：
- 基础版本
- 当前运行版本
- 下次启动版本
- 是否已加载热更新
- 是否有待生效的更改
- APK 路径和 SHA1 校验值

### 2. 发布新版本

通过 `HotUpdateManager` 发布新版本：

```kotlin
lifecycleScope.launch {
    hotUpdateManager.releaseNewVersion(
        apkFilePath = "/path/to/new.apk"
    ) { result, message ->
        if (result.isSuccess) {
            // 提示用户重启应用
        }
    }
}
```

### 3. 回滚版本

如果新版本有问题，可以回滚到上一版本：

```kotlin
hotUpdateManager.rollbackToLastVersion { success, message ->
    if (success) {
        // 提示用户重启应用
    }
}
```

### 4. 清理旧版本

清理上一个版本的文件：

```kotlin
hotUpdateManager.cleanLastVersion { success, message ->
    // 处理结果
}
```

---

## ⚙️ 配置说明

### 切换集成方式

在 `AndroidManifest.xml` 中修改 `android:name`：

```xml
<!-- 方案一：继承方式 -->
<application android:name=".DemoApp" ...>

<!-- 方案二：手动方式 -->
<application android:name=".manual.ManualDemoApp" ...>
```

### build.gradle.kts

```kotlin
dependencies {
    // 从 Maven Central 引入
    implementation("io.github.huarangmeng:forge:1.0.0")
    
    // 或者本地 module
    // implementation(project(":forge"))
}
```

---

## 📝 使用流程

### 1. 选择集成方式

- 新项目 → 使用方案一（继承 ForgeApplication）
- 已有基类 → 使用方案二（手动安装）

### 2. 开发阶段

1. 在 `ApplicationLike` 中编写业务代码
2. 正常开发和调试
3. ⚠️ 业务逻辑必须写在 ApplicationLike 中

### 3. 发布热更新

1. 构建新版本 APK
2. 将 APK 上传到服务器或放到设备存储
3. 在应用中调用 `releaseNewVersion()`
4. 提示用户重启应用

### 4. 应用重启

下次启动时会自动加载新版本的代码和资源。

---

## ✅ 测试建议

### 1. 基础测试

```bash
# 1. 安装并启动应用
./gradlew :app:installDebug

# 2. 查看版本信息（应该显示基础版本）
# 3. 准备一个新版本 APK
# 4. 调用 releaseNewVersion()
# 5. 重启应用
# 6. 查看版本信息（应该显示新版本）
```

### 2. 回滚测试

```bash
# 1. 发布版本 1.0.1 → 重启
# 2. 发布版本 1.0.2 → 重启
# 3. 调用 rollbackToLastVersion() → 重启
# 4. 应该回到版本 1.0.1
```

### 3. 切换集成方式测试

```bash
# 测试两种集成方式的一致性
# 1. 使用方案一测试热更新
# 2. 卸载应用
# 3. 切换到方案二（修改 AndroidManifest）
# 4. 重新测试热更新
# 5. 验证功能完全一致
```

---

## ⚠️ 注意事项

### 代码热更新范围

| 代码位置 | 是否会被热更新 | 说明 |
|---------|--------------|------|
| **Application 类** | ❌ 不会 | Application 本身不会被热更新 |
| **ApplicationLike 类** | ✅ 会 | 所有业务代码应该写在这里 |
| **Activity/Service/Receiver** | ✅ 会 | 四大组件都可以被热更新 |
| **普通类和方法** | ✅ 会 | ApplicationLike 引用的所有代码 |

### 其他注意事项

1. **APK 路径**：确保提供的 APK 文件路径正确且可访问
2. **包名匹配**：新版本 APK 的包名必须与当前应用一致
3. **重启生效**：发布新版本后必须重启应用才能生效
4. **存储权限**：如果 APK 在外部存储，需要申请存储权限
5. **架构要求**：仅支持 arm64-v8a 设备

---

## 🔌 扩展功能

### 1. 添加网络下载

```kotlin
// 使用 Kotlin 协程下载
suspend fun downloadApk(url: String): File = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()

    val destFile = File(context.cacheDir, "update.apk")
    response.body?.byteStream()?.use { input ->
        FileOutputStream(destFile).use { output ->
            input.copyTo(output)
        }
    }
    destFile
}
```

### 2. 添加版本检查

```kotlin
// 从服务器获取最新版本信息
suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
    // 调用 API 获取最新版本
    val latestVersion = api.getLatestVersion()
    val currentVersion = Forge.getCurrentVersionInfo(context)
    
    if (latestVersion.versionCode > currentVersion.currentVersionCode) {
        UpdateInfo(latestVersion.versionName, latestVersion.downloadUrl)
    } else {
        null
    }
}
```

### 3. 添加更新进度

```kotlin
// 在下载时显示进度条
fun downloadWithProgress(url: String, onProgress: (Int) -> Unit) {
    // 使用 Flow 发送进度
    flow {
        // 下载并 emit 进度
    }.collect { progress ->
        onProgress(progress)
    }
}
```

---

## ❓ 常见问题

**Q: 应该选择哪种集成方式？**  
A: 
- 新项目 → 方案一（继承 ForgeApplication）更简单
- 已有 Application 基类 → 方案二（手动安装）更灵活

**Q: 两种方式功能有区别吗？**  
A: 没有区别，功能完全一致，只是集成方式不同。

**Q: 发布新版本后为什么没有生效？**  
A: 需要重启应用才能加载新版本。

**Q: 如何验证新版本是否加载成功？**  
A: 查看日志输出或使用 `getVersionInfo()` 查看当前版本信息。

**Q: 新版本加载失败怎么办？**  
A: Forge 会自动尝试回滚到上一个可用版本，也可以手动调用 `rollbackToLastVersion()`。

**Q: 哪些代码会被热更新？**  
A: ApplicationLike 及其引用的所有代码都会被热更新，但 Application 类本身不会。

**Q: 可以新增 Activity 吗？**  
A: 可以！Forge 支持四大组件的新增和修改。

**Q: 可以更新 Native 代码吗？**  
A: 可以，Forge 支持 SO 库的动态加载（仅限 arm64-v8a）。

---

## 📖 相关文档

- [Forge 框架 README](../forge/README.md)