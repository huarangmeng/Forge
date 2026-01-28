package com.hrm.forge.internal.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hrm.forge.internal.log.Logger

/**
 * 热更场景 ContentProvider Hook 工具类
 * 
 * **核心策略：调用系统 ActivityThread.installContentProviders 安装热更 Provider**
 * 
 * 工作原理：
 * 1. 使用 PackageParser 解析热更 APK，获取 ProviderInfo 列表
 * 2. 修改 ProviderInfo 的 packageName 为主 APK 的包名（避免 ClassLoader 问题）
 * 3. 调用 ActivityThread.installContentProviders() 让系统自动安装
 * 4. 系统会自动创建 Provider 实例、添加到 mProviderMap、调用 onCreate 等
 * 5. 调用方使用正常 URI：content://com.hrm.forge.upgrade.test.provider/users
 * 
 * 优势：
 * - ✅ 调用系统方法，兼容性最强
 * - ✅ 无需手动操作 mProviderMap、ProviderKey 等内部结构
 * - ✅ 系统自动管理 Provider 生命周期
 * - ✅ 调用方使用正常 URI，无需修改
 */
internal object ContentProviderHook {
    private const val TAG = "ContentProviderHook"
    
    private var isHooked = false
    private lateinit var appContext: Context

    /**
     * 初始化 Hook
     */
    fun hook(context: Context) {
        if (isHooked) {
            Logger.i(TAG, "ContentProvider already hooked, skip")
            return
        }
        
        appContext = context

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                doHook()
            }
        } else {
            doHook()
        }
    }

    private fun doHook() {
        try {
            Logger.i(TAG, "Start hooking ContentProvider...")
            
            // 1. 从 ComponentManager 获取热更 Provider 信息
            val hotUpdateProviders = ComponentManager.getHotUpdateProviders()
            
            if (hotUpdateProviders.isEmpty()) {
                Logger.i(TAG, "No hot update providers found, skip hooking")
                return
            }
            
            Logger.i(TAG, "Found ${hotUpdateProviders.size} hot update providers:")
            hotUpdateProviders.forEach { (authority, providerInfo) ->
                Logger.i(TAG, "  - $authority -> ${providerInfo.className}")
            }
            
            // 2. 将 ComponentManager 的 ProviderInfo 转换为系统的 android.content.pm.ProviderInfo
            val androidProviderInfoList = hotUpdateProviders.map { (authority, providerInfo) ->
                createAndroidProviderInfo(authority, providerInfo)
            }
            
            // 3. 调用系统方法安装 ContentProvider
            installContentProviders(androidProviderInfoList)
            
            isHooked = true
            Logger.i(TAG, "✅ ContentProvider hook successfully")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hook ContentProvider", e)
        }
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
     * 
     * 安装后，还需要包装每个 Provider 实例，拦截 notifyChange 调用
     */
    private fun installContentProviders(providerInfoList: List<android.content.pm.ProviderInfo>) {
        try {
            Logger.d(TAG, "Installing ${providerInfoList.size} ContentProviders...")
            
            // 1. 获取 ActivityThread 实例
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)
            
            if (activityThread == null) {
                Logger.e(TAG, "ActivityThread instance is null!")
                return
            }
            
            Logger.d(TAG, "Got ActivityThread instance: ${activityThread.javaClass.name}")
            
            // 2. 调用 installContentProviders() 方法
            val installContentProvidersMethod = activityThreadClass.getDeclaredMethod(
                "installContentProviders",
                Context::class.java,
                List::class.java
            )
            installContentProvidersMethod.isAccessible = true
            
            Logger.d(TAG, "Calling ActivityThread.installContentProviders()...")
            installContentProvidersMethod.invoke(activityThread, appContext, providerInfoList)
            
            Logger.i(TAG, "✅ Installed ${providerInfoList.size} ContentProviders via system method")
            
            // 3. 验证安装结果（可选）
            verifyInstallation(providerInfoList)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to install ContentProviders", e)
            throw e
        }
    }

    /**
     * 验证 Provider 是否成功安装
     */
    private fun verifyInstallation(providerInfoList: List<android.content.pm.ProviderInfo>) {
        try {
            Logger.d(TAG, "Verifying ContentProvider installation...")
            
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField = activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            val activityThread = sCurrentActivityThreadField.get(null)
            
            val providerMapField = try {
                activityThreadClass.getDeclaredField("mProviderMap")
            } catch (e: NoSuchFieldException) {
                activityThreadClass.getDeclaredField("mProviders")
            }
            providerMapField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val providerMap = providerMapField.get(activityThread) as Map<Any, Any>
            
            Logger.d(TAG, "mProviderMap size: ${providerMap.size}")
            
            // 检查每个热更 Provider 是否在 mProviderMap 中
            providerInfoList.forEach { providerInfo ->
                val found = providerMap.keys.any { key ->
                    key.toString().contains(providerInfo.authority)
                }
                if (found) {
                    Logger.i(TAG, "✅ Verified: ${providerInfo.authority} is in mProviderMap")
                } else {
                    Logger.w(TAG, "⚠️ Not found: ${providerInfo.authority} is not in mProviderMap")
                }
            }
            
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to verify installation", e)
        }
    }

    /**
     * 清理 Hook（测试用）
     */
    fun reset() {
        isHooked = false
        Logger.d(TAG, "ContentProviderHook reset")
    }
}
