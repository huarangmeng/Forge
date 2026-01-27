package com.hrm.forge.internal.loader

import android.content.Context
import com.hrm.forge.ForgeApplication
import com.hrm.forge.internal.hook.ComponentManager
import com.hrm.forge.internal.log.Logger
import com.hrm.forge.internal.state.VersionStateManager
import com.hrm.forge.internal.util.DataStorage
import com.hrm.forge.internal.util.FileUtils
import java.io.File

/**
 * Forge 核心加载器
 * 负责协调 DEX、资源、SO 的加载
 */
internal object ForgeAllLoader {
    private const val TAG = "ForgeAllLoader"

    // 存储键
    private const val KEY_CURRENT_VERSION = "forge_current_version"
    private const val KEY_CURRENT_APK_PATH = "forge_current_apk_path"
    private const val KEY_LOAD_SUCCESS = "forge_load_success"
    private const val KEY_LAST_VERSION = "forge_last_version"
    private const val KEY_LAST_APK_PATH = "forge_last_apk_path"

    // 加载结果码
    const val LOAD_OK = 0
    const val FAIL_VERIFY = -1
    const val FAIL_DEX = -2
    const val FAIL_RESOURCE = -3
    const val FAIL_SO = -4
    const val NO_NEW_VERSION = -100

    @Volatile
    private var isLoaded = false

    /**
     * 加载新版本 APK
     * @param context Context
     * @param applicationLikeClassName ApplicationLike 类名
     * @param nativeLibraryDir Native 库目录
     * @return 加载结果码
     */
    fun loadNewApk(
        context: Context,
        applicationLikeClassName: String?,
        nativeLibraryDir: String
    ): Int {
        if (isLoaded) {
            Logger.w(TAG, "Already loaded, skip")
            return LOAD_OK
        }

        synchronized(this) {
            if (isLoaded) {
                return LOAD_OK
            }

            try {
                // 检查是否有新版本
                val currentVersion = DataStorage.getString(KEY_CURRENT_VERSION)
                val currentApkPath = DataStorage.getString(KEY_CURRENT_APK_PATH)

                if (currentVersion == null || currentApkPath == null) {
                    Logger.i(TAG, "No new version to load (may be rolled back to base version)")
                    // 清除待重启标记（回滚到基础版本后重启的情况）
                    VersionStateManager.clearPendingRestart()
                    return NO_NEW_VERSION
                }

                val apkFile = File(currentApkPath)
                if (!apkFile.exists() || !apkFile.isFile) {
                    Logger.e(TAG, "APK file not exists: $currentApkPath")
                    return FAIL_VERIFY
                }

                Logger.i(
                    TAG,
                    "Start loading new APK: version=$currentVersion, path=$currentApkPath"
                )

                // 1. 验证新版本
                val verifyResult = verifyNewVersion(context, apkFile, currentVersion)
                if (verifyResult != LOAD_OK) {
                    Logger.e(TAG, "Verify failed: $verifyResult")
                    handleLoadFailed(context)
                    return verifyResult
                }

                // 2. 加载 DEX
                val dexResult = loadNewDex(context, apkFile)
                if (dexResult != LOAD_OK) {
                    Logger.e(TAG, "Load DEX failed: $dexResult")
                    handleLoadFailed(context)
                    return dexResult
                }

                // 3. 加载资源
                val resourceResult = loadNewResource(context, apkFile)
                if (resourceResult != LOAD_OK) {
                    Logger.e(TAG, "Load resource failed: $resourceResult")
                    handleLoadFailed(context)
                    return resourceResult
                }

                // 4. 加载 SO
                val soResult = loadNewSo(context, apkFile, nativeLibraryDir)
                if (soResult != LOAD_OK) {
                    Logger.e(TAG, "Load SO failed: $soResult")
                    handleLoadFailed(context)
                    return soResult
                }

                // 5. 初始化组件信息管理器（一次性解析 Activity 和 Service）
                ComponentManager.init(context, apkFile.absolutePath)

                // 6. 创建 ApplicationLike 实例
                if (!applicationLikeClassName.isNullOrEmpty()) {
                    createApplicationLike(context, applicationLikeClassName)
                }

                // 标记加载成功（使用 VersionStateManager）
                VersionStateManager.markLoadSuccess()
                isLoaded = true

                Logger.i(TAG, "✅ Load new APK success!")
                return LOAD_OK

            } catch (e: Exception) {
                Logger.e(TAG, "Load new APK failed", e)
                handleLoadFailed(context)
                return FAIL_VERIFY
            }
        }
    }

