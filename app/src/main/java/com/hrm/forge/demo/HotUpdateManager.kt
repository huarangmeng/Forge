package com.hrm.forge.demo

import android.content.Context
import android.util.Log
import com.hrm.forge.Forge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 热更新管理器
 * 封装了热更新相关的操作
 */
class HotUpdateManager(val context: Context) {
    
    companion object {
        private const val TAG = "HotUpdateManager"
    }
    
    /**
     * 发布新版本
     * @param apkFilePath APK 文件路径
     * @param version 版本号
     * @param callback 回调
     */
    fun releaseNewVersion(
        apkFilePath: String,
        version: String,
        callback: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.i(TAG, "Start release new version: $version")
                
                val apkFile = File(apkFilePath)
                if (!apkFile.exists()) {
                    val msg = "APK file not exists: $apkFilePath"
                    Log.e(TAG, msg)
                    callback(false, msg)
                    return@launch
                }
                
                // 发布新版本
                val success = Forge.releaseNewApk(
                    context = context,
                    apkFile = apkFile,
                    version = version
                )
                
                if (success) {
                    val msg = "Release success! Please restart the app."
                    Log.i(TAG, msg)
                    callback(true, msg)
                } else {
                    val msg = "Release failed!"
                    Log.e(TAG, msg)
                    callback(false, msg)
                }
                
            } catch (e: Exception) {
                val msg = "Release exception: ${e.message}"
                Log.e(TAG, msg, e)
                callback(false, msg)
            }
        }
    }
    
    /**
     * 从 URL 下载并发布新版本
     * @param downloadUrl 下载地址
     * @param version 版本号
     * @param onProgress 下载进度回调
     * @param callback 完成回调
     */
    fun downloadAndRelease(
        downloadUrl: String,
        version: String,
        onProgress: (Int) -> Unit,
        callback: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Start download APK from: $downloadUrl")
                
                // 1. 下载 APK
                onProgress(0)
                val apkFile = downloadApk(downloadUrl) { progress ->
                    onProgress(progress)
                }
                
                if (apkFile == null) {
                    withContext(Dispatchers.Main) {
                        callback(false, "Download failed")
                    }
                    return@launch
                }
                
                Log.i(TAG, "Download success: ${apkFile.absolutePath}")
                onProgress(100)
                
                // 2. 发布新版本
                val success = Forge.releaseNewApk(
                    context = context,
                    apkFile = apkFile,
                    version = version
                )
                
                // 3. 切换到主线程回调
                withContext(Dispatchers.Main) {
                    if (success) {
                        callback(true, "Release success! Please restart the app.")
                    } else {
                        callback(false, "Release failed!")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download and release exception", e)
                withContext(Dispatchers.Main) {
                    callback(false, "Error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 回滚到上一个版本
     */
    fun rollbackToLastVersion(callback: (Boolean, String) -> Unit) {
        try {
            Log.i(TAG, "Start rollback to last version")
            
            val success = Forge.rollbackToLastVersion(context)
            if (success) {
                val msg = "Rollback success! Please restart the app."
                Log.i(TAG, msg)
                callback(true, msg)
            } else {
                val msg = "Rollback failed! No last version available."
                Log.e(TAG, msg)
                callback(false, msg)
            }
        } catch (e: Exception) {
            val msg = "Rollback exception: ${e.message}"
            Log.e(TAG, msg, e)
            callback(false, msg)
        }
    }
    
    /**
     * 清理上一个版本
     */
    fun cleanLastVersion(callback: (Boolean, String) -> Unit) {
        try {
            Log.i(TAG, "Start clean last version")
            
            val success = Forge.cleanLastVersion(context)
            if (success) {
                val msg = "Clean success!"
                Log.i(TAG, msg)
                callback(true, msg)
            } else {
                val msg = "Clean failed!"
                Log.e(TAG, msg)
                callback(false, msg)
            }
        } catch (e: Exception) {
            val msg = "Clean exception: ${e.message}"
            Log.e(TAG, msg, e)
            callback(false, msg)
        }
    }
    
    /**
     * 获取版本信息
     */
    fun getVersionInfo(): Forge.VersionInfo {
        return Forge.getCurrentVersionInfo(context)
    }
    
    /**
     * 测试启动 Service
     * @param serviceClassName Service 完整类名
     */
    fun testStartService(serviceClassName: String): Boolean {
        return HotUpdateTester.testStartService(context, serviceClassName)
    }
    
    /**
     * 测试停止 Service
     * @param serviceClassName Service 完整类名
     */
    fun testStopService(serviceClassName: String): Boolean {
        return HotUpdateTester.testStopService(context, serviceClassName)
    }
    
    /**
     * 测试启动 Activity
     * @param activityClassName Activity 完整类名
     */
    fun testLaunchActivity(activityClassName: String): Boolean {
        return HotUpdateTester.testLaunchActivity(context, activityClassName)
    }
    
    /**
     * 从 Assets 加载 APK 并发布
     * @param assetFileName Assets 中的 APK 文件名（例如：app-debug.apk）
     * @param callback 回调
     */
    fun releaseFromAssets(
        assetFileName: String,
        callback: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Start copy APK from assets: $assetFileName")
                
                // 1. 从 assets 复制到缓存目录
                val destFile = File(context.cacheDir, assetFileName)
                
                context.assets.open(assetFileName).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (!destFile.exists() || destFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        val msg = "Copy from assets failed: $assetFileName"
                        Log.e(TAG, msg)
                        callback(false, msg)
                    }
                    return@launch
                }
                
                Log.i(TAG, "Copy success: ${destFile.absolutePath}, size: ${destFile.length()}")
                
                // 2. 从 APK 中读取版本信息
                val versionName = com.hrm.forge.common.ApkUtils.getVersionName(
                    context,
                    destFile.absolutePath
                )
                
                if (versionName.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        val msg = "Failed to read version from APK: $assetFileName"
                        Log.e(TAG, msg)
                        callback(false, msg)
                    }
                    return@launch
                }
                
                Log.i(TAG, "APK version: $versionName")
                
                // 3. 发布新版本
                val success = Forge.releaseNewApk(
                    context = context,
                    apkFile = destFile,
                    version = versionName
                )
                
                // 4. 切换到主线程回调
                withContext(Dispatchers.Main) {
                    if (success) {
                        val msg = "Release success! Version: $versionName. Please restart the app."
                        Log.i(TAG, msg)
                        callback(true, msg)
                    } else {
                        val msg = "Release failed!"
                        Log.e(TAG, msg)
                        callback(false, msg)
                    }
                }
                
            } catch (e: Exception) {
                val msg = "Release from assets exception: ${e.message}"
                Log.e(TAG, msg, e)
                withContext(Dispatchers.Main) {
                    callback(false, msg)
                }
            }
        }
    }
    
    /**
     * 下载 APK
     * @param url 下载地址
     * @param onProgress 进度回调
     * @return APK 文件
     */
    private suspend fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: 实际的下载逻辑
                // 这里需要使用 OkHttp 或其他网络库实现
                // 示例代码：
                /*
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext null
                }
                
                val destFile = File(context.cacheDir, "update.apk")
                val contentLength = response.body?.contentLength() ?: 0
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var len: Int
                        
                        while (input.read(buffer).also { len = it } != -1) {
                            output.write(buffer, 0, len)
                            bytesRead += len
                            
                            if (contentLength > 0) {
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                
                destFile
                */
                
                // 临时返回 null，需要实现实际的下载逻辑
                Log.w(TAG, "Download not implemented yet")
                null
                
            } catch (e: Exception) {
                Log.e(TAG, "Download exception", e)
                null
            }
        }
    }
}
