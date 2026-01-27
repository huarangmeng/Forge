package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.internal.util.DataStorage
import com.hrm.forge.internal.hook.AMSHook
import com.hrm.forge.internal.hook.InstrumentationHook
import com.hrm.forge.internal.loader.ForgeAllLoader
import com.hrm.forge.internal.log.Logger

/**
 * Forge Application 代理（内部实现）
 * 
 * 职责：
 * 1. 在 attachBaseContext 阶段完成 Hook 和热更新加载
 * 2. 创建 ApplicationLike 实例
 * 3. 提供生命周期分发方法
 * 
 * 使用者无需了解此类的实现细节，SDK 会自动处理一切
 * 
 * 这是 Forge SDK 的内部 API
 */
internal object ForgeApplicationDelegate {
    
    private const val TAG = "ForgeDelegate"
    
    private var applicationLikeInstance: Any? = null
    private var isInstalled = false
    
    /**
     * 安装 Forge（内部使用）
     * 
     * 此方法由 ForgeApplication 或 Forge.install() 调用
     * 完成所有初始化工作
     * 
     * @param application Application 实例
     * @param base Context
     * @param applicationLikeClassName ApplicationLike 类名
     */
    fun install(application: Application, base: Context, applicationLikeClassName: String) {
        if (isInstalled) {
            Logger.w(TAG, "Already installed")
            return
        }
        
        try {
            Logger.i(TAG, "========================================")
            Logger.i(TAG, "Forge installing...")
            Logger.i(TAG, "ApplicationLike: $applicationLikeClassName")
            Logger.i(TAG, "========================================")
            
            // 1. 初始化数据存储
            DataStorage.init(base)
            Logger.i(TAG, "✓ Data storage initialized")
            
            // 2. Hook Instrumentation（必须在 Activity 启动之前）
            InstrumentationHook.hookInstrumentation(application)
            Logger.i(TAG, "✓ Instrumentation hooked")
            
            // 3. Hook AMS（必须在 Service 启动之前）
            AMSHook.hookAMS(base)
            Logger.i(TAG, "✓ AMS hooked")
            
            // 4. 加载热更新 APK
            val loadResult = ForgeAllLoader.loadNewApk(
                base,
                applicationLikeClassName,
                base.applicationInfo.nativeLibraryDir
            )
            Logger.i(TAG, "✓ Hot update load result: $loadResult")
            
            isInstalled = true
            
            Logger.i(TAG, "========================================")
            Logger.i(TAG, "Forge installed successfully")
            Logger.i(TAG, "========================================")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to install Forge", e)
        }
    }
    
    /**
     * 分发 attachBaseContext 到 ApplicationLike
     */
    fun dispatchAttachBaseContext(base: Context) {
        invokeApplicationLikeMethod("attachBaseContext", Context::class.java, base)
    }
    
    /**
     * 分发 onCreate 到 ApplicationLike
     */
    fun dispatchOnCreate() {
        invokeApplicationLikeMethod("onCreate")
    }
    
    /**
     * 分发 onTerminate 到 ApplicationLike
     */
    fun dispatchOnTerminate() {
        invokeApplicationLikeMethod("onTerminate")
    }
    
    /**
     * 分发 onLowMemory 到 ApplicationLike
     */
    fun dispatchOnLowMemory() {
        invokeApplicationLikeMethod("onLowMemory")
    }
    
    /**
     * 分发 onTrimMemory 到 ApplicationLike
     */
    fun dispatchOnTrimMemory(level: Int) {
        invokeApplicationLikeMethod("onTrimMemory", Int::class.java, level)
    }
    
    /**
     * 调用 ApplicationLike 的方法
     */
    private fun invokeApplicationLikeMethod(
        methodName: String,
        parameterType: Class<*>? = null,
        parameter: Any? = null
    ) {
        try {
            val appLike = applicationLikeInstance ?: return
            val clazz = appLike.javaClass

            val method = if (parameterType != null) {
                clazz.getMethod(methodName, parameterType)
            } else {
                clazz.getMethod(methodName)
            }

            if (parameter != null) {
                method.invoke(appLike, parameter)
            } else {
                method.invoke(appLike)
            }

            Logger.d(TAG, "✓ Invoke ApplicationLike.$methodName success")
        } catch (e: NoSuchMethodException) {
            Logger.d(TAG, "ApplicationLike.$methodName not found, skip")
        } catch (e: Exception) {
            Logger.e(TAG, "✗ Invoke ApplicationLike.$methodName failed", e)
        }
    }
    
    /**
     * 获取 ApplicationLike 实例
     */
    fun getApplicationLike(): Any? = applicationLikeInstance
    
    /**
     * 设置 ApplicationLike 实例（内部使用）
     */
    internal fun setApplicationLike(appLike: Any) {
        applicationLikeInstance = appLike
        Logger.i(TAG, "✓ ApplicationLike instance created: ${appLike.javaClass.name}")
    }
}
