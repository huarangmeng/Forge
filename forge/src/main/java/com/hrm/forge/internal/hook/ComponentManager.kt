package com.hrm.forge.internal.hook

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
    
    // 占坑 Activity 映射：launchMode -> 占坑 Activity 类名
    private val STUB_ACTIVITIES = mapOf(
        ActivityInfo.LAUNCH_MULTIPLE to "com.hrm.forge.loader.instrumentation.StubActivityStandard",
        ActivityInfo.LAUNCH_SINGLE_TOP to "com.hrm.forge.loader.instrumentation.StubActivitySingleTop",
        ActivityInfo.LAUNCH_SINGLE_TASK to "com.hrm.forge.loader.instrumentation.StubActivitySingleTask",
        ActivityInfo.LAUNCH_SINGLE_INSTANCE to "com.hrm.forge.loader.instrumentation.StubActivitySingleInstance"
    )
    
    // 主 APK 中已注册的 Activity：className -> launchMode
    private val mainActivities = mutableMapOf<String, Int>()
    
    // 热更新 APK 中的 Activity：className -> launchMode
    private val hotUpdateActivities = mutableMapOf<String, Int>()
    
    // 主 APK 中已注册的 Service
    private val mainServices = mutableSetOf<String>()
    
    // 热更新 APK 中的 Service
    private val hotUpdateServices = mutableSetOf<String>()
    
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
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 1. 一次性解析主 APK 的所有组件（Activity + Service）
            parseMainComponents(context)
            
            // 2. 一次性解析热更新 APK 的所有组件（Activity + Service）
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
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize ComponentInfoManager", e)
        }
    }
    
    /**
     * 解析主 APK 的所有组件
     * 一次性解析 Activity 和 Service，避免重复读取 PackageInfo
     */
    private fun parseMainComponents(context: Context) {
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
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
            
            Logger.i(TAG, "Parsed main APK: ${mainActivities.size} activities, ${mainServices.size} services")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse main components", e)
        }
    }
    
    /**
     * 解析热更新 APK 的所有组件
     * 一次性解析 Activity 和 Service，避免重复读取 APK
     */
    private fun parseHotUpdateComponents(context: Context, apkPath: String) {
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
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
            
            Logger.i(TAG, "Parsed hot update APK: ${hotUpdateActivities.size} activities, ${hotUpdateServices.size} services")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse hot update components", e)
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
    
    // ==================== 管理方法 ====================
    
    /**
     * 清除所有数据
     */
    fun clear() {
        mainActivities.clear()
        hotUpdateActivities.clear()
        mainServices.clear()
        hotUpdateServices.clear()
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
            appendLine("  Initialized: $isInitialized")
        }
    }
}
