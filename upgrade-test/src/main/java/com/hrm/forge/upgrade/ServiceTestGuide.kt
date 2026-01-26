package com.hrm.forge.upgrade

/**
 * Service Hook 测试说明
 * 
 * 本模块包含两个测试 Service，用于验证 Forge 框架的 Service Hook 功能
 * 
 * ## 测试 Service 列表
 * 
 * ### 1. TestService - 普通 startService 测试
 * - 类名: `com.hrm.forge.upgrade.TestService`
 * - 类型: 普通 Service（不支持绑定）
 * - 功能: 
 *   - 接收 startService 调用
 *   - 执行模拟任务（2秒）
 *   - 输出详细日志
 * - 测试方法:
 *   ```kotlin
 *   val manager = HotUpdateManager(context)
 *   manager.testStartService("com.hrm.forge.upgrade.TestService")
 *   ```
 * 
 * ### 2. TestBindService - bindService 测试
 * - 类名: `com.hrm.forge.upgrade.TestBindService`
 * - 类型: 可绑定 Service
 * - 功能:
 *   - 支持 bindService 绑定
 *   - 提供 Binder 接口供客户端调用
 *   - 支持 unbind 和 rebind
 * - 测试方法:
 *   ```kotlin
 *   val intent = Intent()
 *   intent.component = ComponentName(packageName, "com.hrm.forge.upgrade.TestBindService")
 *   intent.putExtra("client_name", "TestClient")
 *   
 *   val connection = object : ServiceConnection {
 *       override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
 *           val binder = service as TestBindService.LocalBinder
 *           val testService = binder.getService()
 *           
 *           // 调用 Service 方法
 *           val result = testService.sendMessage("Hello from client")
 *           Log.i(TAG, result)
 *           
 *           val status = testService.getStatus()
 *           Log.i(TAG, status)
 *       }
 *       
 *       override fun onServiceDisconnected(name: ComponentName?) {
 *           Log.i(TAG, "Service disconnected")
 *       }
 *   }
 *   
 *   bindService(intent, connection, Context.BIND_AUTO_CREATE)
 *   ```
 * 
 * ## 测试步骤
 * 
 * ### 步骤 1: 构建热更新 APK
 * ```bash
 * cd /Users/dehuilin/AndroidStudioProjects/Forge
 * ./gradlew :upgrade-test:assembleDebug
 * ```
 * 生成的 APK 位于: `upgrade-test/build/outputs/apk/debug/upgrade-test-debug.apk`
 * 
 * ### 步骤 2: 复制 APK 到 Assets（可选）
 * 将生成的 APK 复制到 `app/src/main/assets/` 目录，然后使用:
 * ```kotlin
 * manager.releaseFromAssets("upgrade-test-debug.apk") { success, message ->
 *     Log.i(TAG, "Release result: $success, $message")
 * }
 * ```
 * 
 * ### 步骤 3: 重启应用
 * 热更新生效需要重启应用
 * 
 * ### 步骤 4: 测试 startService
 * ```kotlin
 * val manager = HotUpdateManager(context)
 * manager.testStartService("com.hrm.forge.upgrade.TestService")
 * ```
 * 
 * ### 步骤 5: 测试 stopService
 * ```kotlin
 * manager.testStopService("com.hrm.forge.upgrade.TestService")
 * ```
 * 
 * ### 步骤 6: 测试 bindService
 * 参考上面的 bindService 示例代码
 * 
 * ## 验证方式
 * 
 * ### 查看日志
 * ```bash
 * adb logcat -s TestService TestBindService StubService ServiceHelper AMSHookHelper
 * ```
 * 
 * ### 预期日志输出
 * 
 * **启动 TestService:**
 * ```
 * AMSHookHelper: Intercepting startService
 * ServiceHelper: Service NOT registered, replacing with StubService: com.hrm.forge.upgrade.TestService
 * StubService: StubService onCreate
 * StubService: StubService onStartCommand
 * StubService: Creating real service: com.hrm.forge.upgrade.TestService
 * TestService: 🎉 TestService onCreate() - Service 创建成功！
 * TestService: ▶️ TestService onStartCommand() - 第 1 次启动
 * TestService: ⏳ TestService 开始执行任务...
 * TestService: ✅ TestService 任务执行完成
 * ```
 * 
 * **绑定 TestBindService:**
 * ```
 * AMSHookHelper: Intercepting bindService
 * ServiceHelper: Service NOT registered, replacing with StubService: com.hrm.forge.upgrade.TestBindService
 * StubService: StubService onBind
 * TestBindService: 🎉 TestBindService onCreate() - Service 创建成功！
 * TestBindService: 🔗 TestBindService onBind() - 第 1 次绑定
 * TestBindService: 📨 收到消息 #1: Hello from client
 * ```
 * 
 * ## 常见问题
 * 
 * ### Q1: Service 无法启动
 * - 检查热更新 APK 是否正确部署
 * - 检查 Service 类名是否正确（包括包名）
 * - 查看 logcat 是否有错误信息
 * 
 * ### Q2: 日志中没有 Service Hook 相关信息
 * - 确认 AMSHookHelper 是否正确初始化（在 ForgeApplication.attachBaseContext 中）
 * - 检查 ProGuard 是否混淆了关键类
 * 
 * ### Q3: Service 方法调用失败
 * - 确认 Service 字段注入是否成功
 * - 检查 Service.attach() 方法是否正确调用
 * - 查看 StubService 的错误日志
 * 
 * ## 注意事项
 * 
 * 1. **这两个 Service 不会在主 APK 的 AndroidManifest 中注册**
 * 2. **必须通过热更新加载后才能使用**
 * 3. **首次启动应用时这些 Service 不存在**
 * 4. **Service Hook 依赖 AMS Hook，必须在 attachBaseContext 中初始化**
 */
class ServiceTestGuide
