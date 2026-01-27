package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.VersionStateManager
import com.hrm.forge.release.VersionReleaseManager
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
        return VersionReleaseManager.releaseNewVersion(context, apkFile, version)
    }
    
    /**
     * 清理上一个版本
     */
    fun cleanLastVersion(context: Context): Boolean {
        return VersionStateManager.cleanPreviousVersion(context)
    }
    
    /**
     * 回滚到上一个版本
     */
    fun rollbackToLastVersion(context: Context): Boolean {
        return VersionStateManager.rollbackToPreviousVersion(context)
    }
    
    /**
     * 获取当前版本信息
     * 
     * 注意：此方法区分运行时状态和配置状态：
     * - 运行时状态：当前进程已加载的版本
     * - 配置状态：下次启动将要加载的版本
     * - 当两者不一致时，表示有待生效的更改（需要重启）
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
        
        // 获取版本状态（从 VersionStateManager）
        val versionState = VersionStateManager.getVersionState()
        
        // 运行时状态：当前进程是否已加载热更新
        val isRuntimeLoaded = ForgeAllLoader.isLoaded()
        val runtimeVersion = if (isRuntimeLoaded) {
            ForgeAllLoader.getCurrentVersion()
        } else {
            null
        }
        
        // 确定当前运行版本
        val currentVersion = if (isRuntimeLoaded && runtimeVersion != null) {
            "$appName $runtimeVersion"
        } else {
            "$appName $baseVersionName"
        }
        
        val currentVersionCode = if (isRuntimeLoaded && versionState.hasHotUpdate) {
            versionState.currentVersionCode
        } else {
            baseVersionCode
        }
        
        // 确定下次启动的版本
        val nextVersion = if (versionState.hasHotUpdate) {
            "$appName ${versionState.currentVersion}"
        } else {
            "$appName $baseVersionName"
        }
        
        val nextVersionCode = if (versionState.hasHotUpdate) {
            versionState.currentVersionCode
        } else {
            baseVersionCode
        }
        
        // 判断是否有待生效的更改
        val hasPendingChange = versionState.isPendingRestart
        
        // 判断是否可以回滚
        val canRollback = versionState.canRollback
        
        return VersionInfo(
            baseVersion = "$appName $baseVersionName",
            baseVersionCode = baseVersionCode,
            currentVersion = currentVersion,
            currentVersionCode = currentVersionCode,
            nextVersion = nextVersion,
            nextVersionCode = nextVersionCode,
            isHotUpdateLoaded = isRuntimeLoaded,
            hasPendingChange = hasPendingChange,
            canRollback = canRollback,
            apkPath = versionState.currentApkPath,
            sha1 = versionState.currentSha1,
            buildNumber = if (versionState.hasHotUpdate) versionState.currentVersionCode else null
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
        val currentVersion: String,             // 当前运行版本（应用名 + 版本号）
        val currentVersionCode: Long,           // 当前运行版本号
        val nextVersion: String,                // 下次启动版本（应用名 + 版本号）
        val nextVersionCode: Long,              // 下次启动版本号
        val isHotUpdateLoaded: Boolean,         // 运行时是否已加载热更新
        val hasPendingChange: Boolean,          // 是否有待生效的更改（需要重启）
        val canRollback: Boolean,               // 是否可以回滚
        val apkPath: String?,                   // APK 路径
        val sha1: String?,                      // SHA1 校验值
        val buildNumber: Long?                  // 构建号
    )
}