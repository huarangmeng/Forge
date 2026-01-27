package com.hrm.forge.internal.release

import android.content.Context
import com.hrm.forge.api.ReleaseResult
import com.hrm.forge.internal.log.Logger
import com.hrm.forge.internal.state.VersionStateManager
import com.hrm.forge.internal.util.ApkUtils
import com.hrm.forge.internal.util.Constants
import com.hrm.forge.internal.util.FileUtils
import com.hrm.forge.internal.util.UnZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 版本发布管理器（内部实现）
 *
 * 负责新版本 APK 的发布流程：
 * - APK 验证（包名 + 签名）
 * - APK 安装（复制到版本目录）
 * - APK 优化（DEX 预加载、解压）
 * - 旧版本清理
 *
 * @hide 此类仅供内部使用，不对外暴露
 */
internal object VersionReleaseManager {
    private const val TAG = "VersionReleaseManager"

    /**
     * 发布新版本 APK（自动从 APK 读取版本号）
     *
     * @param context Context
     * @param apkFile 新版本 APK 文件
     * @return 发布结果
     */
    suspend fun releaseNewVersion(context: Context, apkFile: File): ReleaseResult {
        // 从 APK 读取版本号
        val version = ApkUtils.getVersionName(context, apkFile.absolutePath)
        if (version.isNullOrEmpty()) {
            Logger.e(TAG, "Cannot read version from APK: ${apkFile.absolutePath}")
            return ReleaseResult.APK_READ_VERSION_FAILED
        }

        return releaseNewVersion(context, apkFile, version)
    }

    /**
     * 发布新版本 APK（指定版本号）
     *
     * @param context Context
     * @param apkFile 新版本 APK 文件
     * @param version 版本号
     * @return 发布结果
     */
    internal suspend fun releaseNewVersion(
        context: Context,
        apkFile: File,
        version: String
    ): ReleaseResult {
        return withContext(Dispatchers.IO) {
            Logger.i(TAG, "Start release new version: $version, path=${apkFile.absolutePath}")

            val startTime = System.currentTimeMillis()

            try {
                // 1. 验证 APK
                val validateResult = validateApk(context, apkFile)
                if (validateResult != ReleaseResult.SUCCESS) {
                    return@withContext validateResult
                }

                // 2. 获取 APK 版本码
                val versionCode = ApkUtils.getVersionCode(context, apkFile.absolutePath)
                if (versionCode <= 0) {
                    Logger.e(TAG, "Invalid version code: $versionCode")
                    return@withContext ReleaseResult.APK_VERSION_CODE_INVALID
                }

                // 3. 检查是否已安装相同版本（避免浪费资源）
                val currentVersionState = VersionStateManager.getVersionState()
                if (currentVersionState.hasHotUpdate &&
                    currentVersionState.currentVersion == version &&
                    currentVersionState.currentVersionCode == versionCode
                ) {
                    Logger.i(TAG, "✓ Same version already installed: $version ($versionCode)")
                    Logger.i(TAG, "⚠️ Skip installation to save resources")
                    return@withContext ReleaseResult.SUCCESS
                }

                // 4. 安装 APK（复制到版本目录)
                val installResult = installApk(context, apkFile, version)
                if (installResult.first != ReleaseResult.SUCCESS) {
                    return@withContext installResult.first
                }
                val (_, destApkFile, sha1) = installResult

                // 5. 优化 APK（DEX 预加载、解压）
                optimizeApk(context, destApkFile)

                // 6. 保存版本信息
                val saved = VersionStateManager.saveNewVersion(
                    version = version,
                    versionCode = versionCode,
                    apkPath = destApkFile.absolutePath,
                    sha1 = sha1
                )

                if (!saved) {
                    Logger.e(TAG, "Save version state failed")
                    return@withContext ReleaseResult.SAVE_VERSION_STATE_FAILED
                }

                // 7. 清理旧版本
                cleanOldVersions(context, version)

                val elapsed = System.currentTimeMillis() - startTime
                Logger.i(TAG, "✅ Release new version success, elapsed: ${elapsed}ms")
                Logger.i(TAG, "⚠️ Please restart the app to apply changes")

                ReleaseResult.SUCCESS
            } catch (e: IOException) {
                Logger.e(TAG, "Release new version failed: IO error", e)
                ReleaseResult.UNKNOWN_ERROR
            } catch (e: SecurityException) {
                Logger.e(TAG, "Release new version failed: Security error", e)
                ReleaseResult.UNKNOWN_ERROR
            } catch (e: Exception) {
                Logger.e(TAG, "Release new version failed: Unexpected error", e)
                ReleaseResult.UNKNOWN_ERROR
            }
        }
    }

