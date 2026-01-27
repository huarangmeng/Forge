package com.hrm.forge.internal.state

import android.content.Context
import com.hrm.forge.internal.util.Constants
import com.hrm.forge.internal.util.DataStorage
import com.hrm.forge.internal.util.FileUtils
import com.hrm.forge.internal.log.Logger
import java.io.File

/**
 * 版本状态管理器（内部实现）
 *
 * 统一管理热更新的所有状态信息：
 * - 版本信息（当前版本、上一版本）
 * - 加载状态（是否加载成功、是否待重启）
 * - 版本文件管理（以版本号命名的文件夹）
 * - 回滚和清理操作
 * 
 * @hide 此类仅供内部使用，不对外暴露
 */
internal object VersionStateManager {
    private const val TAG = "VersionStateManager"

    // 存储键
    private const val KEY_CURRENT_VERSION = "forge_current_version"
    private const val KEY_CURRENT_VERSION_CODE = "forge_current_version_code"
    private const val KEY_CURRENT_APK_PATH = "forge_current_apk_path"
    private const val KEY_CURRENT_SHA1 = "forge_current_sha1"

    private const val KEY_PREVIOUS_VERSION = "forge_previous_version"
    private const val KEY_PREVIOUS_VERSION_CODE = "forge_previous_version_code"
    private const val KEY_PREVIOUS_APK_PATH = "forge_previous_apk_path"

    private const val KEY_LOAD_SUCCESS = "forge_load_success"
    private const val KEY_PENDING_RESTART = "forge_pending_restart"
    
    // 记录实际运行的版本（加载成功后才更新）
    private const val KEY_RUNTIME_VERSION = "forge_runtime_version"
    private const val KEY_RUNTIME_VERSION_CODE = "forge_runtime_version_code"
    private const val KEY_RUNTIME_APK_PATH = "forge_runtime_apk_path"

    /**
     * 版本状态数据类（内部使用）
     */
    internal data class VersionState(
        val currentVersion: String?,           // 当前配置的版本号
        val currentVersionCode: Long,          // 当前配置的版本码
        val currentApkPath: String?,           // 当前 APK 路径
        val currentSha1: String?,              // 当前 APK SHA1

        val previousVersion: String?,          // 上一个版本号
        val previousVersionCode: Long,         // 上一个版本码
        val previousApkPath: String?,          // 上一个 APK 路径

        val isLoadSuccess: Boolean,            // 是否加载成功过（运行时状态）
        val isPendingRestart: Boolean          // 是否有待重启生效的更改
    ) {
        /**
         * 是否配置了热更新
         */
        val hasHotUpdate: Boolean
            get() = currentVersion != null && currentApkPath != null

        /**
         * 是否可以回滚
         */
        val canRollback: Boolean
            get() = previousVersion != null
    }

    /**
     * 获取当前版本状态
     */
    internal fun getVersionState(): VersionState {
        return VersionState(
            currentVersion = DataStorage.getString(KEY_CURRENT_VERSION),
            currentVersionCode = DataStorage.getLong(KEY_CURRENT_VERSION_CODE, 0L),
            currentApkPath = DataStorage.getString(KEY_CURRENT_APK_PATH),
            currentSha1 = DataStorage.getString(KEY_CURRENT_SHA1),

            previousVersion = DataStorage.getString(KEY_PREVIOUS_VERSION),
            previousVersionCode = DataStorage.getLong(KEY_PREVIOUS_VERSION_CODE, 0L),
            previousApkPath = DataStorage.getString(KEY_PREVIOUS_APK_PATH),

            isLoadSuccess = DataStorage.getBoolean(KEY_LOAD_SUCCESS, false),
            isPendingRestart = DataStorage.getBoolean(KEY_PENDING_RESTART, false)
        )
    }

