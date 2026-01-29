package com.hrm.forge.internal.loader

import android.content.Context
import android.os.Build
import com.hrm.forge.internal.util.Constants
import com.hrm.forge.internal.util.FileUtils
import com.hrm.forge.internal.util.ReflectUtil
import com.hrm.forge.internal.log.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

/**
 * SO 库动态加载器
 * 
 * 仅支持 arm64-v8a 架构（ARM 64位）
 */
internal object NativeLibraryLoader {
    private const val TAG = "ForgeLoadLibrary"
    
    /**
     * 检查当前设备是否支持 arm64-v8a
     */
    private fun isSupportedDevice(): Boolean {
        val deviceAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { it.isNotEmpty() }
        }
        
        Logger.d(TAG, "Device ABIs: ${deviceAbis.joinToString()}")
        
        val isSupported = deviceAbis.contains(Constants.SUPPORTED_ABI)
        if (isSupported) {
            Logger.i(TAG, "Device supports ${Constants.SUPPORTED_ABI}")
        } else {
            Logger.e(TAG, "Device does not support ${Constants.SUPPORTED_ABI}")
        }
        
        return isSupported
    }
    
    /**
     * 安装 Native 库
     * @param context Context
     * @param apkFile APK 文件
     * @param nativeLibraryDir 原始 Native 库目录
     * @throws IOException 当文件操作失败时抛出
     * @throws ReflectiveOperationException 当反射操作失败时抛出
     */
    @Throws(IOException::class, ReflectiveOperationException::class)
    fun installNativeLibrary(context: Context, apkFile: File, nativeLibraryDir: String) {
        Logger.i(TAG, "Start install native library from: ${apkFile.absolutePath}")
        
        val startTime = System.currentTimeMillis()
        
        try {
            // 检测设备是否支持 arm64-v8a
            if (!isSupportedDevice()) {
                Logger.e(TAG, "Device does not support arm64-v8a architecture")
                return
            }
            
            // 创建 SO 输出目录
            val soDir = File(context.filesDir, "forge_so")
            if (!soDir.exists()) {
                soDir.mkdirs()
            }
            
            // 提取 SO 文件
            val soFiles = extractSoFiles(apkFile, soDir)
            if (soFiles.isEmpty()) {
                Logger.w(TAG, "No SO files found in APK for arm64-v8a")
                return
            }
            
            Logger.i(TAG, "Found ${soFiles.size} SO files for arm64-v8a")
            
            // 根据 Android 版本选择不同的加载策略
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+
                installSoOnN(context, soFiles)
            } else {
                // Android 7.0 以下
                installSoBeforeN(context, soFiles)
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Logger.i(TAG, "Install native library success, elapsed: ${elapsed}ms")
            
        } catch (e: IOException) {
            Logger.e(TAG, "Install native library failed: IO error", e)
            throw e
        } catch (e: ReflectiveOperationException) {
            Logger.e(TAG, "Install native library failed: Reflection error", e)
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Install native library failed: Unexpected error", e)
            throw e
        }
    }
    
    /**
     * 从 APK 中提取 SO 文件（仅 arm64-v8a）
     * 
     * @param apkFile APK 文件
     * @param destDir 目标目录
     * @return 提取的 SO 文件列表
     */
    @Throws(IOException::class)
    private fun extractSoFiles(apkFile: File, destDir: File): List<File> {
        val soFiles = mutableListOf<File>()
        
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                
                // 只处理 arm64-v8a 的 SO 文件
                if (name.startsWith("lib/${Constants.SUPPORTED_ABI}/") && name.endsWith(".so") && !entry.isDirectory) {
                    val soName = File(name).name
                    val soFile = File(destDir, soName)
                    
                    // 检查是否需要更新（比较 SHA1）
                    var needExtract = true
                    if (soFile.exists()) {
                        val newSha1 = calculateZipEntrySHA1(zip, entry)
                        val oldSha1 = FileUtils.getFileSHA1(soFile)
                        
                        if (newSha1 != null && oldSha1 != null && newSha1 == oldSha1) {
                            Logger.d(TAG, "SO file unchanged, skip: $soName")
                            needExtract = false
                            soFiles.add(soFile)
                        }
                    }
                    
                    if (needExtract) {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(soFile).use { output ->
                                val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                                var count: Int
                                while (input.read(buffer).also { count = it } != -1) {
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        Logger.d(TAG, "Extracted SO: $soName")
                        soFiles.add(soFile)
                    }
                }
            }
        }
        
        return soFiles
    }
    
    /**
     * 计算 ZIP 条目的 SHA1
     */
    private fun calculateZipEntrySHA1(zip: ZipFile, entry: java.util.zip.ZipEntry): String? {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    digest.update(buffer, 0, len)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(TAG, "Calculate ZIP entry SHA1 failed", e)
            null
        }
    }
    
    /**
     * Android 7.0+ 的 SO 加载
     */
    @Throws(ReflectiveOperationException::class)
    private fun installSoOnN(context: Context, soFiles: List<File>) {
        Logger.i(TAG, "Install SO for Android N+")
        
        val classLoader = context.classLoader
        
        // 获取 pathList 字段
        val pathListField = ReflectUtil.getClassLoaderField(classLoader, "pathList")
        val pathList = pathListField.get(classLoader)
        
        // 获取 nativeLibraryDirectories 列表
        val nativeLibraryDirectoriesField = pathList.javaClass.getDeclaredField("nativeLibraryDirectories")
        nativeLibraryDirectoriesField.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val nativeLibraryDirectories = nativeLibraryDirectoriesField.get(pathList) as MutableList<File>
        
        // 添加新的 SO 目录
        val soDir = soFiles.firstOrNull()?.parentFile
        if (soDir != null && !nativeLibraryDirectories.contains(soDir)) {
            nativeLibraryDirectories.add(0, soDir)
            Logger.d(TAG, "Added SO directory: ${soDir.absolutePath}")
        }
        
        // 更新 nativeLibraryPathElements（Android 7.0+）
        try {
            val nativeLibraryPathElementsField = pathList.javaClass.getDeclaredField("nativeLibraryPathElements")
            nativeLibraryPathElementsField.isAccessible = true
            
            // 使用反射调用 makePathElements 来创建新的 path elements
            val makePathElementsMethod = pathList.javaClass.getDeclaredMethod(
                "makePathElements",
                List::class.java
            )
            makePathElementsMethod.isAccessible = true
            val newElements = makePathElementsMethod.invoke(pathList, nativeLibraryDirectories)
            
            nativeLibraryPathElementsField.set(pathList, newElements)
            Logger.d(TAG, "Updated nativeLibraryPathElements")
            
        } catch (e: NoSuchFieldException) {
            Logger.w(TAG, "nativeLibraryPathElements field not found, might not be critical", e)
        } catch (e: NoSuchMethodException) {
            Logger.w(TAG, "makePathElements method not found, might not be critical", e)
        }
        
        Logger.i(TAG, "SO installation on N+ completed")
    }
    
    /**
     * Android 7.0 以下的 SO 加载
     */
    @Throws(ReflectiveOperationException::class)
    private fun installSoBeforeN(context: Context, soFiles: List<File>) {
        Logger.i(TAG, "Install SO for Android before N")
        
        val classLoader = context.classLoader
        
        // 获取 pathList 字段
        val pathListField = ReflectUtil.getClassLoaderField(classLoader, "pathList")
        val pathList = pathListField.get(classLoader)
        
        // 获取 nativeLibraryDirectories 列表
        val nativeLibraryDirectoriesField = pathList.javaClass.getDeclaredField("nativeLibraryDirectories")
        nativeLibraryDirectoriesField.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val nativeLibraryDirectories = nativeLibraryDirectoriesField.get(pathList) as MutableList<File>
        
        // 添加新的 SO 目录
        val soDir = soFiles.firstOrNull()?.parentFile
        if (soDir != null && !nativeLibraryDirectories.contains(soDir)) {
            nativeLibraryDirectories.add(0, soDir)
            Logger.d(TAG, "Added SO directory: ${soDir.absolutePath}")
        }
        
        Logger.i(TAG, "SO installation before N completed")
    }
}
