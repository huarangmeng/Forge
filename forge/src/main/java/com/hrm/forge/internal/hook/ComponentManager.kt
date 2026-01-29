package com.hrm.forge.internal.hook

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.hrm.forge.internal.log.Logger
import java.io.File

/**
 * 组件信息管理器（内部实现）
 *
 * 使用策略模式 + 泛型统一管理四大组件：Activity、Service、BroadcastReceiver、ContentProvider
 *
 * 优化重点：
 * 1. 消除重复代码，提取通用组件处理逻辑
 * 2. 使用泛型管理组件注册信息
 * 3. 策略模式处理不同组件的特殊逻辑
 * 4. 提高可扩展性和可维护性
 *
 * @hide 此类仅供内部使用，不对外暴露
 */
internal object ComponentManager {

    private const val TAG = "ComponentInfoManager"

    /**
     * Intent extra key: 真实的 Service 类名
     */
    const val KEY_REAL_SERVICE = "REAL_SERVICE_CLASS"

    /**
     * Intent extra key: 真实的 BroadcastReceiver 类名
     */
    const val KEY_REAL_RECEIVER = "forge_real_receiver"

    // 占坑 Activity 映射：launchMode -> 占坑 Activity 类名
    private val STUB_ACTIVITIES = mapOf(
        ActivityInfo.LAUNCH_MULTIPLE to "com.hrm.forge.internal.hook.StubActivityStandard",
        ActivityInfo.LAUNCH_SINGLE_TOP to "com.hrm.forge.internal.hook.StubActivitySingleTop",
        ActivityInfo.LAUNCH_SINGLE_TASK to "com.hrm.forge.internal.hook.StubActivitySingleTask",
        ActivityInfo.LAUNCH_SINGLE_INSTANCE to "com.hrm.forge.internal.hook.StubActivitySingleInstance"
    )

    // 组件注册表（使用泛型统一管理）
    private val activityRegistry = ComponentRegistry<ActivityMeta>()
    private val serviceRegistry = ComponentRegistry<String>()
    private val receiverRegistry = ComponentRegistry<String>()
    private val providerRegistry = ComponentRegistry<ProviderInfo>()

    // Receiver 特殊配置
    private val receiverConfigMap = mutableMapOf<String, MutableList<ReceiverConfig>>()
    private val receiverInstanceCache = mutableMapOf<String, android.content.BroadcastReceiver>()

    // 是否已初始化
    private var isInitialized = false

    // ==================== 数据模型 ====================

    /**
     * Activity 元数据
     */
    data class ActivityMeta(
        val className: String,
        val launchMode: Int
    )

    /**
     * ContentProvider 配置信息
     */
    data class ProviderInfo(
        val className: String,
        val authority: String,
        val exported: Boolean = false
    )

    /**
     * BroadcastReceiver 配置信息
     *
     * @property receiverClass Receiver 完整类名
     * @property actions 监听的 Action 列表
     * @property priority 优先级
     * @property exported 是否导出
     */
    data class ReceiverConfig(
        val receiverClass: String,
        val actions: List<String>,
        val priority: Int = 0,
        val exported: Boolean = false
    )

    // ==================== 泛型组件注册表 ====================

    /**
     * 组件注册表：统一管理主 APK 和热更新 APK 中的组件
     *
     * @param T 组件元数据类型
     */
    private class ComponentRegistry<T> {
        private val mainComponents = mutableMapOf<String, T>()
        private val hotUpdateComponents = mutableMapOf<String, T>()

        fun registerMain(key: String, value: T) {
            mainComponents[key] = value
        }

        fun registerHotUpdate(key: String, value: T) {
            if (!mainComponents.containsKey(key)) {
                hotUpdateComponents[key] = value
            }
        }

        fun isRegisteredInMain(key: String): Boolean = mainComponents.containsKey(key)

        fun isInHotUpdate(key: String): Boolean = hotUpdateComponents.containsKey(key)

