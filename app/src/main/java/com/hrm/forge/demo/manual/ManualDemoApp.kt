package com.hrm.forge.demo.manual

import android.app.Application
import android.content.Context
import android.util.Log
import com.hrm.forge.Forge
import com.hrm.forge.api.LogLevel

/**
 * Manual Demo Application（方案二：手动安装）
 * 
 * ## 特点：
 * - ✅ 不需要继承 ForgeApplication
 * - ✅ 可以继承任意 Application 基类
 * - ⚙️ 需要手动转发生命周期（但 SDK 会自动处理 ApplicationLike）
 * 
 * ## 使用方法：
 * 1. 在 attachBaseContext 中调用 Forge.install()
 * 2. 在每个生命周期方法中调用对应的 Forge.dispatchXxx()
 * 
 * ## 代码生效区间：
 * - ❌ ManualDemoApp 类本身不会被热更新
 * - ❌ onCreate() 中直接调用的代码不会被热更新
 * - ✅ ManualApplicationLike 及其引用的所有代码会被热更新
 * 
 * ## 核心原则：
 * ApplicationLike 的生命周期由 SDK 自动管理，用户只需要转发 Application 的生命周期即可
 */
class ManualDemoApp : Application() {

    companion object {
        private const val TAG = "ManualDemoApp"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        Log.i(TAG, "attachBaseContext - Manual integration")

        // ⚠️ 关键：安装 Forge，必须传入 ApplicationLike 类名
        Forge.install(
            application = this,
            applicationLikeClassName = "com.hrm.forge.demo.manual.ManualApplicationLike"
        )
        
        // SDK 会自动转发 attachBaseContext 到 ApplicationLike
    }

    override fun onCreate() {
        super.onCreate()

        // ⚠️ 注意：这里的代码不会被热更新！
        Log.i(TAG, "Application onCreate (this code WON'T be hot updated)")

        // ✅ SDK 配置可以放在这里
        Forge.setLogLevel(LogLevel.DEBUG)

        // ⚠️ 关键：手动转发到 ApplicationLike
        Forge.dispatchOnCreate()

        // 打印版本信息
        printVersionInfo()
    }

    override fun onTerminate() {
        super.onTerminate()
        
        Log.i(TAG, "onTerminate")
        
        // ⚠️ 关键：手动转发到 ApplicationLike
        Forge.dispatchOnTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        
        Log.i(TAG, "onLowMemory")
        
        // ⚠️ 关键：手动转发到 ApplicationLike
        Forge.dispatchOnLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        Log.i(TAG, "onTrimMemory: $level")
        
        // ⚠️ 关键：手动转发到 ApplicationLike
        Forge.dispatchOnTrimMemory(level)
    }

    /**
     * 打印版本信息
     */
    private fun printVersionInfo() {
        val versionInfo = Forge.getCurrentVersionInfo(this)
        Log.i(TAG, "========== Forge Version Info ==========")
        Log.i(TAG, "Base version: ${versionInfo.baseVersion}")
        Log.i(TAG, "Current version: ${versionInfo.currentVersion}")
        Log.i(TAG, "Is hot update loaded: ${versionInfo.isHotUpdateLoaded}")
        if (versionInfo.isHotUpdateLoaded) {
            Log.i(TAG, "✅ Hot update is active!")
        } else {
            Log.i(TAG, "ℹ️  Running base version")
        }
        Log.i(TAG, "========================================")
    }
}
