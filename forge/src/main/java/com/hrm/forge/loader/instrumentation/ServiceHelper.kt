package com.hrm.forge.loader.instrumentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hrm.forge.logger.Logger

/**
 * Service Hook 辅助类
 * 
 * 负责处理 Service Intent，将未注册的 Service 替换为 StubService
 * 
 * 工作原理：
 * 1. 检查 Service 是否在主 APK 的 AndroidManifest 中注册
 * 2. 如果未注册，保存真实 Service 类名到 Intent
 * 3. 将 Intent 的 Component 替换为 StubService
 * 4. StubService 会在运行时创建真实 Service 并转发所有调用
 * 
 * 注意：
 * 1. 此类不能被混淆
 * 2. 必须在 AMS Hook 中调用
 */
object ServiceHelper {
    
    private const val TAG = "ServiceHelper"
    
    /**
     * Intent Extra Key：真实 Service 类名
     */
    const val KEY_REAL_SERVICE = "intent_real_service_name"
    
    /**
     * StubService 类名
     */
    private const val STUB_SERVICE_CLASS = "com.hrm.forge.loader.instrumentation.StubService"
    
    /**
     * 主 APK 中注册的 Service 列表
     */
    private val mainServices = mutableSetOf<String>()
    
    /**
     * 是否已初始化
     */
    private var isInitialized = false
    
    /**
     * 初始化：加载主 APK 中注册的 Service 列表
     */
    private fun init(context: Context) {
        if (isInitialized) {
            return
        }
        
        try {
            Logger.i(TAG, "Initializing ServiceHelper...")
            
            // 获取主 APK 的 PackageInfo
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SERVICES
            )
            
            // 解析所有注册的 Service
            packageInfo.services?.forEach { serviceInfo ->
                val className = serviceInfo.name
                mainServices.add(className)
                Logger.d(TAG, "Found registered service: $className")
            }
            
            isInitialized = true
            Logger.i(TAG, "✅ ServiceHelper initialized, found ${mainServices.size} services")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize ServiceHelper", e)
        }
    }
    
    /**
     * 检查 Service 是否在主 APK 中注册
     */
    private fun isServiceRegisteredInMain(className: String): Boolean {
        return mainServices.contains(className)
    }
    
    /**
     * 处理 startService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processStartServiceIntent(context: Context, intent: Intent) {
        init(context)
        processServiceIntent(context, intent)
    }
    
    /**
     * 处理 bindService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processBindServiceIntent(context: Context, intent: Intent) {
        init(context)
        processServiceIntent(context, intent)
    }
    
    /**
     * 处理 Service Intent
     */
    private fun processServiceIntent(context: Context, intent: Intent) {
        try {
            // 获取目标 Service 类名
            val targetClassName = intent.component?.className
            
            if (targetClassName.isNullOrEmpty()) {
                Logger.d(TAG, "Intent component is null or empty, skip")
                return
            }
            
            // 如果已经是 StubService，不需要处理（避免重复替换）
            if (targetClassName == STUB_SERVICE_CLASS) {
                Logger.d(TAG, "Already StubService, skip")
                return
            }
            
            // 检查 Service 是否在主 APK 中注册
            if (isServiceRegisteredInMain(targetClassName)) {
                Logger.d(TAG, "Service is registered in main APK: $targetClassName")
                return
            }
            
            // 未注册的 Service，需要替换为 StubService
            Logger.i(TAG, "Service NOT registered, replacing with StubService: $targetClassName")
            
            // 保存真实 Service 类名
            intent.putExtra(KEY_REAL_SERVICE, targetClassName)
            
            // 替换为 StubService
            intent.component = ComponentName(context.packageName, STUB_SERVICE_CLASS)
            
            Logger.d(TAG, "✅ Intent replaced with StubService")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to process service intent", e)
        }
    }
    
    /**
     * 从 Intent 获取真实 Service 类名
     */
    fun getRealServiceClass(intent: Intent?): String? {
        return intent?.getStringExtra(KEY_REAL_SERVICE)
    }
}
