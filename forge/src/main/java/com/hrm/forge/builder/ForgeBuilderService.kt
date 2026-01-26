package com.hrm.forge.builder

import android.content.Context
import com.hrm.forge.common.ApkUtils
import com.hrm.forge.common.Constants
import com.hrm.forge.common.DataSavingUtils
import com.hrm.forge.common.FileUtil
import com.hrm.forge.common.UnZipUtils
import com.hrm.forge.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Forge 构建服务
 *
 * 负责新版本 APK 的验证、解压和管理
 *
 * 核心功能：
 * - APK 验证（包名 + 签名）
 * - 版本管理和回滚
 * - 旧版本清理
 * - DEX 预加载优化
 */
object ForgeBuilderService {
    private const val TAG = "ForgeBuilderService"

    // 存储键（使用 Constants 统一管理）
    private const val KEY_CURRENT_APK_PATH = "forge_current_apk_path"
    private const val KEY_PREVIOUS_VERSION = "forge_previous_version"
    private const val KEY_PREVIOUS_APK_PATH = "forge_previous_apk_path"
    private const val KEY_BUILD_NUMBER = "forge_build_number"

    /**
     * 发布新版本 APK
     *
     * @param context Context
     * @param apkFile 新版本 APK 文件
     * @param version 版本号
     * @return 是否成功
     * @throws IOException 当文件操作失败时
     */
    suspend fun releaseNewApk(context: Context, apkFile: File, version: String): Boolean {
        return withContext(Dispatchers.IO) {
            Logger.i(TAG, "Start release new APK: version=$version, path=${apkFile.absolutePath}")

            val startTime = System.currentTimeMillis()

            try {
                // 1. 验证 APK
                if (!validateApk(context, apkFile)) {
                    return@withContext false
                }

                // 2. 备份当前版本
                backupCurrentVersion()

                // 3. 安装新 APK
                val destApkFile = installNewApk(context, apkFile, version)
                    ?: return@withContext false

                // 4. 预加载和优化
                performOptimizations(context, destApkFile)

                // 5. 保存版本信息
                saveVersionInfo(context, version, destApkFile)

                // 6. 清理旧版本
                cleanOldVersions(context, version)

                val elapsed = System.currentTimeMillis() - startTime
                Logger.i(TAG, "Release new APK success, elapsed: ${elapsed}ms")

                true
            } catch (e: IOException) {
                Logger.e(TAG, "Release new APK failed: IO error", e)
                false
            } catch (e: SecurityException) {
                Logger.e(TAG, "Release new APK failed: Security error", e)
                false
            } catch (e: Exception) {
                Logger.e(TAG, "Release new APK failed: Unexpected error", e)
                false
            }
        }
    }

