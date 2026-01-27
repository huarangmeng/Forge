package com.hrm.forge.demo

import android.util.Log
import com.hrm.forge.Forge
import com.hrm.forge.ForgeApplication
import com.hrm.forge.api.LogLevel

/**
 * Demo Application（方案一：继承 ForgeApplication）
 *
 * ## 特点：
 * - ✅ 最简单的集成方式
 * - ✅ SDK 自动处理所有生命周期转发
 * - ✅ 无需手动调用任何方法
 * 
 * ## 代码生效区间：
 * - ❌ DemoApp 类本身不会被热更新
 * - ❌ onCreate() 中直接调用的代码不会被热更新
 * - ✅ DemoApplicationLike 及其引用的所有代码会被热更新
 * 
 * ## 关键规则：
 * 所有业务初始化必须在 ApplicationLike 中，不要在 Application 中！
 */
class DemoApp : ForgeApplication() {

    companion object {
        private const val TAG = "DemoApp"
    }

    /**
     * 返回 ApplicationLike 类名（必须实现）
     */
    override fun getApplicationLike(): String {
        return "com.hrm.forge.demo.DemoApplicationLike"
    }

    override fun onCreate() {
        super.onCreate()

        // ⚠️ 注意：这里的代码不会被热更新！
        Log.i(TAG, "Application onCreate (this code WON'T be hot updated)")

        // ✅ SDK 配置可以放在这里（这些不需要热更新）
        Forge.setLogLevel(LogLevel.DEBUG)

        // ❌ 错误示例：不要在这里初始化业务代码
        // UserManager.init()  // 这不会被热更新
        // NetworkManager.init()  // 这不会被热更新

        // ✅ 正确做法：所有业务初始化应该在 DemoApplicationLike.onCreate() 中
        
        // 打印版本信息
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