    /**
     * 保存新版本信息
     */
    internal fun saveNewVersion(context: Context, version: String, versionCode: Long, apkPath: String, sha1: String) {
        Logger.i(TAG, "Save new version: $version ($versionCode)")

        // 获取当前实际运行的版本（用作 previousVersion）
        val runtimeVersion = DataStorage.getString(KEY_RUNTIME_VERSION)
        val runtimeVersionCode = DataStorage.getLong(KEY_RUNTIME_VERSION_CODE, 0L)
        val runtimeApkPath = DataStorage.getString(KEY_RUNTIME_APK_PATH)
        
        if (runtimeVersion != null && runtimeApkPath != null) {
            // 当前有热更新在运行，备份运行中的版本
            DataStorage.putString(KEY_PREVIOUS_VERSION, runtimeVersion)
            DataStorage.putLong(KEY_PREVIOUS_VERSION_CODE, runtimeVersionCode)
            DataStorage.putString(KEY_PREVIOUS_APK_PATH, runtimeApkPath)
            Logger.i(TAG, "✅ Backed up runtime version as previous: $runtimeVersion")
        } else {
            // 当前运行的是基础版本，标记可以回滚到基础版本
            DataStorage.putString(KEY_PREVIOUS_VERSION, "BASE")
            DataStorage.putLong(KEY_PREVIOUS_VERSION_CODE, 0L)
            DataStorage.putString(KEY_PREVIOUS_APK_PATH, "")
            Logger.i(TAG, "✅ Running base version, can rollback to BASE")
        }

        // 保存新版本信息
        DataStorage.putString(KEY_CURRENT_VERSION, version)
        DataStorage.putLong(KEY_CURRENT_VERSION_CODE, versionCode)
        DataStorage.putString(KEY_CURRENT_APK_PATH, apkPath)
        DataStorage.putString(KEY_CURRENT_SHA1, sha1)

        // 标记待重启
        DataStorage.putBoolean(KEY_PENDING_RESTART, true)

        Logger.i(TAG, "✅ Version saved, pending restart")
    }

    /**
     * 标记加载成功
     */
    internal fun markLoadSuccess() {
        // 获取当前配置的版本信息
        val currentVersion = DataStorage.getString(KEY_CURRENT_VERSION)
        val currentVersionCode = DataStorage.getLong(KEY_CURRENT_VERSION_CODE, 0L)
        val currentApkPath = DataStorage.getString(KEY_CURRENT_APK_PATH)
        
        // 记录实际运行的版本（用于下次发布时确定 previousVersion）
        if (currentVersion != null && currentApkPath != null) {
            DataStorage.putString(KEY_RUNTIME_VERSION, currentVersion)
            DataStorage.putLong(KEY_RUNTIME_VERSION_CODE, currentVersionCode)
            DataStorage.putString(KEY_RUNTIME_APK_PATH, currentApkPath)
            Logger.i(TAG, "✅ Marked runtime version: $currentVersion")
        }
        
        DataStorage.putBoolean(KEY_LOAD_SUCCESS, true)
        DataStorage.putBoolean(KEY_PENDING_RESTART, false)
        Logger.i(TAG, "✅ Marked load success")
    }

    /**
     * 清除待重启标记
     */
    internal fun clearPendingRestart() {
        // 清除运行时版本（因为运行的是基础版本）
        DataStorage.remove(KEY_RUNTIME_VERSION)
        DataStorage.remove(KEY_RUNTIME_VERSION_CODE)
        DataStorage.remove(KEY_RUNTIME_APK_PATH)
        
        DataStorage.putBoolean(KEY_PENDING_RESTART, false)
        DataStorage.putBoolean(KEY_LOAD_SUCCESS, false)
        Logger.i(TAG, "✅ Cleared pending restart flag (no hot update to load)")
    }

    /**
     * 回滚到上一个版本
     */
    internal fun rollbackToPreviousVersion(context: Context): Boolean {
        Logger.i(TAG, "Start rollback to previous version")

        val currentState = getVersionState()

        if (!currentState.canRollback) {
            Logger.e(TAG, "No previous version to rollback")
            return false
        }

        // 检查是否回滚到基础版本
        if (currentState.previousVersion == "BASE") {
            Logger.i(TAG, "Rollback to BASE version")
            return rollbackToBaseVersion(context)
        }

        // 检查上一版本 APK 是否存在
        val previousApkPath = currentState.previousApkPath
        if (previousApkPath.isNullOrEmpty() || !File(previousApkPath).exists()) {
            Logger.w(TAG, "Previous APK not exists, fallback to BASE")
            return rollbackToBaseVersion(context)
        }

        try {
            // ⚠️ 不删除当前版本文件，保留版本历史
            
            // 切换到上一个版本
            DataStorage.putString(KEY_CURRENT_VERSION, currentState.previousVersion)
            DataStorage.putLong(KEY_CURRENT_VERSION_CODE, currentState.previousVersionCode)
            DataStorage.putString(KEY_CURRENT_APK_PATH, currentState.previousApkPath)

            // 将当前版本变成新的 previousVersion（可以再次回滚）
            DataStorage.putString(KEY_PREVIOUS_VERSION, currentState.currentVersion)
            DataStorage.putLong(KEY_PREVIOUS_VERSION_CODE, currentState.currentVersionCode)
            DataStorage.putString(KEY_PREVIOUS_APK_PATH, currentState.currentApkPath)

            // 标记待重启
            DataStorage.putBoolean(KEY_PENDING_RESTART, true)
            DataStorage.putBoolean(KEY_LOAD_SUCCESS, false)

            Logger.i(TAG, "✅ Rollback success: ${currentState.previousVersion}")
            Logger.i(TAG, "📝 Can rollback again to: ${currentState.currentVersion}")
            Logger.i(TAG, "⚠️ Please restart the app to apply changes")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Rollback failed", e)
            return false
        }
    }

