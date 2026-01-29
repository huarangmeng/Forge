package com.hrm.forge.internal.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hrm.forge.internal.hook.base.BaseHook
import com.hrm.forge.internal.util.ReflectUtil
import com.hrm.forge.internal.log.Logger

/**
 * ContentProvider Hook（重构版本）
 * 
 * 使用 BaseHook 统一生命周期管理
 * 使用 ReflectUtil 简化反射代码
 * 
 * **核心策略：调用系统 ActivityThread.installContentProviders 安装热更 Provider**
 * 
 * 工作原理：
 * 1. 使用 ComponentManager 获取热更 Provider 信息
 * 2. 转换为系统的 ProviderInfo 格式
 * 3. 调用 ActivityThread.installContentProviders() 让系统自动安装
 * 4. 系统会自动创建 Provider 实例、添加到 mProviderMap、调用 onCreate 等
 * 
 * 优势：
 * - ✅ 调用系统方法，兼容性最强
 * - ✅ 无需手动操作 mProviderMap、ProviderKey 等内部结构
 * - ✅ 系统自动管理 Provider 生命周期
 * - ✅ 调用方使用正常 URI，无需修改
 */
internal object ContentProviderHook : BaseHook() {
    
    override val tag = "ContentProviderHook"
    
    private const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
    
    private lateinit var appContext: Context
    
    /**
     * 初始化 Hook（公开方法，保持兼容性）
     */
    fun hook(context: Context) {
        appContext = context
        hook()
    }
    
    override fun doHook() {
        // Hook IContentService
        ContentResolverHook.hookIContentService(appContext.contentResolver)
        
        // 安装热更 Provider（需要在主线程执行）
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                installHotUpdateProviders()
            }
        } else {
            installHotUpdateProviders()
        }
    }
    
    /**
     * 安装热更 ContentProvider
     */
    private fun installHotUpdateProviders() {
        // 1. 从 ComponentManager 获取热更 Provider 信息
        val hotUpdateProviders = ComponentManager.getHotUpdateProviders()
        
        if (hotUpdateProviders.isEmpty()) {
            Logger.i(tag, "No hot update providers found, skip installation")
            return
        }
        
        Logger.i(tag, "Found ${hotUpdateProviders.size} hot update providers:")
        hotUpdateProviders.forEach { (authority, providerInfo) ->
            Logger.i(tag, "  - $authority -> ${providerInfo.className}")
        }
        
        // 2. 转换为系统的 ProviderInfo 格式
        val androidProviderInfoList = hotUpdateProviders.map { (authority, providerInfo) ->
            createAndroidProviderInfo(authority, providerInfo)
        }
        
        // 3. 调用系统方法安装 ContentProvider
        installContentProviders(androidProviderInfoList)
    }
    
    /**
     * 将 ComponentManager.ProviderInfo 转换为 android.content.pm.ProviderInfo
     */
    private fun createAndroidProviderInfo(
        authority: String,
        providerInfo: ComponentManager.ProviderInfo
    ): android.content.pm.ProviderInfo {
        return android.content.pm.ProviderInfo().apply {
            this.authority = authority
            this.name = providerInfo.className
            this.packageName = appContext.packageName
            this.applicationInfo = appContext.applicationInfo
            this.exported = providerInfo.exported
            this.enabled = true
            this.processName = appContext.applicationInfo.processName
            this.multiprocess = false
            this.initOrder = 0
        }
    }
    
    /**
     * 调用系统方法安装 ContentProvider
     */
    private fun installContentProviders(providerInfoList: List<android.content.pm.ProviderInfo>) {
        Logger.d(tag, "Installing ${providerInfoList.size} ContentProviders...")
        
        // 1. 获取 ActivityThread 实例
        val activityThreadClass = ReflectUtil.getClass(ACTIVITY_THREAD_CLASS)
            ?: throw IllegalStateException("Failed to get ActivityThread class")
        
        val activityThread = ReflectUtil.getStaticFieldValue<Any>(
            activityThreadClass,
            "sCurrentActivityThread"
        ) ?: throw IllegalStateException("ActivityThread instance is null")
        
        Logger.d(tag, "Got ActivityThread instance")
        
        // 2. 调用 installContentProviders() 方法
        val method = ReflectUtil.getMethod(
            activityThreadClass,
            "installContentProviders",
            Context::class.java,
            List::class.java
        ) ?: throw IllegalStateException("Failed to get installContentProviders method")
        
        Logger.d(tag, "Calling ActivityThread.installContentProviders()...")
        method.invoke(activityThread, appContext, providerInfoList)
        
        Logger.i(tag, "✅ Installed ${providerInfoList.size} ContentProviders via system method")
        
        // 3. 验证安装结果（可选）
        verifyInstallation(activityThread, activityThreadClass, providerInfoList)
    }
    
    /**
     * 验证 Provider 是否成功安装
     */
    private fun verifyInstallation(
        activityThread: Any,
        activityThreadClass: Class<*>,
        providerInfoList: List<android.content.pm.ProviderInfo>
    ) {
        try {
            Logger.d(tag, "Verifying ContentProvider installation...")
            
            // 尝试获取 mProviderMap 或 mProviders
            val providerMap = ReflectUtil.getFieldValue<Map<Any, Any>>(
                activityThread,
                activityThreadClass,
                "mProviderMap"
            ) ?: ReflectUtil.getFieldValue<Map<Any, Any>>(
                activityThread,
                activityThreadClass,
                "mProviders"
            )
            
            if (providerMap == null) {
                Logger.w(tag, "Failed to get provider map for verification")
                return
            }
            
            Logger.d(tag, "Provider map size: ${providerMap.size}")
            
            // 检查每个热更 Provider 是否在 mProviderMap 中
            providerInfoList.forEach { providerInfo ->
                val found = providerMap.keys.any { key ->
                    key.toString().contains(providerInfo.authority)
                }
                if (found) {
                    Logger.i(tag, "✅ Verified: ${providerInfo.authority} is in provider map")
                } else {
                    Logger.w(tag, "⚠️ Not found: ${providerInfo.authority} is not in provider map")
                }
            }
            
        } catch (e: Exception) {
            Logger.w(tag, "Failed to verify installation", e)
        }
    }
}