    /**
     * 验证 APK 文件
     */
    private fun validateApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return false
        }

        if (!ApkUtils.isValidApk(context, apkFile)) {
            Logger.e(TAG, "Invalid APK file (包名或签名验证失败): ${apkFile.absolutePath}")
            return false
        }

        return true
    }

    /**
     * 备份当前版本信息
     */
    private fun backupCurrentVersion() {
        val currentVersion = DataSavingUtils.getString(Constants.KEY_CURRENT_VERSION)
        val currentApkPath = DataSavingUtils.getString(KEY_CURRENT_APK_PATH)

        if (currentVersion != null && currentApkPath != null) {
            DataSavingUtils.putString(KEY_PREVIOUS_VERSION, currentVersion)
            DataSavingUtils.putString(KEY_PREVIOUS_APK_PATH, currentApkPath)
            Logger.i(TAG, "Backed up current version: $currentVersion")
        }
    }

    /**
     * 安装新 APK
     *
     * @return 安装后的 APK 文件，如果失败返回 null
     */
    @Throws(IOException::class)
    private fun installNewApk(context: Context, apkFile: File, version: String): File? {
        // 创建版本目录
        val versionDir =
            File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}/$version")
        if (!FileUtil.ensureDir(versionDir)) {
            Logger.e(TAG, "Cannot create version dir: ${versionDir.absolutePath}")
            return null
        }

        // 复制 APK 到版本目录
        val destApkFile = File(versionDir, "base.apk")
        if (!FileUtil.copyFile(apkFile, destApkFile)) {
            Logger.e(TAG, "Copy APK failed")
            return null
        }

        Logger.i(TAG, "APK copied to: ${destApkFile.absolutePath}")

        // 计算 SHA1
        val sha1 = FileUtil.getFileSHA1(destApkFile)
        if (sha1 == null) {
            Logger.e(TAG, "Calculate SHA1 failed")
            return null
        }

        Logger.i(TAG, "APK SHA1: $sha1")
        DataSavingUtils.putString("${Constants.KEY_VERSION_SHA1}_$version", sha1)

        return destApkFile
    }

    /**
     * 执行优化操作
     */
    private fun performOptimizations(context: Context, apkFile: File) {
        try {
            // 解压 APK（可选，用于预加载优化）
            val unzipDir = File(apkFile.parentFile, "unzip")
            if (!UnZipUtils.unzipApk(apkFile, unzipDir)) {
                Logger.w(TAG, "Unzip APK failed, but continue")
            }

            // 预加载 DEX（优化首次启动）
            preloadDex(context, apkFile)
        } catch (e: Exception) {
            Logger.w(TAG, "Optimization failed, but continue", e)
        }
    }

    /**
     * 保存版本信息
     */
    private fun saveVersionInfo(context: Context, version: String, apkFile: File) {
        DataSavingUtils.putString(Constants.KEY_CURRENT_VERSION, version)
        DataSavingUtils.putString(KEY_CURRENT_APK_PATH, apkFile.absolutePath)

        val buildNumber = ApkUtils.getVersionCode(context, apkFile.absolutePath)
        DataSavingUtils.putLong(KEY_BUILD_NUMBER, buildNumber)

        Logger.i(TAG, "Saved version info: $version, buildNumber=$buildNumber")
    }

    /**
     * 预加载 DEX（优化首次启动速度）
     */
    @Throws(Exception::class)
    private fun preloadDex(context: Context, apkFile: File) {
        Logger.i(TAG, "Start preload DEX")

        val dexDir = File(context.filesDir, "forge_dex_preload")
        dexDir.mkdirs()

        val optimizedDir = File(dexDir, "oat")
        optimizedDir.mkdirs()

        // 使用 DexClassLoader 触发 dex2oat 编译
        dalvik.system.DexClassLoader(
            apkFile.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )

        Logger.i(TAG, "Preload DEX completed")
    }

    /**
     * 清理上一个版本
     */
    fun cleanPreviousVersion(context: Context): Boolean {
        Logger.i(TAG, "Start clean previous version")

        try {
            val previousVersion = DataSavingUtils.getString(KEY_PREVIOUS_VERSION)

            if (previousVersion != null) {
                val versionDir = File(
                    context.filesDir,
                    "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}/$previousVersion"
                )
                if (versionDir.exists()) {
                    FileUtil.deleteRecursively(versionDir)
                    Logger.i(TAG, "Deleted previous version dir: ${versionDir.absolutePath}")
                }

                // 清理存储的信息
                DataSavingUtils.remove(KEY_PREVIOUS_VERSION)
                DataSavingUtils.remove(KEY_PREVIOUS_APK_PATH)
                DataSavingUtils.remove("${Constants.KEY_VERSION_SHA1}_$previousVersion")
            }

            Logger.i(TAG, "Clean previous version success")
            return true
        } catch (e: IOException) {
            Logger.e(TAG, "Clean previous version failed: IO error", e)
            return false
        } catch (e: Exception) {
            Logger.e(TAG, "Clean previous version failed: Unexpected error", e)
            return false
        }
    }

    /**
     * 清理旧版本（保留最近 N 个版本）
     */
    private fun cleanOldVersions(context: Context, currentVersion: String) {
        Logger.i(TAG, "Start clean old versions")

        try {
            val versionsDir =
                File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}")
            if (!versionsDir.exists() || !versionsDir.isDirectory) {
                return
            }

            val versionDirs = versionsDir.listFiles()?.filter { it.isDirectory } ?: return

            // 按修改时间排序，保留最新的 N 个版本
            val sortedDirs = versionDirs.sortedByDescending { it.lastModified() }

            val previousVersion = DataSavingUtils.getString(KEY_PREVIOUS_VERSION)

            sortedDirs.drop(Constants.MAX_VERSION_RETENTION).forEach { dir ->
                // 不要删除标记的上一个版本和当前版本
                if (dir.name != previousVersion && dir.name != currentVersion) {
                    FileUtil.deleteRecursively(dir)
                    Logger.i(TAG, "Deleted old version: ${dir.name}")

                    // 清理存储的信息
                    DataSavingUtils.remove("${Constants.KEY_VERSION_SHA1}_${dir.name}")
                }
            }

            Logger.i(TAG, "Clean old versions completed")
        } catch (e: IOException) {
            Logger.e(TAG, "Clean old versions failed: IO error", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Clean old versions failed: Unexpected error", e)
        }
    }

    /**
     * 回滚到上一个版本
     */
    fun rollbackToPreviousVersion(context: Context): Boolean {
        Logger.i(TAG, "Start rollback to previous version")

        try {
            val previousVersion = DataSavingUtils.getString(KEY_PREVIOUS_VERSION)
            val previousApkPath = DataSavingUtils.getString(KEY_PREVIOUS_APK_PATH)

            if (previousVersion == null || previousApkPath == null) {
                Logger.e(TAG, "No previous version to rollback")
                return false
            }

            val previousApkFile = File(previousApkPath)
            if (!previousApkFile.exists()) {
                Logger.e(TAG, "Previous version APK not exists: $previousApkPath")
                return false
            }

            // 保存当前版本为上一个版本
            val currentVersion = DataSavingUtils.getString(Constants.KEY_CURRENT_VERSION)
            val currentApkPath = DataSavingUtils.getString(KEY_CURRENT_APK_PATH)

            // 切换到上一个版本
            DataSavingUtils.putString(Constants.KEY_CURRENT_VERSION, previousVersion)
            DataSavingUtils.putString(KEY_CURRENT_APK_PATH, previousApkPath)

            // 更新上一个版本为之前的当前版本
            if (currentVersion != null && currentApkPath != null) {
                DataSavingUtils.putString(KEY_PREVIOUS_VERSION, currentVersion)
                DataSavingUtils.putString(KEY_PREVIOUS_APK_PATH, currentApkPath)
            }

            Logger.i(TAG, "Rollback to previous version success: $previousVersion")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Rollback to previous version failed", e)
            return false
        }
    }

    /**
     * 获取当前版本信息
     */
    fun getCurrentVersionInfo(context: Context): VersionInfo? {
        val version = DataSavingUtils.getString(Constants.KEY_CURRENT_VERSION) ?: return null
        val apkPath = DataSavingUtils.getString(KEY_CURRENT_APK_PATH) ?: return null
        val buildNumber = DataSavingUtils.getLong(KEY_BUILD_NUMBER, 0L)
        val sha1 = DataSavingUtils.getString("${Constants.KEY_VERSION_SHA1}_$version")

        return VersionInfo(version, apkPath, buildNumber, sha1)
    }

    /**
     * 版本信息数据类
     */
    data class VersionInfo(
        val version: String,
        val apkPath: String,
        val buildNumber: Long,
        val sha1: String?
    )
}
