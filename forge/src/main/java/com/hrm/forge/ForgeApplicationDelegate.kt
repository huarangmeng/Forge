package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.internal.hook.HookManager
import com.hrm.forge.internal.loader.ForgeAllLoader
import com.hrm.forge.internal.log.Logger
import com.hrm.forge.internal.util.DataStorage

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
 * 
 * @since 2.0.0 重构使用 HookManager 统一管理所有 Hook
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
     * @param applicationLikeClassName ApplicationLike 类名
     */
    fun install(application: Application, applicationLikeClassName: String) {
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
            DataStorage.init(application)
            Logger.i(TAG, "✓ Data storage initialized")

            // 2. 加载热更新 APK（获取热更新 APK 路径）
            val loadResult = ForgeAllLoader.loadNewApk(
                application,
                applicationLikeClassName,
                application.applicationInfo.nativeLibraryDir
            )
            Logger.i(TAG, "✓ Hot update load result: $loadResult")

            // 3. 使用 HookManager 统一初始化所有 Hook
            // HookManager 会自动处理：
            // - ComponentManager 初始化（解析组件信息）
            // - InstrumentationHook（拦截 Activity）
            // - AMSHook（拦截 Service 和 Receiver）
            // - ContentProviderHook（安装 ContentProvider）
            val hotUpdateApkPath = ForgeAllLoader.getCurrentApkPath()
            HookManager.init(application, hotUpdateApkPath)
            Logger.i(TAG, "✓ All hooks initialized via HookManager")

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