    /**
     * 验证新版本
     */
    private fun verifyNewVersion(context: Context, apkFile: File, version: String): Int {
        Logger.i(TAG, "Verifying new version: $version")

        // 计算 SHA1
        val sha1 = FileUtils.getFileSHA1(apkFile)
        if (sha1 == null) {
            Logger.e(TAG, "Calculate SHA1 failed")
            return FAIL_VERIFY
        }

        // 保存 SHA1
        DataStorage.putString("forge_apk_sha1_$version", sha1)

        Logger.i(TAG, "Verify success, SHA1: $sha1")
        return LOAD_OK
    }

    /**
     * 加载新的 DEX
     */
    private fun loadNewDex(context: Context, apkFile: File): Int {
        return try {
            DexLoader.installDex(context, apkFile)
            LOAD_OK
        } catch (e: Exception) {
            Logger.e(TAG, "Load DEX exception", e)
            FAIL_DEX
        }
    }

    /**
     * 加载新的资源
     */
    private fun loadNewResource(context: Context, apkFile: File): Int {
        return try {
            ResourceLoader.loadResources(context, apkFile.absolutePath)
            LOAD_OK
        } catch (e: Exception) {
            Logger.e(TAG, "Load resource exception", e)
            FAIL_RESOURCE
        }
    }

    /**
     * 加载新的 SO
     */
    private fun loadNewSo(context: Context, apkFile: File, nativeLibraryDir: String): Int {
        return try {
            NativeLibraryLoader.installNativeLibrary(context, apkFile, nativeLibraryDir)
            LOAD_OK
        } catch (e: Exception) {
            Logger.e(TAG, "Load SO exception", e)
            FAIL_SO
        }
    }

    /**
     * 创建 ApplicationLike 实例
     */
    private fun createApplicationLike(context: Context, className: String) {
        try {
            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(Context::class.java)
            val instance = constructor.newInstance(context)

            if (context is ForgeApplication) {
                context.setApplicationLike(instance)
            }

            Logger.i(TAG, "Create ApplicationLike success: $className")
        } catch (e: Exception) {
            Logger.e(TAG, "Create ApplicationLike failed: $className", e)
        }
    }

    /**
     * 处理加载失败
     */
    private fun handleLoadFailed(context: Context) {
        Logger.w(TAG, "Handle load failed, try to rollback")

        // 标记加载失败
        DataStorage.putBoolean(KEY_LOAD_SUCCESS, false)

        // 尝试回滚到上一个版本
        val lastVersion = DataStorage.getString(KEY_LAST_VERSION)
        val lastApkPath = DataStorage.getString(KEY_LAST_APK_PATH)

        if (lastVersion != null && lastApkPath != null) {
            Logger.i(TAG, "Rollback to last version: $lastVersion")
            DataStorage.putString(KEY_CURRENT_VERSION, lastVersion)
            DataStorage.putString(KEY_CURRENT_APK_PATH, lastApkPath)
        }
    }

    /**
     * 获取当前版本
     */
    fun getCurrentVersion(): String? {
        return DataStorage.getString(KEY_CURRENT_VERSION)
    }

    /**
     * 是否已加载
     */
    fun isLoaded(): Boolean = isLoaded
}
