# Forge Demo App

这是 Forge 热更新框架的演示应用，展示了如何使用 Forge 实现 Android 应用的热更新功能。

## 项目结构

```
app/src/main/java/com/hrm/forge/demo/
├── DemoApp.kt                    # 演示 Application（继承 ForgeApplication）
├── DemoApplicationLike.kt        # ApplicationLike 实现
├── MainActivity.kt               # 主界面（使用 Compose）
└── HotUpdateManager.kt           # 热更新管理器
```

## 核心组件

### 1. DemoApp

继承自 `ForgeApplication`，是应用的入口：

```kotlin
class DemoApp : ForgeApplication() {
    override fun getApplicationLike(): String {
        return "com.hrm.forge.demo.DemoApplicationLike"
    }
    
    override fun onCreate() {
        super.onCreate()
        Forge.init(this)
    }
}
```

### 2. DemoApplicationLike

实际的应用逻辑类，所有业务代码都应该写在这里：

```kotlin
class DemoApplicationLike(private val context: Context) {
    fun attachBaseContext(base: Context) { }
    fun onCreate() { }
    fun onTerminate() { }
    fun onLowMemory() { }
    fun onTrimMemory(level: Int) { }
}
```

### 3. HotUpdateManager

封装了热更新相关的操作：

- `releaseNewVersion()` - 发布新版本
- `downloadAndRelease()` - 下载并发布新版本
- `rollbackToLastVersion()` - 回滚到上一版本
- `cleanLastVersion()` - 清理上一版本
- `getVersionInfo()` - 获取版本信息

### 4. MainActivity

使用 Jetpack Compose 构建的主界面，展示：

- 当前版本信息
- 热更新操作按钮
- 版本管理功能

## 功能演示

### 1. 查看版本信息

启动应用后，会显示：
- 是否已加载热更新
- 当前版本号
- 构建号
- APK 路径
- SHA1 校验值

### 2. 发布新版本

通过 `HotUpdateManager` 发布新版本：

```kotlin
hotUpdateManager.releaseNewVersion(
    apkFilePath = "/path/to/new.apk",
    version = "1.0.1"
) { success, message ->
    if (success) {
        // 提示用户重启应用
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

## 配置说明

### AndroidManifest.xml

```xml
<application
    android:name=".DemoApp"
    ...>
    <activity android:name=".MainActivity" .../>
</application>
```

### build.gradle.kts

```kotlin
dependencies {
    implementation(project(":forge"))
}
```

## 使用流程

### 1. 开发阶段

1. 在 `DemoApplicationLike` 中编写业务代码
2. 正常开发和调试

### 2. 发布热更新

1. 构建新版本 APK
2. 将 APK 上传到服务器或放到设备存储
3. 在应用中调用 `releaseNewVersion()`
4. 提示用户重启应用

### 3. 应用重启

下次启动时会自动加载新版本的代码和资源。

## 测试建议

### 1. 基础测试

```kotlin
// 1. 安装并启动应用
// 2. 查看版本信息（应该显示"无"）
// 3. 准备一个新版本 APK
// 4. 调用 releaseNewVersion()
// 5. 重启应用
// 6. 查看版本信息（应该显示新版本）
```

### 2. 回滚测试

```kotlin
// 1. 发布版本 1.0.1
// 2. 重启应用
// 3. 发布版本 1.0.2
// 4. 重启应用
// 5. 调用 rollbackToLastVersion()
// 6. 重启应用
// 7. 应该回到版本 1.0.1
```

### 3. 清理测试

```kotlin
// 1. 发布多个版本
// 2. 调用 cleanLastVersion()
// 3. 查看文件系统，上一版本应该被删除
```

## 注意事项

1. **APK 路径**：确保提供的 APK 文件路径正确且可访问
2. **包名匹配**：新版本 APK 的包名必须与当前应用一致
3. **重启生效**：发布新版本后必须重启应用才能生效
4. **存储权限**：如果 APK 在外部存储，需要申请存储权限
5. **网络下载**：`downloadAndRelease()` 方法需要自行实现下载逻辑

## 扩展功能

### 1. 添加网络下载

在 `HotUpdateManager.downloadApk()` 中实现：

```kotlin
// 使用 OkHttp 下载
val client = OkHttpClient()
val request = Request.Builder().url(url).build()
val response = client.newCall(request).execute()

val destFile = File(context.cacheDir, "update.apk")
response.body?.byteStream()?.use { input ->
    FileOutputStream(destFile).use { output ->
        input.copyTo(output)
    }
}
```

### 2. 添加版本检查

```kotlin
// 从服务器获取最新版本信息
fun checkUpdate(callback: (hasUpdate: Boolean, version: String?) -> Unit) {
    // 调用 API 获取最新版本
    // 比较当前版本
    // 返回结果
}
```

### 3. 添加更新进度

```kotlin
// 在下载时显示进度条
fun downloadWithProgress(url: String, onProgress: (Int) -> Unit) {
    // 下载并更新进度
}
```

## 常见问题

**Q: 发布新版本后为什么没有生效？**
A: 需要重启应用才能加载新版本。

**Q: 如何验证新版本是否加载成功？**
A: 查看日志输出或使用 `getVersionInfo()` 查看当前版本信息。

**Q: 新版本加载失败怎么办？**
A: Forge 会自动尝试回滚到上一个可用版本，也可以手动调用 `rollbackToLastVersion()`。

**Q: 支持增量更新吗？**
A: 当前版本不支持，需要提供完整的 APK 文件。

**Q: 可以更新 Native 代码吗？**
A: 可以，Forge 支持 SO 库的动态加载（仅限 arm64-v8a）。

## 相关文档

- [Forge 框架 README](../forge/README.md)
- [实现文档](../forge/IMPLEMENTATION.md)