        fun exists(key: String): Boolean =
            mainComponents.containsKey(key) || hotUpdateComponents.containsKey(key)

        fun get(key: String): T? = hotUpdateComponents[key] ?: mainComponents[key]

        fun getHotUpdateComponents(): Map<String, T> = hotUpdateComponents.toMap()

        fun getAllKeys(): List<String> = (mainComponents.keys + hotUpdateComponents.keys).toList()

        fun mainSize(): Int = mainComponents.size

        fun hotUpdateSize(): Int = hotUpdateComponents.size

        fun clear() {
            mainComponents.clear()
            hotUpdateComponents.clear()
        }
    }

    // ==================== 组件解析策略 ====================

    /**
     * 组件解析策略接口
     * 定义了解析主 APK 和热更新 APK 组件的通用方法
     */
    private interface ComponentParser<T> {
        /**
         * 从 PackageInfo 解析主 APK 的组件
         */
        fun parseMainComponents(packageInfo: PackageInfo)

        /**
         * 从 PackageInfo 解析热更新 APK 的组件
         */
        fun parseHotUpdateComponents(packageInfo: PackageInfo)

        /**
         * 获取组件类型名称（用于日志）
         */
        fun getComponentTypeName(): String
    }

    /**
     * Activity 解析策略
     */
    private object ActivityParser : ComponentParser<ActivityMeta> {
        override fun parseMainComponents(packageInfo: PackageInfo) {
            packageInfo.activities?.forEach { activityInfo ->
                val meta = ActivityMeta(activityInfo.name, activityInfo.launchMode)
                activityRegistry.registerMain(activityInfo.name, meta)
                Logger.d(
                    TAG,
                    "Main activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}"
                )
            }
        }

        override fun parseHotUpdateComponents(packageInfo: PackageInfo) {
            packageInfo.activities?.forEach { activityInfo ->
                val meta = ActivityMeta(activityInfo.name, activityInfo.launchMode)
                activityRegistry.registerHotUpdate(activityInfo.name, meta)
                if (!activityRegistry.isRegisteredInMain(activityInfo.name)) {
                    Logger.d(
                        TAG,
                        "Hot update activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}"
                    )
                }
            }
        }

        override fun getComponentTypeName(): String = "Activity"
    }

    /**
     * Service 解析策略
     */
    private object ServiceParser : ComponentParser<String> {
        override fun parseMainComponents(packageInfo: PackageInfo) {
            packageInfo.services?.forEach { serviceInfo ->
                serviceRegistry.registerMain(serviceInfo.name, serviceInfo.name)
                Logger.d(TAG, "Main service: ${serviceInfo.name}")
            }
        }

        override fun parseHotUpdateComponents(packageInfo: PackageInfo) {
            packageInfo.services?.forEach { serviceInfo ->
                serviceRegistry.registerHotUpdate(serviceInfo.name, serviceInfo.name)
                if (!serviceRegistry.isRegisteredInMain(serviceInfo.name)) {
                    Logger.d(TAG, "Hot update service: ${serviceInfo.name}")
                }
            }
        }

        override fun getComponentTypeName(): String = "Service"
    }

    /**
     * BroadcastReceiver 解析策略
     */
    private object ReceiverParser : ComponentParser<String> {
        override fun parseMainComponents(packageInfo: PackageInfo) {
            packageInfo.receivers?.forEach { receiverInfo ->
                receiverRegistry.registerMain(receiverInfo.name, receiverInfo.name)
                Logger.d(TAG, "Main receiver: ${receiverInfo.name}")
            }
        }

        override fun parseHotUpdateComponents(packageInfo: PackageInfo) {
            packageInfo.receivers?.forEach { receiverInfo ->
                receiverRegistry.registerHotUpdate(receiverInfo.name, receiverInfo.name)
                if (!receiverRegistry.isRegisteredInMain(receiverInfo.name)) {
                    Logger.d(TAG, "Hot update receiver: ${receiverInfo.name}")
                }
            }
        }

