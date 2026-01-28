package com.hrm.forge.internal.hook

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.hrm.forge.internal.log.Logger
import java.io.File

/**
 * 组件信息管理器（内部实现）
 * 
 * 统一管理 Activity 和 Service 的解析，避免重复解析 APK
 * 
 * 负责：
 * 1. 一次性解析主 APK 和热更新 APK 的所有组件信息
 * 2. 提供 Activity 和 Service 的查询接口
 * 3. 根据启动模式选择合适的占坑 Activity
 * 4. 提高性能，避免重复解析 APK 文件
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
    
    // 主 APK 中已注册的 Activity：className -> launchMode
    private val mainActivities = mutableMapOf<String, Int>()
    
    // 热更新 APK 中的 Activity：className -> launchMode
    private val hotUpdateActivities = mutableMapOf<String, Int>()
    
    // 主 APK 中已注册的 Service
    private val mainServices = mutableSetOf<String>()
    
    // 热更新 APK 中的 Service
    private val hotUpdateServices = mutableSetOf<String>()
    
    // 主 APK 中已注册的 BroadcastReceiver
    private val mainReceivers = mutableSetOf<String>()
    
    // 热更新 APK 中的 BroadcastReceiver
    private val hotUpdateReceivers = mutableSetOf<String>()
    
    // 主 APK 中已注册的 ContentProvider：authority -> ProviderInfo
    private val mainProviders = mutableMapOf<String, ProviderInfo>()
    
    // 热更新 APK 中的 ContentProvider：authority -> ProviderInfo
    private val hotUpdateProviders = mutableMapOf<String, ProviderInfo>()
    
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
    
    // 热更新 APK 中 Receiver 的 IntentFilter 配置：Action -> List<ReceiverConfig>
    private val receiverConfigMap = mutableMapOf<String, MutableList<ReceiverConfig>>()
    
    // Receiver 实例缓存，避免重复创建
    private val receiverInstanceCache = mutableMapOf<String, android.content.BroadcastReceiver>()
    
    // 热更新 APK 路径（供 ContentProviderHook 使用）
    @Volatile
    var hotUpdateApkPath: String? = null
        private set
    
    // 是否已初始化
    private var isInitialized = false
    
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
        
        // 保存热更新 APK 路径
        this.hotUpdateApkPath = hotUpdateApkPath
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 1. 一次性解析主 APK 的所有组件（Activity + Service + Receiver）
            parseMainComponents(context)
            
            // 2. 一次性解析热更新 APK 的所有组件（Activity + Service + Receiver）
            if (hotUpdateApkPath != null && File(hotUpdateApkPath).exists()) {
                parseHotUpdateComponents(context, hotUpdateApkPath)
            } else {
                Logger.w(TAG, "Hot update APK not found, skip parsing: $hotUpdateApkPath")
            }
            
            val elapsedTime = System.currentTimeMillis() - startTime
            
            isInitialized = true
            Logger.i(TAG, "✅ ComponentManager initialized in ${elapsedTime}ms")
            Logger.i(TAG, "Components summary:")
            Logger.i(TAG, "  - Main Activities: ${mainActivities.size}")
            Logger.i(TAG, "  - Hot update Activities: ${hotUpdateActivities.size}")
            Logger.i(TAG, "  - Main Services: ${mainServices.size}")
            Logger.i(TAG, "  - Hot update Services: ${hotUpdateServices.size}")
            Logger.i(TAG, "  - Main Receivers: ${mainReceivers.size}")
            Logger.i(TAG, "  - Hot update Receivers: ${hotUpdateReceivers.size}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize ComponentInfoManager", e)
        }
    }
    
    /**
     * 解析主 APK 的所有组件
     * 一次性解析 Activity、Service 和 BroadcastReceiver，避免重复读取 PackageInfo
     */
    private fun parseMainComponents(context: Context) {
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val packageInfo = pm.getPackageInfo(context.packageName, flags)
            
            // 解析 Activity
            packageInfo.activities?.forEach { activityInfo ->
                mainActivities[activityInfo.name] = activityInfo.launchMode
                Logger.d(TAG, "Main activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}")
            }
            
            // 解析 Service
            packageInfo.services?.forEach { serviceInfo ->
                mainServices.add(serviceInfo.name)
                Logger.d(TAG, "Main service: ${serviceInfo.name}")
            }
            
            // 解析 BroadcastReceiver
            packageInfo.receivers?.forEach { receiverInfo ->
                mainReceivers.add(receiverInfo.name)
                Logger.d(TAG, "Main receiver: ${receiverInfo.name}")
            }

            // 解析 ContentProvider
            packageInfo.providers?.forEach { providerInfo ->
                val info = ProviderInfo(providerInfo.name, providerInfo.authority, providerInfo.exported)
                mainProviders[providerInfo.authority] = info
                Logger.d(TAG, "Main provider: ${providerInfo.name}, authority: ${providerInfo.authority}")
            }
            
            Logger.i(TAG, "Parsed main APK: ${mainActivities.size} activities, ${mainServices.size} services, ${mainReceivers.size} receivers, ${mainProviders.size} providers")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse main components", e)
        }
    }
    
    /**
     * 解析热更新 APK 的所有组件
     * 一次性解析 Activity、Service 和 BroadcastReceiver，避免重复读取 APK
     */
    private fun parseHotUpdateComponents(context: Context, apkPath: String) {
        try {
            val pm = context.packageManager
            
            // 使用 GET_RECEIVERS 会自动包含 IntentFilter 信息
            val flags = PackageManager.GET_ACTIVITIES or 
                       PackageManager.GET_SERVICES or 
                       PackageManager.GET_RECEIVERS or
                       PackageManager.GET_PROVIDERS
            
            val packageInfo = pm.getPackageArchiveInfo(apkPath, flags)
            
            if (packageInfo == null) {
                Logger.e(TAG, "Failed to parse hot update APK: $apkPath")
                return
            }
            
            // 解析 Activity（只记录不在主 APK 中的）
            packageInfo.activities?.forEach { activityInfo ->
                if (!mainActivities.containsKey(activityInfo.name)) {
                    hotUpdateActivities[activityInfo.name] = activityInfo.launchMode
                    Logger.d(TAG, "Hot update activity: ${activityInfo.name}, launchMode: ${activityInfo.launchMode}")
                }
            }
            
            // 解析 Service（只记录不在主 APK 中的）
            packageInfo.services?.forEach { serviceInfo ->
                if (!mainServices.contains(serviceInfo.name)) {
                    hotUpdateServices.add(serviceInfo.name)
                    Logger.d(TAG, "Hot update service: ${serviceInfo.name}")
                }
            }
            
            // 解析 BroadcastReceiver（只记录不在主 APK 中的）
            packageInfo.receivers?.forEach { receiverInfo ->
                if (!mainReceivers.contains(receiverInfo.name)) {
                    hotUpdateReceivers.add(receiverInfo.name)
                    Logger.d(TAG, "Hot update receiver: ${receiverInfo.name}")
                }
            }

            // 解析 ContentProvider
            packageInfo.providers?.forEach { providerInfo ->
                if (!mainProviders.containsKey(providerInfo.authority)) {
                    val info = ProviderInfo(providerInfo.name, providerInfo.authority, providerInfo.exported)
                    hotUpdateProviders[providerInfo.authority] = info
                    Logger.d(TAG, "Hot update provider: ${providerInfo.name}, authority: ${providerInfo.authority}")
                }
            }
            
            // 解析 IntentFilter 配置（需要使用 PackageParser）
            if (hotUpdateReceivers.isNotEmpty()) {
                parseReceiverIntentFilters(context, apkPath)
            }
            
            Logger.i(TAG, "Parsed hot update APK: ${hotUpdateActivities.size} activities, ${hotUpdateServices.size} services, ${hotUpdateReceivers.size} receivers, ${hotUpdateProviders.size} providers")
            Logger.i(TAG, "Parsed ${receiverConfigMap.size} receiver intent-filter actions")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse hot update components", e)
        }
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
                if (!hotUpdateReceivers.contains(receiverName)) {
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
    private fun getIntentFilterFromManifest(apkPath: String, receiverName: String): Pair<List<String>, Int> {
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
                                        Logger.d(TAG, "  └─ Found ${actions.size} actions (priority: $priority) via Manifest XML parsing")
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
        return mainActivities.containsKey(activityClassName)
    }
    
    /**
     * 检查 Activity 是否在热更新 APK 中存在
     */
    fun isActivityInHotUpdate(activityClassName: String): Boolean {
        return hotUpdateActivities.containsKey(activityClassName)
    }
    
    /**
     * 检查 Activity 是否存在（主 APK 或热更新 APK）
     */
    fun isActivityExists(activityClassName: String): Boolean {
        return isActivityRegisteredInMain(activityClassName) || isActivityInHotUpdate(activityClassName)
    }
    
    /**
     * 获取 Activity 的启动模式
     */
    fun getActivityLaunchMode(activityClassName: String): Int {
        // 优先从热更新 APK 中查找
        hotUpdateActivities[activityClassName]?.let { return it }
        
        // 如果热更新中没有，从主 APK 中查找
        mainActivities[activityClassName]?.let { return it }
        
        // 默认返回 standard 模式
        return ActivityInfo.LAUNCH_MULTIPLE
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
        return mainServices.contains(serviceClassName)
    }
    
    /**
     * 检查 Service 是否在热更新 APK 中存在
     */
    fun isServiceInHotUpdate(serviceClassName: String): Boolean {
        return hotUpdateServices.contains(serviceClassName)
    }
    
    /**
     * 检查 Service 是否存在（主 APK 或热更新 APK）
     */
    fun isServiceExists(serviceClassName: String): Boolean {
        return isServiceRegisteredInMain(serviceClassName) || isServiceInHotUpdate(serviceClassName)
    }
    
    /**
     * 处理 startService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processStartServiceIntent(context: Context, intent: android.content.Intent) {
        val targetServiceName = intent.component?.className
        
        if (targetServiceName == null) {
            Logger.d(TAG, "Service component is null, skip processing")
            return
        }
        
        Logger.d(TAG, "Target service: $targetServiceName")
        
        // 检查 Service 是否在主 APK 中注册
        val isRegisteredInMain = isServiceRegisteredInMain(targetServiceName)
        
        if (!isRegisteredInMain) {
            Logger.i(TAG, "⚠️ Service not registered in main APK: $targetServiceName")
            
            // 检查是否在热更新 APK 中存在
            val existsInHotUpdate = isServiceInHotUpdate(targetServiceName)
            
            if (!existsInHotUpdate) {
                Logger.e(TAG, "❌ Service not found in hot update APK: $targetServiceName")
                Logger.e(TAG, "❌ Cannot start unregistered Service!")
                return
            }
            
            Logger.i(TAG, "✓ Service found in hot update APK: $targetServiceName")
            
            // 保存真实 Service 信息到 Intent
            intent.putExtra(KEY_REAL_SERVICE, targetServiceName)
            
            // 替换为 StubService
            val stubServiceComponent = android.content.ComponentName(
                context.packageName,
                "com.hrm.forge.internal.hook.StubService"
            )
            intent.component = stubServiceComponent
            
            Logger.i(TAG, "✅ Replaced with StubService")
        } else {
            Logger.d(TAG, "✓ Service registered in main APK, no need to replace")
        }
    }
    
    /**
     * 处理 bindService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processBindServiceIntent(context: Context, intent: android.content.Intent) {
        // bindService 的处理逻辑与 startService 相同
        processStartServiceIntent(context, intent)
    }
    
    // ==================== BroadcastReceiver 相关方法 ====================
    
    /**
     * 检查 BroadcastReceiver 是否在主 APK 中注册
     */
    fun isReceiverRegisteredInMain(receiverClassName: String): Boolean {
        return mainReceivers.contains(receiverClassName)
    }
    
    /**
     * 检查 BroadcastReceiver 是否在热更新 APK 中存在
     */
    fun isReceiverInHotUpdate(receiverClassName: String): Boolean {
        return hotUpdateReceivers.contains(receiverClassName)
    }
    
    /**
     * 检查 BroadcastReceiver 是否存在（主 APK 或热更新 APK）
     */
    fun isReceiverExists(receiverClassName: String): Boolean {
        return isReceiverRegisteredInMain(receiverClassName) || isReceiverInHotUpdate(receiverClassName)
    }
    
    /**
     * 获取所有需要通过 StubReceiver 接收的 Receiver 列表
     * （只返回热更新 APK 中新增的静态 Receiver）
     */
    fun getHotUpdateReceivers(): Set<String> {
        return hotUpdateReceivers.toSet()
    }
    
    /**
     * 处理 broadcastIntent Intent（拦截显式广播）
     * 将未注册的 Receiver 替换为 StubReceiver
     * 
     * 工作原理：与 Service 完全一致
     * 1. 检查 Intent 的 component 是否指向具体的 Receiver
     * 2. 检查该 Receiver 是否在主 APK 中注册
     * 3. 如果未注册但在热更新 APK 中存在，保存真实类名并替换为 StubReceiver
     */
    fun processBroadcastIntent(context: Context, intent: Intent) {
        val targetReceiverName = intent.component?.className
        
        if (targetReceiverName == null) {
            // 隐式广播，尝试匹配热更新 APK 中的 Receiver
            processImplicitBroadcast(context, intent)
            return
        }
        
        Logger.d(TAG, "Target receiver: $targetReceiverName")
        
        // 检查 Receiver 是否在主 APK 中注册
        val isRegisteredInMain = isReceiverRegisteredInMain(targetReceiverName)
        
        if (!isRegisteredInMain) {
            Logger.i(TAG, "⚠️ Receiver not registered in main APK: $targetReceiverName")
            
            // 检查是否在热更新 APK 中存在
            val existsInHotUpdate = isReceiverInHotUpdate(targetReceiverName)
            
            if (!existsInHotUpdate) {
                Logger.e(TAG, "❌ Receiver not found in hot update APK: $targetReceiverName")
                Logger.e(TAG, "❌ Cannot send broadcast to unregistered Receiver!")
                return
            }
            
            Logger.i(TAG, "✓ Receiver found in hot update APK: $targetReceiverName")
            
            // 保存真实 Receiver 信息到 Intent
            intent.putExtra(KEY_REAL_RECEIVER, targetReceiverName)
            
            // 替换为 StubReceiver
            val stubReceiverComponent = android.content.ComponentName(
                context.packageName,
                "com.hrm.forge.internal.hook.StubReceiver"
            )
            intent.component = stubReceiverComponent
            
            Logger.i(TAG, "✅ Replaced with StubReceiver")
        } else {
            Logger.d(TAG, "✓ Receiver registered in main APK, no need to replace")
        }
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
        
        Logger.i(TAG, "📡 Found ${matchingConfigs.size} matching hot update receivers for action: $action")
        
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
            Logger.d(TAG, "├─ Dispatching to: ${config.receiverClass} (priority: ${config.priority})")
            
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
        return mainProviders.containsKey(authority)
    }
    
    /**
     * 检查 ContentProvider 是否在热更新 APK 中存在
     */
    fun isProviderInHotUpdate(authority: String): Boolean {
        return hotUpdateProviders.containsKey(authority)
    }
    
    /**
     * 检查 ContentProvider 是否存在（主 APK 或热更新 APK）
     */
    fun isProviderExists(authority: String): Boolean {
        return isProviderRegisteredInMain(authority) || isProviderInHotUpdate(authority)
    }

    /**
     * 获取 ContentProvider 信息
     */
    fun getProviderInfo(authority: String): ProviderInfo? {
        return hotUpdateProviders[authority] ?: mainProviders[authority]
    }
    
    /**
     * 获取所有已知的 Authority（包括主 APK 和热更新 APK）
     */
    fun getAllAuthorities(): List<String> {
        val all = mutableListOf<String>()
        all.addAll(mainProviders.keys)
        all.addAll(hotUpdateProviders.keys)
        return all
    }
    
    /**
     * 获取热更新 APK 中的所有 ContentProvider（仅热更，不包括主 APK）
     * 
     * @return Map<Authority, ProviderInfo>
     */
    fun getHotUpdateProviders(): Map<String, ProviderInfo> {
        return hotUpdateProviders.toMap()
    }
    
    // ==================== 管理方法 ====================
    
    /**
     * 清除所有数据
     */
    fun clear() {
        mainActivities.clear()
        hotUpdateActivities.clear()
        mainServices.clear()
        hotUpdateServices.clear()
        mainReceivers.clear()
        hotUpdateReceivers.clear()
        mainProviders.clear()
        hotUpdateProviders.clear()
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
            appendLine("  Main Activities: ${mainActivities.size}")
            appendLine("  Hot update Activities: ${hotUpdateActivities.size}")
            appendLine("  Main Services: ${mainServices.size}")
            appendLine("  Hot update Services: ${hotUpdateServices.size}")
            appendLine("  Main Receivers: ${mainReceivers.size}")
            appendLine("  Hot update Receivers: ${hotUpdateReceivers.size}")
            appendLine("  Main Providers: ${mainProviders.size}")
            appendLine("  Hot update Providers: ${hotUpdateProviders.size}")
            appendLine("  Receiver IntentFilter Actions: ${receiverConfigMap.size}")
            appendLine("  Receiver Instance Cache: ${receiverInstanceCache.size}")
            appendLine("  Initialized: $isInitialized")
        }
    }
}
