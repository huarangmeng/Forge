package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.builder.ForgeBuilderService
import com.hrm.forge.loader.ForgeAllLoader
import com.hrm.forge.loader.instrumentation.HookHelper
import com.hrm.forge.logger.ILogger
import com.hrm.forge.logger.Logger
import java.io.File

/**
 * Forge - Android 热更新框架
 * 
 * 使用方法：
 * 1. 让你的 Application 继承 ForgeApplication
 * 2. 实现 getApplicationLike() 方法返回实际的 ApplicationLike 类名
 * 3. 调用 Forge.init() 初始化
 * 4. 使用 ForgeBuilderService.releaseNewApk() 发布新版本
 */
object Forge {
    
    private const val TAG = "Forge"
    
    @Volatile
    private var isInitialized = false
    
    /**
     * 初始化 Forge
     * @param application Application 实例
     */
    fun init(application: Application) {
        if (isInitialized) {
            Logger.w(TAG, "Already initialized")
            return
        }
        
        synchronized(this) {
            if (isInitialized) {
                return
            }
            
            Logger.i(TAG, "Initializing Forge...")
            
            // Hook Instrumentation
            HookHelper.hookInstrumentation(application)
            
            isInitialized = true
            Logger.i(TAG, "Forge initialized")
        }
    }
    
    /**
     * 设置日志实现
     */
    fun setLogger(logger: ILogger) {
        Logger.setLogger(logger)
    }
    
    /**
     * 设置日志级别
     */
    fun setLogLevel(level: Logger.LogLevel) {
        Logger.setLogLevel(level)
    }
    
    /**
     * 发布新版本 APK
     * @param context Context
     * @param apkFile APK 文件
     * @param version 版本号
     * @return 是否成功
     */
    suspend fun releaseNewApk(context: Context, apkFile: File, version: String): Boolean {
        return ForgeBuilderService.releaseNewApk(context, apkFile, version)
    }
    
    /**
     * 清理上一个版本
     */
    fun cleanLastVersion(context: Context): Boolean {
        return ForgeBuilderService.cleanPreviousVersion(context)
    }
    
    /**
     * 回滚到上一个版本
     */
    fun rollbackToLastVersion(context: Context): Boolean {
        return ForgeBuilderService.rollbackToPreviousVersion(context)
    }
    
    /**
     * 获取当前版本信息
     */
    fun getCurrentVersionInfo(context: Context): VersionInfo {
        // 获取应用基础信息
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val baseVersionName = packageInfo.versionName ?: "unknown"
        val baseVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        
        // 获取热更新信息
        val hotUpdateInfo = ForgeBuilderService.getCurrentVersionInfo(context)
        val isHotUpdateLoaded = ForgeAllLoader.isLoaded()
        
        // 确定当前版本：优先使用热更新版本，否则使用基础版本
        val currentVersion = if (hotUpdateInfo?.version != null) {
            "$appName ${hotUpdateInfo.version}"
        } else {
            "$appName $baseVersionName"
        }
        
        val currentVersionCode = hotUpdateInfo?.buildNumber ?: baseVersionCode
        
        return VersionInfo(
            baseVersion = "$appName $baseVersionName",
            baseVersionCode = baseVersionCode,
            currentVersion = currentVersion,
            currentVersionCode = currentVersionCode,
            isHotUpdateLoaded = isHotUpdateLoaded,
            apkPath = hotUpdateInfo?.apkPath,
            sha1 = hotUpdateInfo?.sha1,
            buildNumber = hotUpdateInfo?.buildNumber
        )
    }
    
    /**
     * 获取热更新版本号
     */
    fun getHotUpdateVersion(): String? {
        return ForgeAllLoader.getCurrentVersion()
    }
    
    /**
     * 检查是否已加载热更新
     */
    fun isHotUpdateLoaded(): Boolean {
        return ForgeAllLoader.isLoaded()
    }
    
    /**
     * 版本信息数据类
     */
    data class VersionInfo(
        val baseVersion: String,                // 基础版本（应用名 + 基础版本号）
        val baseVersionCode: Long,              // 基础版本号
        val currentVersion: String,             // 当前版本（应用名 + 版本号）
        val currentVersionCode: Long,           // 当前版本号
        val isHotUpdateLoaded: Boolean,         // 是否已加载热更新
        val apkPath: String?,                   // APK 路径
        val sha1: String?,                      // SHA1 校验值
        val buildNumber: Long?                  // 构建号
    )
}