        override fun getComponentTypeName(): String = "Receiver"
    }

    /**
     * ContentProvider 解析策略
     */
    private object ProviderParser : ComponentParser<ProviderInfo> {
        override fun parseMainComponents(packageInfo: PackageInfo) {
            packageInfo.providers?.forEach { providerInfo ->
                val info =
                    ProviderInfo(providerInfo.name, providerInfo.authority, providerInfo.exported)
                providerRegistry.registerMain(providerInfo.authority, info)
                Logger.d(
                    TAG,
                    "Main provider: ${providerInfo.name}, authority: ${providerInfo.authority}"
                )
            }
        }

        override fun parseHotUpdateComponents(packageInfo: PackageInfo) {
            packageInfo.providers?.forEach { providerInfo ->
                val info =
                    ProviderInfo(providerInfo.name, providerInfo.authority, providerInfo.exported)
                providerRegistry.registerHotUpdate(providerInfo.authority, info)
                if (!providerRegistry.isRegisteredInMain(providerInfo.authority)) {
                    Logger.d(
                        TAG,
                        "Hot update provider: ${providerInfo.name}, authority: ${providerInfo.authority}"
                    )
                }
            }
        }

        override fun getComponentTypeName(): String = "Provider"
    }

    /**
     * 通用组件解析器：使用策略模式统一处理不同组件的解析
     */
    private fun <T> parseComponents(
        context: Context,
        apkPath: String?,
        parser: ComponentParser<T>,
        isMainApk: Boolean
    ) {
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS

            val packageInfo = if (isMainApk) {
                pm.getPackageInfo(context.packageName, flags)
            } else {
                apkPath?.let { pm.getPackageArchiveInfo(it, flags) }
            }

            if (packageInfo == null) {
                Logger.e(TAG, "Failed to parse ${if (isMainApk) "main" else "hot update"} APK")
                return
            }

            if (isMainApk) {
                parser.parseMainComponents(packageInfo)
            } else {
                parser.parseHotUpdateComponents(packageInfo)
            }

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse ${parser.getComponentTypeName()}", e)
        }
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化组件信息
     *
     * @param context 上下文
     * @param hotUpdateApkPath 热更新 APK 路径
     */
    fun init(context: Context, hotUpdateApkPath: String?) {
        if (isInitialized) {
            Logger.i(TAG, "ComponentManager already initialized, skip")
            return
        }

        Logger.i(TAG, "Initializing ComponentInfoManager")

        try {
            val startTime = System.currentTimeMillis()

            // 使用策略模式统一解析所有组件
            val parsers = listOf(ActivityParser, ServiceParser, ReceiverParser, ProviderParser)

            // 1. 解析主 APK 的所有组件
            parsers.forEach { parser ->
                parseComponents(context, null, parser, isMainApk = true)
            }

            // 2. 解析热更新 APK 的所有组件
            if (hotUpdateApkPath != null && File(hotUpdateApkPath).exists()) {
                parsers.forEach { parser ->
                    parseComponents(context, hotUpdateApkPath, parser, isMainApk = false)
                }

                // 解析 Receiver 的 IntentFilter 配置
                if (receiverRegistry.hotUpdateSize() > 0) {
                    parseReceiverIntentFilters(context, hotUpdateApkPath)
                }
            } else {
                Logger.w(TAG, "Hot update APK not found, skip parsing: $hotUpdateApkPath")
            }

            val elapsedTime = System.currentTimeMillis() - startTime

            isInitialized = true
            Logger.i(TAG, "✅ ComponentManager initialized in ${elapsedTime}ms")
            logComponentStats()

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize ComponentInfoManager", e)
        }
    }

