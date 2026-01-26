package com.hrm.forge.loader.instrumentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.hrm.forge.logger.Logger

/**
 * Service Hook 辅助类
 * 
 * 负责处理 Service Intent，将未注册的 Service 替换为 StubService
 * 委托给 ComponentInfoManager 进行组件信息查询
 * 
 * 工作原理：
 * 1. 检查 Service 是否在主 APK 的 AndroidManifest 中注册
 * 2. 如果未注册，检查是否在热更新 APK 中存在
 * 3. 如果存在，保存真实 Service 类名到 Intent
 * 4. 将 Intent 的 Component 替换为 StubService
 * 5. StubService 会在运行时创建真实 Service 并转发所有调用
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
     * 处理 startService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processStartServiceIntent(context: Context, intent: Intent) {
        processServiceIntent(context, intent)
    }
    
    /**
     * 处理 bindService Intent
     * 将未注册的 Service 替换为 StubService
     */
    fun processBindServiceIntent(context: Context, intent: Intent) {
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
            if (ComponentInfoManager.isServiceRegisteredInMain(targetClassName)) {
                Logger.d(TAG, "✅ Service registered in main APK: $targetClassName")
                return
            }
            
            // 检查 Service 是否在热更新 APK 中存在
            if (!ComponentInfoManager.isServiceInHotUpdate(targetClassName)) {
                Logger.e(TAG, "❌ Service not found: $targetClassName")
                Logger.e(TAG, "   - Not in main APK")
                Logger.e(TAG, "   - Not in hot update APK")
                throw ClassNotFoundException("Service not found in main APK or hot update APK: $targetClassName")
            }
            
            // 未注册但在热更新中存在的 Service，需要替换为 StubService
            Logger.i(TAG, "⚠️ Service NOT registered in main APK: $targetClassName")
            Logger.i(TAG, "✅ Service found in hot update APK")
            Logger.i(TAG, "🔄 Replacing with StubService")
            
            // 保存真实 Service 类名
            intent.putExtra(KEY_REAL_SERVICE, targetClassName)
            
            // 替换为 StubService
            intent.component = ComponentName(context.packageName, STUB_SERVICE_CLASS)
            
            Logger.i(TAG, "✅ Intent replaced with StubService")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to process service intent", e)
            throw e
        }
    }
    
    /**
     * 从 Intent 获取真实 Service 类名
     */
    fun getRealServiceClass(intent: Intent?): String? {
        return intent?.getStringExtra(KEY_REAL_SERVICE)
    }
}