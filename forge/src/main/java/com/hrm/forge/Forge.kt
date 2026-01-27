package com.hrm.forge

import android.app.Application
import android.content.Context
import com.hrm.forge.api.ILogger
import com.hrm.forge.api.LogLevel
import com.hrm.forge.api.VersionInfo
import com.hrm.forge.internal.loader.ForgeAllLoader
import com.hrm.forge.internal.log.Logger
import com.hrm.forge.internal.release.VersionReleaseManager
import com.hrm.forge.internal.state.VersionStateManager
import java.io.File

/**
 * Forge - Android 热更新框架
 *
 * 这是 Forge SDK 的唯一公开 API 入口点
 *
 * ## 两种集成方案：
 *
 * ### 方案一：继承 ForgeApplication（推荐）
 *
 * ```kotlin
 * class MyApplication : ForgeApplication() {
 *     override fun getApplicationLike(): String {
 *         return "com.example.MyApplicationLike"
 *     }
 * }
 * ```
 *
 * **特点：** SDK 自动处理所有生命周期，无需手动调用
 *
 * ### 方案二：手动安装（已有基类时使用）
 *
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
 * **特点：** 不需要继承，需要手动转发生命周期（但 SDK 会自动处理 ApplicationLike）
 *
 * ## 两种方案的区别：
 *
 * | 特性 | 方案一（继承） | 方案二（安装） |
 * |------|--------------|--------------|
 * | 继承要求 | 必须继承 ForgeApplication | 可以继承任意 Application |
 * | 生命周期转发 | SDK 自动处理 | 需要手动调用 dispatch 方法 |
 * | ApplicationLike 管理 | SDK 自动管理 | SDK 自动管理 |
 * | 代码量 | 最少 | 略多 |
 * | 推荐场景 | 新项目 | 已有 Application 基类 |
 *
 * ## 核心原则：
 *
 * 无论使用哪种方案，ApplicationLike 的生命周期都由 SDK 自动管理，
 * 用户不需要手动调用 ApplicationLike 的方法。
 */
object Forge {

    /**
     * 安装 Forge（方案二：手动安装）
     *
     * 在 Application.attachBaseContext 中调用此方法
     *
     * ⚠️ 注意：使用此方案后，需要在 Application 的所有生命周期方法中
     * 手动调用对应的 dispatch 方法，例如：
     * - onCreate() → Forge.dispatchOnCreate()
     * - onTerminate() → Forge.dispatchOnTerminate()
     * - onLowMemory() → Forge.dispatchOnLowMemory()
     * - onTrimMemory(level) → Forge.dispatchOnTrimMemory(level)
     *
     * @param application Application 实例
     * @param applicationLikeClassName ApplicationLike 类名（必须提供）
     */
    fun install(application: Application, applicationLikeClassName: String) {
        val context = application.baseContext ?: application
        ForgeApplicationDelegate.install(application, context, applicationLikeClassName)

        // 立即转发 attachBaseContext
        ForgeApplicationDelegate.dispatchAttachBaseContext(context)
    }

    /**
     * 分发 onCreate 到 ApplicationLike
     *
     * 在 Application.onCreate() 中调用
     */
    fun dispatchOnCreate() {
        ForgeApplicationDelegate.dispatchOnCreate()
    }

    /**
     * 分发 onTerminate 到 ApplicationLike
     *
     * 在 Application.onTerminate() 中调用
     */
    fun dispatchOnTerminate() {
        ForgeApplicationDelegate.dispatchOnTerminate()
    }

    /**
     * 分发 onLowMemory 到 ApplicationLike
     *
     * 在 Application.onLowMemory() 中调用
     */
    fun dispatchOnLowMemory() {
        ForgeApplicationDelegate.dispatchOnLowMemory()
    }

    /**
     * 分发 onTrimMemory 到 ApplicationLike
     *
     * 在 Application.onTrimMemory(level) 中调用
     */
    fun dispatchOnTrimMemory(level: Int) {
        ForgeApplicationDelegate.dispatchOnTrimMemory(level)
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
    fun setLogLevel(level: LogLevel) {
        Logger.setLogLevel(level)
    }

    /**
     * 发布新版本 APK（自动从 APK 读取版本号）
     *
     * @param context Context
     * @param apkFile APK 文件
     * @return 是否成功
     */
    suspend fun releaseNewApk(context: Context, apkFile: File): Boolean {
        return VersionReleaseManager.releaseNewVersion(context, apkFile)
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
        val baseVersionCode =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
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
}