    /**
     * 打印组件统计信息
     */
    private fun logComponentStats() {
        Logger.i(TAG, "Components summary:")
        Logger.i(TAG, "  - Main Activities: ${activityRegistry.mainSize()}")
        Logger.i(TAG, "  - Hot update Activities: ${activityRegistry.hotUpdateSize()}")
        Logger.i(TAG, "  - Main Services: ${serviceRegistry.mainSize()}")
        Logger.i(TAG, "  - Hot update Services: ${serviceRegistry.hotUpdateSize()}")
        Logger.i(TAG, "  - Main Receivers: ${receiverRegistry.mainSize()}")
        Logger.i(TAG, "  - Hot update Receivers: ${receiverRegistry.hotUpdateSize()}")
        Logger.i(TAG, "  - Main Providers: ${providerRegistry.mainSize()}")
        Logger.i(TAG, "  - Hot update Providers: ${providerRegistry.hotUpdateSize()}")
    }

    /**
     * 解析 BroadcastReceiver 的 IntentFilter 配置
     *
     * 使用多种方法从 ActivityInfo 中提取 IntentFilter：
     * 1. 尝试直接读取 filters 或 intentFilter 字段（推荐）
     * 2. 兜底通过 PackageParser 解析
     */
    @Suppress("DEPRECATION")
    private fun parseReceiverIntentFilters(context: Context, apkPath: String) {
        try {
            Logger.d(TAG, "Parsing receiver IntentFilters from ActivityInfo...")

            val pm = context.packageManager
            val flags = PackageManager.GET_RECEIVERS
            val packageInfo = pm.getPackageArchiveInfo(apkPath, flags)

            if (packageInfo == null || packageInfo.receivers == null) {
                Logger.w(TAG, "Failed to get package info or receivers")
                return
            }

            // 遍历每个 Receiver 的 ActivityInfo
            packageInfo.receivers?.forEach { receiverInfo ->
                val receiverName = receiverInfo.name

                // 只处理热更新 APK 中新增的 Receiver
                if (!receiverRegistry.isInHotUpdate(receiverName)) {
                    return@forEach
                }

                Logger.d(TAG, "Parsing IntentFilters for receiver: $receiverName")

                // 直接解析 Manifest XML 获取 IntentFilter（包括 actions 和 priority）
                val (actions, priority) = getIntentFilterFromManifest(apkPath, receiverName)

                if (actions.isEmpty()) {
                    Logger.w(TAG, "  └─ ⚠️ No actions found for receiver: $receiverName")
                    return@forEach
                }

                // 创建 ReceiverConfig
                val config = ReceiverConfig(
                    receiverClass = receiverName,
                    actions = actions,
                    priority = priority,
                    exported = receiverInfo.exported
                )

                // 将配置添加到映射表
                actions.forEach { action ->
                    receiverConfigMap.getOrPut(action) { mutableListOf() }.add(config)
                    Logger.d(TAG, "  ├─ Action: $action, priority: $priority")
                }
            }

            Logger.i(TAG, "✅ Parsed ${receiverConfigMap.size} receiver IntentFilter actions")

        } catch (e: Exception) {
            Logger.w(TAG, "Failed to parse receiver IntentFilters: ${e.message}")
            Logger.w(TAG, "Stack trace:", e)
        }
    }

