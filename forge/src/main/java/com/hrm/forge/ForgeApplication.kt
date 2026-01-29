package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.internal.log.Logger

/**
 * Forge Application 基类（方案一：继承方案）
 * 
 * ## 特点：
 * - ✅ 最简单的集成方式，只需继承并返回 ApplicationLike 类名
 * - ✅ SDK 自动处理所有生命周期转发
 * - ✅ 无需手动调用任何方法
 * 
 * ## 使用方法：
 * ```kotlin
 * // 1. Application 类
 * class MyApplication : ForgeApplication() {
 *     override fun getApplicationLike(): String {
 *         return "com.example.MyApplicationLike"
 *     }
 * }
 * 
 * // 2. ApplicationLike 类
 * class MyApplicationLike(private val context: Context) {
 *     fun onCreate() {
 *         // ✅ 这里的代码会被热更新
 *         initSDK()
 *         UserManager.init()
 *     }
 * }
 * 
 * // 3. AndroidManifest.xml
 * <application android:name=".MyApplication" ...>
 * ```
 * 
 * ## 代码生效区间：
 * 
 * **会被热更新的代码**：
 * - ✅ ApplicationLike 的所有代码
 * - ✅ ApplicationLike 引用的所有业务类
 * - ✅ Activity/Service/BroadcastReceiver 组件
 * 
 * **不会被热更新的代码**：
 * - ❌ Application 类本身
 * - ❌ Application.onCreate() 中直接调用的代码
 * 
 * ## 如果无法继承？
 * 
 * 如果项目已有 Application 基类无法继承，请使用方案二：
 * ```kotlin
 * class MyApplication : BaseApplication() {
 *     override fun attachBaseContext(base: Context) {
 *         super.attachBaseContext(base)
 *         Forge.install(this, "com.example.MyApplicationLike")
 *     }
 *     
 *     override fun onCreate() {
 *         super.onCreate()
 *         Forge.dispatchOnCreate()
 *     }
 *     
 *     override fun onTerminate() {
 *         super.onTerminate()
 *         Forge.dispatchOnTerminate()
 *     }
 *     
 *     override fun onLowMemory() {
 *         super.onLowMemory()
 *         Forge.dispatchOnLowMemory()
 *     }
 *     
 *     override fun onTrimMemory(level: Int) {
 *         super.onTrimMemory(level)
 *         Forge.dispatchOnTrimMemory(level)
 *     }
 * }
 * ```
 * 
 * 这是 Forge SDK 的公开 API
 */
abstract class ForgeApplication : Application() {

    private val TAG = "ForgeApplication"

    /**
     * 子类需要实现此方法，返回 ApplicationLike 的完整类名
     * 
     * @return ApplicationLike 的完整类名（必须提供）
     */
    protected abstract fun getApplicationLike(): String

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        // SDK 负责初始化和生命周期管理
        ForgeApplicationDelegate.install(this, getApplicationLike())
        
        // SDK 自动转发 attachBaseContext
        ForgeApplicationDelegate.dispatchAttachBaseContext(this)
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "Application onCreate")

        // SDK 自动转发到 ApplicationLike
        ForgeApplicationDelegate.dispatchOnCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
        Logger.i(TAG, "Application onTerminate")

        // SDK 自动转发到 ApplicationLike
        ForgeApplicationDelegate.dispatchOnTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Logger.i(TAG, "Application onLowMemory")

        // SDK 自动转发到 ApplicationLike
        ForgeApplicationDelegate.dispatchOnLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.i(TAG, "Application onTrimMemory: $level")

        // SDK 自动转发到 ApplicationLike
        ForgeApplicationDelegate.dispatchOnTrimMemory(level)
    }
}