    /**
     * 验证 APK 文件
     * 检查文件存在性、包名和签名
     *
     * @return 验证结果
     */
    private fun validateApk(context: Context, apkFile: File): ReleaseResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return ReleaseResult.APK_NOT_FOUND
        }

        if (!ApkUtils.isValidApk(context, apkFile)) {
            Logger.e(
                TAG,
                "Invalid APK (package name or signature mismatch): ${apkFile.absolutePath}"
            )
            return ReleaseResult.APK_PACKAGE_MISMATCH
        }

        Logger.i(TAG, "✓ APK validation passed")
        return ReleaseResult.SUCCESS
    }

    /**
     * 安装 APK（复制到版本目录）
     *
     * @return Triple<结果, APK文件, SHA1>
     */
    @Throws(IOException::class)
    private fun installApk(
        context: Context,
        apkFile: File,
        version: String
    ): Triple<ReleaseResult, File, String> {
        // 创建版本目录（以版本号命名）
        val versionDir = VersionStateManager.getVersionDir(context, version)
        if (!FileUtils.ensureDir(versionDir)) {
            Logger.e(TAG, "Cannot create version dir: ${versionDir.absolutePath}")
            return Triple(ReleaseResult.INSTALL_CREATE_DIR_FAILED, File(""), "")
        }

        // 复制 APK 到版本目录
        val destApkFile = File(versionDir, "base.apk")
        if (!FileUtils.copyFile(apkFile, destApkFile)) {
            Logger.e(TAG, "Copy APK failed")
            return Triple(ReleaseResult.INSTALL_COPY_APK_FAILED, File(""), "")
        }

        Logger.i(TAG, "✓ APK copied to: ${destApkFile.absolutePath}")

        // 计算 SHA1 校验值
        val sha1 = FileUtils.getFileSHA1(destApkFile)
        if (sha1 == null) {
            Logger.e(TAG, "Calculate SHA1 failed")
            return Triple(ReleaseResult.INSTALL_CALCULATE_SHA1_FAILED, File(""), "")
        }

        Logger.i(TAG, "✓ APK SHA1: $sha1")

        return Triple(ReleaseResult.SUCCESS, destApkFile, sha1)
    }

    /**
     * 优化 APK
     * - 解压 APK（可选）
     * - 预加载 DEX（优化首次启动速度，仅 Android 8.0 以下）
     */
    private fun optimizeApk(context: Context, apkFile: File) {
        try {
            // 解压 APK（可选，用于预加载优化）
            val unzipDir = File(apkFile.parentFile, "unzip")
            if (!UnZipUtils.unzipApk(apkFile, unzipDir)) {
                Logger.w(TAG, "Unzip APK failed, but continue")
            } else {
                Logger.i(TAG, "✓ APK unzipped")
            }

            // 预加载 DEX（触发 dex2oat 编译）
            // 注意：Android 8.0+ 不允许从可写目录加载 DEX，跳过预加载
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                preloadDex(context, apkFile)
                Logger.i(TAG, "✓ DEX preloaded")
            } else {
                Logger.i(
                    TAG,
                    "✓ DEX preload skipped (Android 8.0+, system will optimize automatically)"
                )
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Optimization failed, but continue", e)
        }
    }

    /**
     * 预加载 DEX（优化首次启动速度）
     * 使用 DexClassLoader 触发系统的 dex2oat 编译
     *
     * 注意：此方法仅在 Android 8.0 以下使用
     * Android 8.0+ 会在后台自动优化 DEX
     */
    @Throws(Exception::class)
    private fun preloadDex(context: Context, apkFile: File) {
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
    }

    /**
     * 清理旧版本（保留最近 N 个版本）
     *
     * 策略：
     * - 按修改时间排序
     * - 保留最新的 N 个版本
     * - 不删除当前版本和上一个版本（用于回滚）
     */
    private fun cleanOldVersions(context: Context, currentVersion: String) {
        Logger.i(TAG, "Start clean old versions")

        try {
            val versionsDir =
                File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}")
            if (!versionsDir.exists() || !versionsDir.isDirectory) {
                Logger.i(TAG, "Versions directory not exists")
                return
            }

            val versionDirs = versionsDir.listFiles()?.filter { it.isDirectory } ?: return
            if (versionDirs.isEmpty()) {
                Logger.i(TAG, "No version directories to clean")
                return
            }

            // 按修改时间排序，保留最新的 N 个版本
            val sortedDirs = versionDirs.sortedByDescending { it.lastModified() }
            val versionState = VersionStateManager.getVersionState()

            var cleanedCount = 0
            sortedDirs.drop(Constants.MAX_VERSION_RETENTION).forEach { dir ->
                // 不要删除当前版本和上一个版本
                if (dir.name != currentVersion && dir.name != versionState.previousVersion) {
                    FileUtils.deleteRecursively(dir)
                    Logger.i(TAG, "  Deleted old version: ${dir.name}")
                    cleanedCount++
                }
            }

            if (cleanedCount > 0) {
                Logger.i(TAG, "✓ Cleaned $cleanedCount old version(s)")
            } else {
                Logger.i(TAG, "✓ No old versions to clean")
            }
        } catch (e: IOException) {
            Logger.e(TAG, "Clean old versions failed: IO error", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Clean old versions failed: Unexpected error", e)
        }
    }
}