    /**
     * 直接解析热更新 APK 的 AndroidManifest.xml 获取 IntentFilter
     *
     * @param apkPath 热更新 APK 路径
     * @param receiverName Receiver 完整类名
     * @return Pair<actions, priority>
     */
    private fun getIntentFilterFromManifest(
        apkPath: String,
        receiverName: String
    ): Pair<List<String>, Int> {
        val actions = mutableListOf<String>()
        var priority = 0

        try {
            // 使用 AssetManager 解析 APK 中的 AndroidManifest.xml
            val assetManager = android.content.res.AssetManager::class.java.newInstance()
            val addAssetPathMethod = android.content.res.AssetManager::class.java
                .getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.invoke(assetManager, apkPath)

            val resources = android.content.res.Resources(
                assetManager,
                android.content.res.Resources.getSystem().displayMetrics,
                android.content.res.Resources.getSystem().configuration
            )

            // 解析 AndroidManifest.xml
            val parser = assetManager.openXmlResourceParser("AndroidManifest.xml")

            var inReceiver = false
            var inIntentFilter = false
            var currentReceiverName = ""

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "receiver" -> {
                                // 获取 receiver 的 android:name 属性
                                val nameAttr = parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "name"
                                )
                                currentReceiverName = resolveClassName(nameAttr, receiverName)
                                inReceiver = (currentReceiverName == receiverName)
                            }

                            "intent-filter" -> {
                                if (inReceiver) {
                                    inIntentFilter = true
                                    // 获取 priority 属性
                                    val priorityAttr = parser.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android",
                                        "priority"
                                    )
                                    if (priorityAttr != null) {
                                        try {
                                            priority = priorityAttr.toInt()
                                        } catch (e: NumberFormatException) {
                                            // 保持默认值 0
                                        }
                                    }
                                }
                            }