    /**
     * 回滚到基础版本（清除所有热更新）
     */
    internal fun rollbackToBaseVersion(context: Context): Boolean {
        Logger.i(TAG, "Start rollback to BASE version")

        try {
            val currentState = getVersionState()

            // 删除当前版本文件
            if (currentState.currentVersion != null) {
                deleteVersionFiles(context, currentState.currentVersion)
            }

            // 删除上一版本文件
            if (currentState.previousVersion != null && currentState.previousVersion != "BASE") {
                deleteVersionFiles(context, currentState.previousVersion)
            }

            // 清除所有热更新相关的配置
            clearAllVersionData()

            Logger.i(TAG, "✅ Rollback to BASE version success")
            Logger.i(TAG, "⚠️ Please restart the app to apply changes")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Rollback to BASE failed", e)
            return false
        }
    }

    /**
     * 清理上一个版本
     */
    internal fun cleanPreviousVersion(context: Context): Boolean {
        Logger.i(TAG, "Start clean previous version")

        try {
            val currentState = getVersionState()

            if (currentState.previousVersion != null && currentState.previousVersion != "BASE") {
                // 删除上一版本文件
                deleteVersionFiles(context, currentState.previousVersion)

                Logger.i(TAG, "Deleted previous version: ${currentState.previousVersion}")
            }

            // 清除上一版本信息
            DataStorage.remove(KEY_PREVIOUS_VERSION)
            DataStorage.remove(KEY_PREVIOUS_VERSION_CODE)
            DataStorage.remove(KEY_PREVIOUS_APK_PATH)

            Logger.i(TAG, "✅ Clean previous version success")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Clean previous version failed", e)
            return false
        }
    }

    /**
     * 删除指定版本的文件夹
     */
    private fun deleteVersionFiles(context: Context, version: String) {
        val versionDir = getVersionDir(context, version)
        if (versionDir.exists()) {
            FileUtils.deleteRecursively(versionDir)
            Logger.i(TAG, "Deleted version dir: ${versionDir.absolutePath}")
        }
    }

    /**
     * 清除所有版本数据
     */
    private fun clearAllVersionData() {
        DataStorage.remove(KEY_CURRENT_VERSION)
        DataStorage.remove(KEY_CURRENT_VERSION_CODE)
        DataStorage.remove(KEY_CURRENT_APK_PATH)
        DataStorage.remove(KEY_CURRENT_SHA1)

        DataStorage.remove(KEY_PREVIOUS_VERSION)
        DataStorage.remove(KEY_PREVIOUS_VERSION_CODE)
        DataStorage.remove(KEY_PREVIOUS_APK_PATH)
        
        // 清除运行时版本
        DataStorage.remove(KEY_RUNTIME_VERSION)
        DataStorage.remove(KEY_RUNTIME_VERSION_CODE)
        DataStorage.remove(KEY_RUNTIME_APK_PATH)

        DataStorage.putBoolean(KEY_LOAD_SUCCESS, false)
        DataStorage.putBoolean(KEY_PENDING_RESTART, true)
    }

    /**
     * 获取版本文件夹路径
     */
    internal fun getVersionDir(context: Context, version: String): File {
        return File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}/$version")
    }

    /**
     * 清理所有版本文件夹
     */
    internal fun cleanAllVersions(context: Context): Boolean {
        Logger.i(TAG, "Start clean all versions")

        try {
            val versionsDir =
                File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}")
            if (versionsDir.exists() && versionsDir.isDirectory) {
                FileUtils.deleteRecursively(versionsDir)
                versionsDir.mkdirs()
                Logger.i(TAG, "Deleted all version directories")
            }

            clearAllVersionData()

            Logger.i(TAG, "✅ Clean all versions success")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Clean all versions failed", e)
            return false
        }
    }
}
