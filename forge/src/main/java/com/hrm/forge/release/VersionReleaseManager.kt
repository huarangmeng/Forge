package com.hrm.forge.release

import android.content.Context
import com.hrm.forge.VersionStateManager
import com.hrm.forge.common.ApkUtils
import com.hrm.forge.common.Constants
import com.hrm.forge.common.FileUtil
import com.hrm.forge.common.UnZipUtils
import com.hrm.forge.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 版本发布管理器
 *
 * 负责新版本 APK 的发布流程：
 * - APK 验证（包名 + 签名）
 * - APK 安装（复制到版本目录）
 * - APK 优化（DEX 预加载、解压）
 * - 旧版本清理
 */
object VersionReleaseManager {
    private const val TAG = "VersionReleaseManager"

    /**
     * 发布新版本 APK
     *
     * @param context Context
     * @param apkFile 新版本 APK 文件
     * @param version 版本号
     * @return 是否成功
     */
    suspend fun releaseNewVersion(context: Context, apkFile: File, version: String): Boolean {
        return withContext(Dispatchers.IO) {
            Logger.i(TAG, "Start release new version: $version, path=${apkFile.absolutePath}")

            val startTime = System.currentTimeMillis()

            try {
                // 1. 验证 APK
                if (!validateApk(context, apkFile)) {
                    return@withContext false
                }

                // 2. 获取 APK 版本码
                val versionCode = ApkUtils.getVersionCode(context, apkFile.absolutePath)
                if (versionCode <= 0) {
                    Logger.e(TAG, "Invalid version code: $versionCode")
                    return@withContext false
                }

                // 3. 安装 APK（复制到版本目录）
                val (destApkFile, sha1) = installApk(context, apkFile, version)
                    ?: return@withContext false

                // 4. 优化 APK（DEX 预加载、解压）
                optimizeApk(context, destApkFile)

                // 5. 保存版本信息
                VersionStateManager.saveNewVersion(
                    context = context,
                    version = version,
                    versionCode = versionCode,
                    apkPath = destApkFile.absolutePath,
                    sha1 = sha1
                )

                // 6. 清理旧版本
                cleanOldVersions(context, version)

                val elapsed = System.currentTimeMillis() - startTime
                Logger.i(TAG, "✅ Release new version success, elapsed: ${elapsed}ms")
                Logger.i(TAG, "⚠️ Please restart the app to apply changes")

                true
            } catch (e: IOException) {
                Logger.e(TAG, "Release new version failed: IO error", e)
                false
            } catch (e: SecurityException) {
                Logger.e(TAG, "Release new version failed: Security error", e)
                false
            } catch (e: Exception) {
                Logger.e(TAG, "Release new version failed: Unexpected error", e)
                false
            }
        }
    }

    /**
     * 验证 APK 文件
     * 检查文件存在性、包名和签名
     */
    private fun validateApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists: ${apkFile.absolutePath}")
            return false
        }

        if (!ApkUtils.isValidApk(context, apkFile)) {
            Logger.e(TAG, "Invalid APK (package name or signature mismatch): ${apkFile.absolutePath}")
            return false
        }

        Logger.i(TAG, "✓ APK validation passed")
        return true
    }

    /**
     * 安装 APK（复制到版本目录）
     *
     * @return Pair<APK文件, SHA1> 如果失败返回 null
     */
    @Throws(IOException::class)
    private fun installApk(context: Context, apkFile: File, version: String): Pair<File, String>? {
        // 创建版本目录（以版本号命名）
        val versionDir = VersionStateManager.getVersionDir(context, version)
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

        Logger.i(TAG, "✓ APK copied to: ${destApkFile.absolutePath}")

        // 计算 SHA1 校验值
        val sha1 = FileUtil.getFileSHA1(destApkFile)
        if (sha1 == null) {
            Logger.e(TAG, "Calculate SHA1 failed")
            return null
        }

        Logger.i(TAG, "✓ APK SHA1: $sha1")
        
        return Pair(destApkFile, sha1)
    }

    /**
     * 优化 APK
     * - 解压 APK（可选）
     * - 预加载 DEX（优化首次启动速度）
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
            preloadDex(context, apkFile)
            Logger.i(TAG, "✓ DEX preloaded")
        } catch (e: Exception) {
            Logger.w(TAG, "Optimization failed, but continue", e)
        }
    }

    /**
     * 预加载 DEX（优化首次启动速度）
     * 使用 DexClassLoader 触发系统的 dex2oat 编译
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
            val versionsDir = File(context.filesDir, "${Constants.DIR_FORGE}/${Constants.DIR_VERSIONS}")
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
                    FileUtil.deleteRecursively(dir)
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
