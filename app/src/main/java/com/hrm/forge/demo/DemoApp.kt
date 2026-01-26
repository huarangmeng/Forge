package com.hrm.forge.demo

import android.util.Log
import com.hrm.forge.Forge
import com.hrm.forge.loader.ForgeApplication
import com.hrm.forge.logger.Logger

/**
 * Demo Application
 *
 * 演示如何使用 Forge 框架
 */
class DemoApp : ForgeApplication() {

    companion object {
        private const val TAG = "DemoApp"
    }

    /**
     * 返回 ApplicationLike 类名
     * 这是必须实现的方法
     */
    override fun getApplicationLike(): String {
        return "com.hrm.forge.demo.DemoApplicationLike"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Application onCreate")

        // 初始化 Forge
        Forge.init(this)

        // 设置日志级别
        Forge.setLogLevel(Logger.LogLevel.DEBUG)

        // 打印版本信息
        val versionInfo = Forge.getCurrentVersionInfo(this)
        Log.i(TAG, "========== Forge Version Info ==========")
        Log.i(TAG, "Base version: ${versionInfo.baseVersion}")
        Log.i(TAG, "Base version code: ${versionInfo.baseVersionCode}")
        Log.i(TAG, "Current version: ${versionInfo.currentVersion}")
        Log.i(TAG, "Current version code: ${versionInfo.currentVersionCode}")
        Log.i(TAG, "Is hot update loaded: ${versionInfo.isHotUpdateLoaded}")
        
        if (versionInfo.isHotUpdateLoaded) {
            Log.i(TAG, "Build number: ${versionInfo.buildNumber}")
            Log.i(TAG, "APK path: ${versionInfo.apkPath}")
            Log.i(TAG, "SHA1: ${versionInfo.sha1}")
        }
        Log.i(TAG, "========================================")
    }
}
