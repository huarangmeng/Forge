package com.hrm.forge.common

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.hrm.forge.logger.Logger
import java.io.File

/**
 * APK 工具类
 * 
 * 提供 APK 文件的信息获取和验证功能
 */
object ApkUtils {
    private const val TAG = "ApkUtils"
    
    /**
     * 获取 PackageInfo 的兼容性版本号
     */
    private fun PackageInfo.getVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }
    
    /**
     * 获取 APK 的 target SDK version
     */
    fun getTargetSdkVersion(context: Context, apkPath: String): Int {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
            packageInfo?.applicationInfo?.targetSdkVersion ?: 0
        } catch (e: Exception) {
            Logger.e(TAG, "Get target SDK version failed: $apkPath", e)
            0
        }
    }
    
    /**
     * 获取 APK 的包名
     */
    fun getPackageName(context: Context, apkPath: String): String? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
            packageInfo?.packageName
        } catch (e: Exception) {
            Logger.e(TAG, "Get package name failed: $apkPath", e)
            null
        }
    }
    
    /**
     * 获取 APK 的版本号
     */
    fun getVersionCode(context: Context, apkPath: String): Long {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
            packageInfo?.getVersionCodeCompat() ?: 0L
        } catch (e: Exception) {
            Logger.e(TAG, "Get version code failed: $apkPath", e)
            0L
        }
    }
    
    /**
     * 获取 APK 的版本名称
     */
    fun getVersionName(context: Context, apkPath: String): String? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
            packageInfo?.versionName
        } catch (e: Exception) {
            Logger.e(TAG, "Get version name failed: $apkPath", e)
            null
        }
    }
    
    /**
     * 验证 APK 签名是否与当前应用一致
     * 
     * @param context 应用上下文
     * @param apkFile APK 文件
     * @return 签名是否一致
     */
    @Suppress("DEPRECATION")
    fun verifyApkSignature(context: Context, apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            
            // 获取 APK 签名信息
            val apkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            // 获取已安装应用的签名信息
            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            if (apkInfo == null) {
                Logger.e(TAG, "Cannot get APK package info")
                return false
            }
            
            // 比较签名
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val apkSignatures = apkInfo.signingInfo?.apkContentsSigners
                val installedSignatures = installedInfo.signingInfo?.apkContentsSigners
                apkSignatures != null && installedSignatures != null &&
                    apkSignatures.contentEquals(installedSignatures)
            } else {
                val apkSignatures = apkInfo.signatures
                val installedSignatures = installedInfo.signatures
                apkSignatures != null && installedSignatures != null &&
                    apkSignatures.contentEquals(installedSignatures)
            }
            
            if (!result) {
                Logger.e(TAG, "APK signature mismatch!")
            }
            
            result
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.e(TAG, "Package not found when verifying signature", e)
            false
        } catch (e: Exception) {
            Logger.e(TAG, "Verify APK signature failed: ${apkFile.absolutePath}", e)
            false
        }
    }
    
    /**
     * 检查 APK 是否有效（包名匹配 + 签名验证）
     */
    fun isValidApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) {
            Logger.e(TAG, "APK file not exists")
            return false
        }
        
        return try {
            // 1. 检查包名是否匹配
            val packageName = getPackageName(context, apkFile.absolutePath)
            if (packageName.isNullOrEmpty()) {
                Logger.e(TAG, "APK has no package name")
                return false
            }
            
            if (packageName != context.packageName) {
                Logger.e(TAG, "Package name mismatch: expected=${context.packageName}, actual=$packageName")
                return false
            }
            
            // 2. 验证签名
            if (!verifyApkSignature(context, apkFile)) {
                Logger.e(TAG, "APK signature verification failed")
                return false
            }
            
            Logger.i(TAG, "APK validation passed: ${apkFile.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Check APK validity failed: ${apkFile.absolutePath}", e)
            false
        }
    }
}