                            "action" -> {
                                if (inReceiver && inIntentFilter) {
                                    val actionName = parser.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android",
                                        "name"
                                    )
                                    if (actionName != null) {
                                        actions.add(actionName)
                                    }
                                }
                            }
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "receiver" -> {
                                if (inReceiver) {
                                    // 找到目标 Receiver 并解析完成，可以提前退出
                                    parser.close()
                                    if (actions.isNotEmpty()) {
                                        Logger.d(
                                            TAG,
                                            "  └─ Found ${actions.size} actions (priority: $priority) via Manifest XML parsing"
                                        )
                                    }
                                    return Pair(actions, priority)
                                }
                                inReceiver = false
                            }

                            "intent-filter" -> {
                                inIntentFilter = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            parser.close()

        } catch (e: Exception) {
            Logger.w(TAG, "  └─ Failed to parse Manifest XML: ${e.message}")
        }

        return Pair(actions, priority)
    }

    /**
     * 解析类名（处理相对类名，如 ".MyReceiver"）
     */
    private fun resolveClassName(className: String?, fullReceiverName: String): String {
        if (className == null) return ""

        return when {
            className.startsWith(".") -> {
                // 相对类名，需要拼接包名
                val packageName = fullReceiverName.substringBeforeLast(".")
                packageName + className
            }

            className.contains(".") -> {
                // 完整类名
                className
            }

            else -> {
                // 简单类名，拼接包名
                val packageName = fullReceiverName.substringBeforeLast(".")
                "$packageName.$className"
            }
        }
    }

    // ==================== Activity 相关方法 ====================

    /**
     * 检查 Activity 是否在主 APK 中注册
     */
    fun isActivityRegisteredInMain(activityClassName: String): Boolean {
        return activityRegistry.isRegisteredInMain(activityClassName)
    }

    /**
     * 检查 Activity 是否在热更新 APK 中存在
     */
    fun isActivityInHotUpdate(activityClassName: String): Boolean {
        return activityRegistry.isInHotUpdate(activityClassName)
    }

    /**
     * 检查 Activity 是否存在（主 APK 或热更新 APK）
     */
    fun isActivityExists(activityClassName: String): Boolean {
        return activityRegistry.exists(activityClassName)
    }

    /**
     * 获取 Activity 的启动模式
     */
    fun getActivityLaunchMode(activityClassName: String): Int {
        return activityRegistry.get(activityClassName)?.launchMode ?: ActivityInfo.LAUNCH_MULTIPLE
    }

    /**
     * 根据启动模式获取对应的占坑 Activity
     */
    fun getStubActivityForLaunchMode(launchMode: Int): String {
        return STUB_ACTIVITIES[launchMode] ?: STUB_ACTIVITIES[ActivityInfo.LAUNCH_MULTIPLE]!!
    }

    /**
     * 获取真实 Activity 对应的占坑 Activity
     */
    fun getStubActivityForRealActivity(activityClassName: String): String {
        val launchMode = getActivityLaunchMode(activityClassName)
        return getStubActivityForLaunchMode(launchMode)
    }

    // ==================== Service 相关方法 ====================

    /**
     * 检查 Service 是否在主 APK 中注册
     */
    fun isServiceRegisteredInMain(serviceClassName: String): Boolean {
        return serviceRegistry.isRegisteredInMain(serviceClassName)
    }

    /**
     * 检查 Service 是否在热更新 APK 中存在
     */
    fun isServiceInHotUpdate(serviceClassName: String): Boolean {
        return serviceRegistry.isInHotUpdate(serviceClassName)
    }

    /**
     * 检查 Service 是否存在（主 APK 或热更新 APK）
     */
    fun isServiceExists(serviceClassName: String): Boolean {
        return serviceRegistry.exists(serviceClassName)
    }

    /**
     * 处理 startService/bindService Intent
     * 将未注册的 Service 替换为 StubService
     *
     * 工作流程：
     * 1. 检查 Service 是否在主 APK 中注册
     * 2. 如果未注册但在热更新 APK 中存在，保存真实类名并替换为 StubService
     * 3. 如果在主 APK 中已注册，无需替换
     */
    private fun processServiceIntent(context: Context, intent: android.content.Intent) {
        val targetServiceName = intent.component?.className ?: run {
            Logger.d(TAG, "Service component is null, skip processing")
            return
        }

        Logger.d(TAG, "Target service: $targetServiceName")

        if (isServiceRegisteredInMain(targetServiceName)) {
            Logger.d(TAG, "✓ Service registered in main APK, no need to replace")
            return
        }

        Logger.i(TAG, "⚠️ Service not registered in main APK: $targetServiceName")

        if (!isServiceInHotUpdate(targetServiceName)) {
            Logger.e(TAG, "❌ Service not found in hot update APK: $targetServiceName")
            Logger.e(TAG, "❌ Cannot start unregistered Service!")
            return
        }

        Logger.i(TAG, "✓ Service found in hot update APK: $targetServiceName")

        // 保存真实 Service 信息并替换为 StubService
        intent.putExtra(KEY_REAL_SERVICE, targetServiceName)
        intent.component = android.content.ComponentName(
            context.packageName,
            "com.hrm.forge.internal.hook.StubService"
        )

        Logger.i(TAG, "✅ Replaced with StubService")
    }

    /**
     * 处理 startService Intent
     */
    fun processStartServiceIntent(context: Context, intent: android.content.Intent) {
        processServiceIntent(context, intent)
    }

    /**
     * 处理 bindService Intent
     */
    fun processBindServiceIntent(context: Context, intent: android.content.Intent) {
        processServiceIntent(context, intent)
    }

    // ==================== BroadcastReceiver 相关方法 ====================

    /**
     * 检查 BroadcastReceiver 是否在主 APK 中注册
     */
    fun isReceiverRegisteredInMain(receiverClassName: String): Boolean {
        return receiverRegistry.isRegisteredInMain(receiverClassName)
    }

    /**
     * 检查 BroadcastReceiver 是否在热更新 APK 中存在
     */
    fun isReceiverInHotUpdate(receiverClassName: String): Boolean {
        return receiverRegistry.isInHotUpdate(receiverClassName)
    }

    /**
     * 检查 BroadcastReceiver 是否存在（主 APK 或热更新 APK）
     */
    fun isReceiverExists(receiverClassName: String): Boolean {
        return receiverRegistry.exists(receiverClassName)
    }

    /**
     * 获取所有需要通过 StubReceiver 接收的 Receiver 列表
     * （只返回热更新 APK 中新增的静态 Receiver）
     */
    fun getHotUpdateReceivers(): Set<String> {
        return receiverRegistry.getHotUpdateComponents().keys
    }

    /**
     * 处理 broadcastIntent Intent（拦截显式和隐式广播）
     */
    fun processBroadcastIntent(context: Context, intent: Intent) {
        val targetReceiverName = intent.component?.className

        if (targetReceiverName == null) {
            // 隐式广播，尝试匹配热更新 APK 中的 Receiver
            processImplicitBroadcast(context, intent)
            return
        }

        // 显式广播
        processExplicitBroadcast(context, intent, targetReceiverName)
    }

    /**
     * 处理显式广播
     */
    private fun processExplicitBroadcast(
        context: Context,
        intent: Intent,
        targetReceiverName: String
    ) {
        Logger.d(TAG, "Target receiver: $targetReceiverName")

        if (isReceiverRegisteredInMain(targetReceiverName)) {
            Logger.d(TAG, "✓ Receiver registered in main APK, no need to replace")
            return
        }

        Logger.i(TAG, "⚠️ Receiver not registered in main APK: $targetReceiverName")

        if (!isReceiverInHotUpdate(targetReceiverName)) {
            Logger.e(TAG, "❌ Receiver not found in hot update APK: $targetReceiverName")
            Logger.e(TAG, "❌ Cannot send broadcast to unregistered Receiver!")
            return
        }

        Logger.i(TAG, "✓ Receiver found in hot update APK: $targetReceiverName")

        // 保存真实 Receiver 信息并替换为 StubReceiver
        intent.putExtra(KEY_REAL_RECEIVER, targetReceiverName)
        intent.component = android.content.ComponentName(
            context.packageName,
            "com.hrm.forge.internal.hook.StubReceiver"
        )

        Logger.i(TAG, "✅ Replaced with StubReceiver")
    }

    /**
     * 处理隐式广播：匹配热更新 APK 中的 Receiver 并手动分发
     *
     * 工作流程：
     * 1. 提取广播的 Action
     * 2. 查找匹配的热更新 Receiver 配置
     * 3. 按优先级排序
     * 4. 手动创建 Receiver 实例并调用 onReceive()
     * 5. 跳过已在主 APK 中注册的 Receiver（避免重复接收）
     */
    private fun processImplicitBroadcast(context: Context, intent: Intent) {
        val action = intent.action

        if (action == null) {
            Logger.d(TAG, "Broadcast has no action, skip implicit processing")
            return
        }

        Logger.d(TAG, "Processing implicit broadcast, action: $action")

        // 查找匹配的 Receiver 配置
        val matchingConfigs = receiverConfigMap[action]

        if (matchingConfigs == null || matchingConfigs.isEmpty()) {
            Logger.d(TAG, "No matching hot update receivers for action: $action")
            return
        }

        Logger.i(
            TAG,
            "📡 Found ${matchingConfigs.size} matching hot update receivers for action: $action"
        )

        // 按优先级排序（从高到低）
        val sortedConfigs = matchingConfigs.sortedByDescending { it.priority }

        // 手动分发给每个匹配的 Receiver
        sortedConfigs.forEach { config ->
            dispatchToReceiver(context, intent, config)
        }
    }

    /**
     * 手动分发广播到指定 Receiver
     *
     * 实现细节：
     * 1. 检查 Receiver 是否在主 APK 中已注册（避免重复分发）
     * 2. 从缓存获取或创建新的 Receiver 实例
     * 3. 调用 onReceive() 方法
     * 4. 处理异常情况
     */
    private fun dispatchToReceiver(
        context: Context,
        intent: Intent,
        config: ReceiverConfig
    ) {
        try {
            Logger.d(
                TAG,
                "├─ Dispatching to: ${config.receiverClass} (priority: ${config.priority})"
            )

            // 检查是否在主 APK 中已注册（避免重复分发）
            if (isReceiverRegisteredInMain(config.receiverClass)) {
                Logger.d(TAG, "│  └─ Skip: Already registered in main APK")
                return
            }

            // 从缓存获取或创建新实例
            val receiver = receiverInstanceCache.getOrPut(config.receiverClass) {
                Logger.d(TAG, "│  ├─ Creating new receiver instance")
                val receiverClass = Class.forName(config.receiverClass)
                receiverClass.newInstance() as android.content.BroadcastReceiver
            }

            // 创建干净的 Intent 副本（移除 Forge 内部使用的 extra）
            val cleanIntent = Intent(intent)
            cleanIntent.removeExtra(KEY_REAL_RECEIVER)

            // 调用 onReceive
            receiver.onReceive(context, cleanIntent)

            Logger.i(TAG, "│  └─ ✅ Successfully dispatched")

        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, "│  └─ ❌ Receiver class not found: ${config.receiverClass}", e)
        } catch (e: InstantiationException) {
            Logger.e(TAG, "│  └─ ❌ Cannot instantiate receiver: ${config.receiverClass}", e)
        } catch (e: Exception) {
            Logger.e(TAG, "│  └─ ❌ Failed to dispatch: ${e.message}", e)
        }
    }

    // ==================== ContentProvider 相关方法 ====================

    /**
     * 检查 ContentProvider 是否在主 APK 中注册
     */
    fun isProviderRegisteredInMain(authority: String): Boolean {
        return providerRegistry.isRegisteredInMain(authority)
    }

    /**
     * 检查 ContentProvider 是否在热更新 APK 中存在
     */
    fun isProviderInHotUpdate(authority: String): Boolean {
        return providerRegistry.isInHotUpdate(authority)
    }

    /**
     * 检查 ContentProvider 是否存在（主 APK 或热更新 APK）
     */
    fun isProviderExists(authority: String): Boolean {
        return providerRegistry.exists(authority)
    }

    /**
     * 获取 ContentProvider 信息
     */
    fun getProviderInfo(authority: String): ProviderInfo? {
        return providerRegistry.get(authority)
    }

    /**
     * 获取所有已知的 Authority（包括主 APK 和热更新 APK）
     */
    fun getAllAuthorities(): List<String> {
        return providerRegistry.getAllKeys()
    }

    /**
     * 获取热更新 APK 中的所有 ContentProvider（仅热更，不包括主 APK）
     *
     * @return Map<Authority, ProviderInfo>
     */
    fun getHotUpdateProviders(): Map<String, ProviderInfo> {
        return providerRegistry.getHotUpdateComponents()
    }

    // ==================== 管理方法 ====================

    /**
     * 清除所有数据
     */
    fun clear() {
        activityRegistry.clear()
        serviceRegistry.clear()
        receiverRegistry.clear()
        providerRegistry.clear()
        receiverConfigMap.clear()
        receiverInstanceCache.clear()
        isInitialized = false
        Logger.i(TAG, "ComponentManager cleared")
    }

    /**
     * 获取统计信息
     */
    fun getStats(): String {
        return buildString {
            appendLine("ComponentManager Stats:")
            appendLine("  Main Activities: ${activityRegistry.mainSize()}")
            appendLine("  Hot update Activities: ${activityRegistry.hotUpdateSize()}")
            appendLine("  Main Services: ${serviceRegistry.mainSize()}")
            appendLine("  Hot update Services: ${serviceRegistry.hotUpdateSize()}")
            appendLine("  Main Receivers: ${receiverRegistry.mainSize()}")
            appendLine("  Hot update Receivers: ${receiverRegistry.hotUpdateSize()}")
            appendLine("  Main Providers: ${providerRegistry.mainSize()}")
            appendLine("  Hot update Providers: ${providerRegistry.hotUpdateSize()}")
            appendLine("  Receiver IntentFilter Actions: ${receiverConfigMap.size}")
            appendLine("  Receiver Instance Cache: ${receiverInstanceCache.size}")
            appendLine("  Initialized: $isInitialized")
        }
    }
}
