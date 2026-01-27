package com.hrm.forge.internal

import android.content.Context
import android.os.Build
import com.hrm.forge.api.VersionInfo
import com.hrm.forge.internal.loader.ForgeAllLoader
import com.hrm.forge.internal.state.VersionStateManager

/**
 * Forge 版本信息查询工具（内部实现）
 *
 * 提供版本信息查询的实现，由 Forge.getCurrentVersionInfo() 对外暴露
 */
internal object ForgeVersionInfo {

    /**
     * 获取当前版本信息
     *
     * 注意：此方法区分运行时状态和配置状态：
     * - 运行时状态：当前进程已加载的版本
     * - 配置状态：下次启动将要加载的版本
     * - 当两者不一致时，表示有待生效的更改（需要重启）
     *
     * @param context Context
     * @return 版本信息
     */
    fun get(context: Context): VersionInfo {
        // 获取应用基础信息
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val baseVersionName = packageInfo.versionName ?: "unknown"
        val baseVersionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
}