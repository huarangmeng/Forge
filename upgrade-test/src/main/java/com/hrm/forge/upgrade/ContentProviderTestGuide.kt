package com.hrm.forge.upgrade

/**
 * ContentProvider 热更新测试指南
 * 
 * 本指南介绍如何测试 ContentProvider 热更新功能
 * 
 * ## 前置条件
 * 
 * 1. **在主 APK 的 AndroidManifest 中注册占坑 Provider**
 *    ```xml
 *    <provider
 *        android:name="com.hrm.forge.internal.hook.StubContentProvider"
 *        android:authorities="com.hrm.forge.stub.provider"
 *        android:exported="false" />
 *    ```
 * 
 * 2. **确保 Forge 框架已正确初始化**
 *    - ComponentManager 已解析热更 APK 的组件信息
 *    - ContentProviderHook 已执行 Hook 操作
 * 
 * ## 测试流程
 * 
 * ### 1. 构建热更新 APK
 * 
 * ```bash
 * # 在项目根目录执行
 * ./gradlew :upgrade-test:assembleDebug
 * 
 * # 生成的 APK 路径：
 * # upgrade-test/build/outputs/apk/debug/upgrade-test-debug.apk
 * ```
 * 
 * ### 2. 部署热更新 APK
 * 
 * 方法 1：通过 UI 加载（推荐）
 * ```kotlin
 * // 在主 APP 中调用
 * hotUpdateManager.releaseFromAssets("upgrade-test-debug.apk") { result, message ->
 *     if (result.isSuccess) {
 *         Log.i(TAG, "热更新成功，请重启应用")
 *         // 重启应用
 *     } else {
 *         Log.e(TAG, "热更新失败: $message")
 *     }
 * }
 * ```
 * 
 * 方法 2：手动放置 APK
 * ```kotlin
 * // 将 APK 复制到：
 * // /data/data/<package_name>/files/forge/apks/current.apk
 * ```
 * 
 * ### 3. 重启应用
 * 
 * - 完全退出应用（从后台清除）
 * - 重新启动应用
 * - Forge 框架会自动加载热更新 APK
 * 
 * ### 4. 测试查询操作
 * 
 * ```kotlin
 * // 在主 Activity 中
 * val hotUpdateManager = HotUpdateManager(this)
 * 
 * // 查询所有用户
 * hotUpdateManager.testQueryProvider(
 *     authority = "com.hrm.forge.upgrade.test.provider",
 *     path = "users"
 * )
 * 
 * // 查询指定用户
 * hotUpdateManager.testQueryProvider(
 *     authority = "com.hrm.forge.upgrade.test.provider",
 *     path = "users/1"
 * )
 * ```
 * 
 * ### 5. 测试插入操作
 * 
 * ```kotlin
 * // 插入新用户
 * hotUpdateManager.testInsertProvider(
 *     authority = "com.hrm.forge.upgrade.test.provider",
 *     path = "users"
 * )
 * ```
 * 
 * ### 6. 查看日志验证
 * 
 * ```bash
 * # 查看 ContentProvider 日志
 * adb logcat | grep TestContentProvider
 * 
 * # 查看 Hook 日志
 * adb logcat | grep ContentProviderHook
 * 
 * # 查看测试日志
 * adb logcat | grep HotUpdateTester
 * ```
 * 
 * ## 预期日志输出
 * 
 * ### 初始化阶段
 * ```
 * ComponentManager: ✅ ComponentManager initialized in XXms
 * ComponentManager:   - Hot update Providers: 1
 * ContentProviderHook: Start hooking ContentProvider...
 * ContentProviderHook: Found 1 hot update providers
 * ContentProviderHook: Adding authority mapping: com.hrm.forge.upgrade.test.provider -> com.hrm.forge.stub.provider
 * ContentProviderHook: ✅ Successfully mapped authority: com.hrm.forge.upgrade.test.provider -> com.hrm.forge.stub.provider
 * ContentProviderHook: ✅ ContentProvider hook successfully
 * ```
 * 
 * ### 查询操作
 * ```
 * HotUpdateTester: Testing query provider: com.hrm.forge.upgrade.test.provider/users
 * TestContentProvider: 📖 query() 被调用
 * TestContentProvider: URI: content://com.hrm.forge.upgrade.test.provider/users
 * TestContentProvider: 查询所有用户，当前共 3 条数据
 * HotUpdateTester: Query successful: 3 rows, columns: [id, name, age, timestamp]
 * ```
 * 
 * ### 插入操作
 * ```
 * HotUpdateTester: Testing insert provider: com.hrm.forge.upgrade.test.provider/users
 * TestContentProvider: ➕ insert() 被调用
 * TestContentProvider: URI: content://com.hrm.forge.upgrade.test.provider/users
 * TestContentProvider: ✅ 成功插入用户: User(id=4, name=Test User 1234567890, age=25, timestamp=1234567890)
 * TestContentProvider: 当前共 4 条数据
 * HotUpdateTester: Insert successful: content://com.hrm.forge.upgrade.test.provider/users/4
 * ```
 * 
 * ## 常见问题
 * 
 * ### 1. Provider 未找到异常
 * 
 * **现象**：
 * ```
 * java.lang.IllegalArgumentException: Unknown URL content://com.hrm.forge.upgrade.test.provider/users
 * ```
 * 
 * **原因**：
 * - 热更新 APK 未加载
 * - ContentProviderHook 未执行
 * - 占坑 Provider 未注册
 * 
 * **解决方法**：
 * 1. 检查 `Forge.isHotUpdateLoaded()` 返回是否为 true
 * 2. 检查日志确认 ContentProviderHook 已执行
 * 3. 检查主 APK 的 AndroidManifest 是否注册了占坑 Provider
 * 
 * ### 2. Hook 未生效
 * 
 * **现象**：
 * ```
 * ContentProviderHook: No hot update providers found, skip hooking
 * ```
 * 
 * **原因**：
 * - ComponentManager 未正确解析热更新 APK
 * - upgrade-test APK 的 Manifest 中未声明 Provider
 * 
 * **解决方法**：
 * 1. 确认 upgrade-test/src/main/AndroidManifest.xml 中有 provider 声明
 * 2. 重新构建 upgrade-test APK
 * 3. 检查 `ComponentManager.getHotUpdateProviders()` 的返回值
 * 
 * ### 3. 数据不持久化
 * 
 * **现象**：
 * 重启应用后，插入的数据丢失
 * 
 * **原因**：
 * TestContentProvider 使用内存存储（List），数据仅在进程内有效
 * 
 * **说明**：
 * 这是测试实现的限制，实际应用应使用 SQLite 等持久化方案
 * 
 * ## 高级测试
 * 
 * ### 1. 自定义 ContentProvider
 * 
 * ```kotlin
 * class MyContentProvider : ContentProvider() {
 *     companion object {
 *         const val AUTHORITY = "com.example.my.provider"
 *     }
 *     
 *     override fun onCreate(): Boolean {
 *         Log.i(TAG, "MyContentProvider created")
 *         return true
 *     }
 *     
 *     // 实现其他方法...
 * }
 * ```
 * 
 * ### 2. 测试多个 Provider
 * 
 * ```kotlin
 * // 测试 Provider 1
 * hotUpdateManager.testQueryProvider("com.example.provider1", "table1")
 * 
 * // 测试 Provider 2
 * hotUpdateManager.testQueryProvider("com.example.provider2", "table2")
 * ```
 * 
 * ### 3. 性能测试
 * 
 * ```kotlin
 * val startTime = System.currentTimeMillis()
 * 
 * repeat(100) {
 *     contentResolver.query(uri, null, null, null, null)?.close()
 * }
 * 
 * val elapsedTime = System.currentTimeMillis() - startTime
 * Log.i(TAG, "100 次查询耗时: ${elapsedTime}ms")
 * ```
 * 
 * ## 注意事项
 * 
 * 1. **占坑 Provider 必须注册**
 *    - 在主 APK 的 AndroidManifest 中注册
 *    - Authority 必须是 "com.hrm.forge.stub.provider"
 * 
 * 2. **热更新 Provider 不能在主 APK 中注册**
 *    - 只能在热更新 APK 的 Manifest 中声明
 *    - 如果在主 APK 中注册，则不需要 Hook
 * 
 * 3. **Authority 必须唯一**
 *    - 每个 Provider 必须有唯一的 Authority
 *    - 不能与主 APK 的 Provider 冲突
 * 
 * 4. **线程安全**
 *    - ContentProvider 的方法可能在多个线程中被调用
 *    - 需要考虑线程安全问题
 * 
 * 5. **生命周期**
 *    - Provider 在首次访问时创建
 *    - onCreate() 在主线程执行
 *    - 其他方法可能在 Binder 线程池中执行
 * 
 * ## 参考资料
 * 
 * - [Android ContentProvider 官方文档](https://developer.android.com/guide/topics/providers/content-providers)
 * - [ContentProvider 最佳实践](https://developer.android.com/guide/topics/providers/content-provider-creating)
 * - Forge 框架源码：forge/src/main/java/com/hrm/forge/internal/hook/ContentProviderHook.kt
 */
object ContentProviderTestGuide